package uk.org.mapthepaths.android


import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color

import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceManager

import android.view.*

import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.westnordost.osmapi.map.data.OsmLatLon
import freemap.data.Point
import kotlinx.android.synthetic.main.activity_main.*
import oauth.signpost.OAuthConsumer
import oauth.signpost.basic.DefaultOAuthConsumer
import uk.co.ordnancesurvey.android.maps.*

class MainActivity : AppCompatActivity() {


    private val recordedWidth = 7.5f
    private val mtpOsmWidth = 7.5f
    private val councilWidth = 15.0f
    private lateinit var osmProcessor: OSMProcessor
    private var recordedRoute = PolylineOptions().color(Color.BLUE).width(recordedWidth)
    private var actualRecRoute: Polyline? = null
    private val defaultGP = GridPoint(0.0, 0.0)
    private var lastPosition: GridPoint = defaultGP
    private val mtpDownloader = MtpDownloader(mapOf("council" to "https://www.mapthepaths.org.uk/row.php?layer=council", "osm" to "https://www.mapthepaths.org.uk/fm/ws/bsvr.php?fullways=1&way=footpaths&inProj=4326&dbname=freemap&format=json"), lifecycleScope, this::displayMtpRoute, this::onError)
    private val downloadedOsm = HashMap<String, Polyline>()
    private val downloadedCouncil = HashMap<String, Polyline>()
    private val downloadedLiveOsm = HashMap<Long, Polyline>()
    private var selectedLiveOsm: Polyline? = null
    private var liveOsmMode = false
    private var consumer: OAuthConsumer? = null
    private var matches = mutableListOf<OSMProcessor.WayMatch>()
    private lateinit var mapFrag: MapFragment
    private var gpsService: GpsService? = null
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter()
    private lateinit var serviceConn: ServiceConnection
    private var myLoc: Marker? = null
    private var newPathOnItemSelected = false
    private var newSurfaceOnItemSelected = false
    private var viewDimensions = 6000
    private var liveOsmDimensions = 3000
    private var showCurrentLocation = true
    private lateinit var gpxUploader: GpxUploader

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        liveOsmMode = savedInstanceState?.getBoolean("liveOsmMode") ?: false
        setContentView(R.layout.activity_main)
        osmProcessor = OSMProcessor(Constants.API_PATH,
                Constants.USER_AGENT, lifecycleScope, ::renderOsmWays)
        gpxUploader = GpxUploader(Constants.API_PATH,
                Constants.USER_AGENT, lifecycleScope)
        val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
        val permissionValues = permissions.map { ContextCompat.checkSelfPermission(this, it) }
        if (!permissionValues.contains(PackageManager.PERMISSION_DENIED)) {
            doInitActivity()
        } else {
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }

    private fun doInitActivity() {
        try {


            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, PathTypes.types.keys.toTypedArray()) {

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val view: View = convertView
                            ?: inflater.inflate(R.layout.spinner, parent, false)
                    val typeInfo = PathTypes.getTypeInfo(position)
                    if (typeInfo != null) {
                        (view.findViewById(R.id.rowIcon) as ImageView).setImageResource(typeInfo.waymark)
                        (view.findViewById(R.id.rowText) as TextView).text = typeInfo.displayName
                    }
                    return view
                }


                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    return getView(position, convertView, parent)
                }
            }

            pathType.adapter = adapter

            pathType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (newPathOnItemSelected) {
                        Toast.makeText(this@MainActivity, "Current type ${parent.getItemAtPosition(pos)}", Toast.LENGTH_SHORT).show()
                        if (gpsService?.logging == true && newPathOnItemSelected) onNewPath()
                    } else {
                        newPathOnItemSelected = true
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {

                }
            }

            val surfaceAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, PathTypes.surfaceTags.keys.toTypedArray())
            surfaceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            pathSurface.adapter = surfaceAdapter

            pathSurface.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                    if (newSurfaceOnItemSelected) {
                        if (gpsService?.logging == true && newPathOnItemSelected) onNewPath()
                    } else {
                        newSurfaceOnItemSelected = true
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>) {

                }
            }

            record.setOnClickListener {


                if (gpsService?.logging == false) {
                    record.setImageDrawable(getDrawable(R.drawable.blacksquare))
                    val broadcast = Intent("uk.org.mapthepaths.android.startLogging")
                    sendBroadcast(broadcast)
                    onNewPath()
                } else {
                    record.setImageDrawable(getDrawable(R.drawable.reddot))
                    val broadcast = Intent("uk.org.mapthepaths.android.stopLogging")
                    sendBroadcast(broadcast)
                }

            }


            note.setOnClickListener {

                val view: View = layoutInflater.inflate(R.layout.textdialog, null)
                AlertDialog.Builder(this).setTitle("Enter a note for other mappers: ").setNegativeButton("Cancel", null).setView(view).setPositiveButton("OK")
                { _, _ ->

                    showProgressBar(true)
                    val et: EditText = view.findViewById(R.id.etTextInput)
                    val latLon = DoubleArray(2)
                    mapFrag.map.mapProjection.fromGridPoint(lastPosition, latLon)
                    osmProcessor.uploadNote(et.text.toString(), OsmLatLon(latLon[0], latLon[1]), consumer, this::onError) {
                        showProgressBar(false)
                        Util.showAlertDialog(this, "Note created with ID $it")
                    }
                }.show()

            }

            mapFrag = fragmentManager.findFragmentById(R.id.mapFragment) as MapFragment



            bottomNavigation.setOnNavigationItemSelectedListener {
                val newLiveOsmMode = it.itemId == R.id.menuItemEdit
                if (newLiveOsmMode && !liveOsmMode) {
                    setupLiveOsmMode()
                } else if (!newLiveOsmMode && liveOsmMode) {
                    setupViewMode()
                }
                liveOsmMode = newLiveOsmMode
                true
            }

            mapFrag.map.setMapLayers(arrayOf(MapLayer(200, 1000.0f)))
            mapFrag.map.setTileSources(listOf(MtpVmdTileSource("https://www.mapthepaths.org.uk/ostiles/0/")))
            mapFrag.map.moveCamera(CameraPosition(GridPoint(489600.0, 128500.0), 2.5f), false)

            mapFrag.map.setOnMapLongClickListener {
                selectedLiveOsm = null
                Toast.makeText(this, "$it", Toast.LENGTH_SHORT).show()
                val ll = DoubleArray(2)
                mapFrag.map.mapProjection.fromGridPoint(it, ll)
                matches.forEach { match ->
                    var tag = osmProcessor.getTag(match.wayId, "designation")
                    if (tag == "None") tag = osmProcessor.getTag(match.wayId, "foot")
                    val typeInfo = PathTypes.getTypeInfoForDesignation(tag)
                    downloadedLiveOsm[match.wayId]?.color = typeInfo?.osmColour ?: Color.GRAY
                }
                matches = osmProcessor.findNearestWay(Point(ll[1], ll[0]), 100.0)
                if (matches.size > 0) {
                    downloadedLiveOsm[matches[0].wayId]?.color = Color.YELLOW
                    if (consumer != null) {
                        selectedLiveOsm = downloadedLiveOsm[matches[0].wayId]
                    }
                }
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    //         Log.d("mapthepaths", "Receiving broadcast... ${intent.action}")
                    when (intent.action) {
                        "uk.org.mapthepaths.android.onLocationChanged" -> {
                            val lat = intent.getDoubleExtra("lat", 51.05)
                            val lon = intent.getDoubleExtra("lon", -0.72)
                            val gp = mapFrag.map.mapProjection.toGridPoint(lat, lon)
                            handleLocationChange(gp)
                        }

                        "uk.org.mapthepaths.android.gpsStatusChanged" -> {
                            val status = intent.getBooleanExtra("status", true)
                            myLoc?.isVisible = status
                        }

                        "uk.org.mapthepaths.android.renderSurvey" -> {
                            //        Log.d("mapthepaths", "renderSurvey broadcast received")
                            renderSurvey()
                        }

                        "uk.org.mapthepaths.android.surveyFileIoError" -> {
                            Util.showAlertDialog(this@MainActivity, intent.getStringExtra("error"))
                        }
                    }
                }
            }

            intentFilter.addAction("uk.org.mapthepaths.android.onLocationChanged")
            intentFilter.addAction("uk.org.mapthepaths.android.gpsStatusChanged")
            intentFilter.addAction("uk.org.mapthepaths.android.renderSurvey")
            intentFilter.addAction("uk.org.mapthepaths.android.surveyFileIoError")
            intentFilter.addAction("uk.org.mapthepaths.android.noGpsPermission")
            intentFilter.addAction("uk.org.mapthepaths.android.noFilePermission")

            registerReceiver(receiver, intentFilter)

            ServiceInitiator.startService(this) // this was in onResume() originally - not sure why
            serviceConn = ServiceInitiator.bindService(this) {
                gpsService = it
                setLoggingPrefs(PreferenceManager.getDefaultSharedPreferences(applicationContext))
                record.setImageDrawable(getDrawable(if (it.logging) R.drawable.blacksquare else R.drawable.reddot))
            }


            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val accessToken = prefs.getString("accessToken", null)
            val accessSecret = prefs.getString("accessSecret", null)
            if (accessToken != null && accessSecret != null) {
                consumer = DefaultOAuthConsumer(Constants.KEY, Constants.SECRET)
                consumer?.setTokenWithSecret(accessToken, accessSecret)
            }

            if (liveOsmMode) {
                setupLiveOsmMode()
            }

        } catch (e: Exception) {
            Util.logException(e, "onCreate()")
            throw e
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        showCurrentLocation = prefs.getBoolean("prefShowCurrentLocation", true)
        viewDimensions = prefs.getString("prefViewDimensions", "6000")?.toInt() ?: 6000
        liveOsmDimensions = prefs.getString("prefLiveOsmDimensions", "3000")?.toInt() ?: 3000
        setLoggingPrefs(prefs)
    }

    override fun onDestroy() {
        osmProcessor.onDestroy()
        mtpDownloader.onDestroy()
        val stopBroadcast = Intent("uk.org.mapthepaths.android.stopIfNotLogging")
        sendBroadcast(stopBroadcast)
        unregisterReceiver(receiver)
        unbindService(serviceConn)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.loginToOsm)?.title = (if (consumer == null) "Login to OSM" else "Log out of OSM")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.download -> {
                initiateDownload()
            }

            R.id.downloadLiveOsm -> {


                //mapFrag1.getLatLonBounds(llBounds)
                // for live OSM data, download data 2km either side of our current point
                val llSW = DoubleArray(2)
                val llNE = DoubleArray(2)
                mapFrag.map.mapProjection.fromGridPoint(GridPoint(mapFrag.map.center.x - liveOsmDimensions / 2, mapFrag.map.center.y - liveOsmDimensions / 2), llSW)
                mapFrag.map.mapProjection.fromGridPoint(GridPoint(mapFrag.map.center.x + liveOsmDimensions / 2, mapFrag.map.center.y + liveOsmDimensions / 2), llNE)

                showProgressBar(true)
                osmProcessor.download(llSW + llNE, {
                    showProgressBar(false)
                    removeAndAddRecRoute()
                }, this::onError)

            }

            R.id.loginToOsm -> {
                if (consumer == null) {
                    Intent(this, OSMAuthActivity::class.java).apply {
                        startActivityForResult(this, 0)
                    }
                } else {
                    consumer = null
                    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    val editor = prefs.edit()
                    editor.remove("accessToken")
                    editor.remove("accessSecret")
                    editor.apply()
                    item.title = "Login to OSM"
                }
            }

            R.id.upload -> {

                val survey = gpsService?.survey
                if (gpsService?.logging == true) {
                    Util.showAlertDialog(this, "Please stop recording before uploading.")
                } else if (survey == null || survey.empty) {
                    Util.showAlertDialog(this, "No current survey. Please record a survey before uploading.")
                } else {
                    uploadSurvey(survey)
                }
            }

            R.id.prefs -> {
                startActivity(Intent(this, Preferences::class.java))
            }

            R.id.newTrack -> {
                newTrack()
            }

            R.id.uploadSavedTrack -> {
                Intent(this, GpxFilesActivity::class.java).apply {
                    startActivityForResult(this, 1)
                }
            }

            R.id.changeDesignation -> {
                when {
                    selectedLiveOsm != null -> {
                        showDesignationDialog { tags ->

                            showProgressBar(true)
                            osmProcessor.setTags(matches[0].wayId, tags, consumer!!, {
                                showProgressBar(false)
                                val typeInfo = PathTypes.getTypeInfoForDesignation(tags["designation"]
                                        ?: (tags["foot"] ?: ""))
                                selectedLiveOsm?.color = typeInfo?.osmColour
                                        ?: Color.GRAY
                                selectedLiveOsm = null
                            }, this::onError)

                        }
                    }
                    consumer == null -> {
                        AlertDialog.Builder(this).setMessage("Please login to OSM to change designations.").setPositiveButton("OK", null).show()
                    }
                    else -> {
                        AlertDialog.Builder(this).setMessage("Please select a way by long-pressing it.").setPositiveButton("OK", null).show()
                    }
                }
            }

            R.id.userGuide -> {
                startActivity(Intent(this, UserGuide::class.java))
            }

            R.id.about -> {
                AlertDialog.Builder(this).setPositiveButton("OK", null).setMessage("MapThePaths app 0.2.3, (c) Nick Whitelegg 2018-20. Uses OpenStreetMap data, copyright OpenStreetMap contributors, licensed under the Open Database License. Map tiles (c) Ordnance Survey, OS OpenData License. Council data available under varying licenses: please see https://osm.mathmos.net/prow/open-data for details.").show()
            }
        }
        return true
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                0 -> {
                    consumer = intent?.getSerializableExtra("oauthConsumer") as OAuthConsumer
                    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                    val editor = prefs.edit()
                    editor.putString("accessToken", consumer?.token)
                    editor.putString("accessSecret", consumer?.tokenSecret)
                    editor.apply()
                    invalidateOptionsMenu()
                }

                1 -> {
                    intent?.apply {
                        val gpx = "${Environment.getExternalStorageDirectory().absolutePath}/mapthepaths/${getStringExtra("uk.org.mapthepaths.android.gpxFile")}"
                        val previousSurvey = GpxParserManager.parse(gpx)
                        uploadSurvey(previousSurvey, false)
                    }
                }
            }
        }
    }

    fun onNewPath() {
        val currentPathType = pathType.selectedItem as String
        val currentSurface = pathSurface.selectedItem as String

        val tags = hashMapOf("type" to currentPathType, "surface" to currentSurface)
        val logging = gpsService?.logging == true
        gpsService?.logging = false // stop while adding last point to new segment
        gpsService?.survey?.newSeg(tags)

        val latLon = DoubleArray(2)
        mapFrag.map.mapProjection.fromGridPoint(lastPosition, latLon)
        gpsService?.survey?.add(Waypoint("Begin ${PathTypes.types[currentPathType]?.shortName
                ?: "Unknown"}/${currentSurface}", latLon[0], latLon[1]))
        gpsService?.logging = logging
    }

    private fun initiateDownload() {
        showProgressBar(true)
        val sw = DoubleArray(2)
        val ne = DoubleArray(2)

        mapFrag.map.mapProjection.fromGridPoint(GridPoint(mapFrag.map.center.x - viewDimensions / 2, mapFrag.map.center.y - viewDimensions / 2), sw)
        mapFrag.map.mapProjection.fromGridPoint(GridPoint(mapFrag.map.center.x + viewDimensions / 2, mapFrag.map.center.y + viewDimensions / 2), ne)


        val bbox = doubleArrayOf(sw[1], sw[0], ne[1], ne[0])
        mtpDownloader.download("council", bbox) {
            mtpDownloader.download("osm", bbox) {
                showProgressBar(false)
            }
        }
    }

    private fun displayMtpRoute(id: String, latLons: DoubleArray, layer: String, designation: String) {
        val lyr = if (layer == "council") downloadedCouncil else downloadedOsm


        if (lyr[id] == null) {
            val processedDataAsGridPoint = mapFrag.map.latLonsToGridPoints(latLons)

            val colour = when (layer) {
                "osm" -> PathTypes.getTypeInfoForDesignation(designation)?.osmColour ?: Color.LTGRAY
                else -> PathTypes.types[designation]?.rowColour ?: Color.argb(128, 232, 232, 232)
            }

            val width = if (layer == "osm") mtpOsmWidth else councilWidth

            val polylineOptions = PolylineOptions().addAll(processedDataAsGridPoint).color(colour).width(width)
            lyr[id] = mapFrag.map.makePolyline(polylineOptions)
            if (!liveOsmMode) {
                mapFrag.map.addPolyline(lyr[id])
            }
            removeAndAddRecRoute()
        }

    }

    fun handleLocationChange(gp: GridPoint) {
        try {
            lastPosition = gp
            if (showCurrentLocation) {
                mapFrag.map.moveCamera(CameraPosition(gp, mapFrag.map.scale), false)
            }

            if (myLoc == null) {
                myLoc = mapFrag.map.addMarker(MarkerOptions().gridPoint(gp).snippet("Current location")
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.person)))
            } else {
                myLoc?.gridPoint = gp
            }

            if (gpsService?.logging == true) {
                recordedRoute.add(gp)
                when (recordedRoute.points.size) {
                    2 -> {
                        actualRecRoute?.remove()
                        actualRecRoute = mapFrag.map.addPolyline(recordedRoute)
                        actualRecRoute?.apply {
                            mapFrag.map.addPolyline(this)
                        }
                    }
                    !in 0..1 -> actualRecRoute?.points = recordedRoute.points
                }
            }
        } catch (e: Exception) {
            Util.logException(e, "handleLocationChange()")
            throw e
        }
    }


    private fun renderOsmWays(osmId: Long, tags: MutableMap<String, String>, processedData: DoubleArray) {
        val processedDataAsGridPoint = mapFrag.map.latLonsToGridPoints(processedData)
        val des = tags["designation"] ?: (tags["foot"] ?: "none")
        val colour = PathTypes.getTypeInfoForDesignation(des)?.osmColour ?: Color.GRAY
        if (tags["highway"] != null) {
            val polylineOptions = PolylineOptions().addAll(processedDataAsGridPoint).color(colour)
            if (downloadedLiveOsm[osmId] == null) { // 050419 don't add if it's already there
                downloadedLiveOsm[osmId] = mapFrag.map.makePolyline(polylineOptions)
                if (liveOsmMode) {
                    mapFrag.map.addPolyline(downloadedLiveOsm[osmId])
                }
            }
        }
    }

    private fun renderSurvey() {
        val renderedPath = PolylineOptions()
        renderedPath.width(recordedWidth).color(Color.BLUE)
        gpsService?.survey?.paths?.forEach {

            it.points.forEach { tp ->
                val gp = mapFrag.map.mapProjection.toGridPoint(tp.y, tp.x)
                renderedPath.add(gp)
                if (it == gpsService?.survey?.paths?.last()) {
                    // if we've restarted and are logging, make sure recorded route is rendered in correct colour

                    newPathOnItemSelected = false

                    // ensure that the dropdowns are matching the current recorded route


                    for (i in 0 until pathType.adapter.count) {
                        if (pathType.adapter.getItem(i) == it.properties["type"]) {
                            pathType.setSelection(i)
                            break
                        }
                    }

                    for (i in 0 until pathSurface.adapter.count) {
                        if (pathSurface.adapter.getItem(i) == it.properties["surface"]) {
                            pathSurface.setSelection(i)
                            break
                        }
                    }
                    newSurfaceOnItemSelected = false
                }
            }
        }


        recordedRoute = renderedPath
        if (recordedRoute.points.size >= 2) {
            actualRecRoute = mapFrag.map.makePolyline(recordedRoute)
            mapFrag.map.addPolyline(actualRecRoute)
        }
    }


    private fun newTrack() {
        if (gpsService?.survey?.empty == false) {
            AlertDialog.Builder(this).setTitle("Save existing track?")
                    .setPositiveButton("Yes") { _, _ ->
                        val view: View = layoutInflater.inflate(R.layout.textdialog, null)
                        AlertDialog.Builder(this).setTitle(
                                "Please enter filename to save (or leave blank to generate filename with timestamp): ").setView(view).setPositiveButton("OK")
                        { _, _ ->
                            val filename = (view.findViewById(R.id.etTextInput) as EditText).text.toString()
                            gpsService?.saveCurrentGpxWithTimestamp(filename)
                        }.show()
                    }
                    .setNegativeButton("No") { _, _ ->
                        gpsService?.newTrack()
                    }.show()
        }
        actualRecRoute?.remove()
        recordedRoute = PolylineOptions().width(recordedWidth).color(Color.BLUE)
        mapFrag.mMapView.invalidate()
    }

    private fun showDesignationDialog(callback: (HashMap<String, String>) -> Unit) {

        AlertDialog.Builder(this).setTitle("Select designation")
                .setItems(PathTypes.getDisplayNames()) { _, which ->
                    val tags = PathTypes.getTypeInfo(which)?.osmTags ?: HashMap()
                    tags.remove("highway")
                    callback(tags)
                }.setNegativeButton("Cancel", null).show()

    }


    private fun showProgressBar(progress: Boolean) {
        if (progress) {
            if (liveOsmMode) {
                selectionsContainer.visibility = View.GONE
            }
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
            if (liveOsmMode) {
                selectionsContainer.visibility = View.VISIBLE
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            AlertDialog.Builder(this).setMessage("Not all permissions were granted, app will be missing some functionality").setPositiveButton("OK") { _, _ ->
                doInitActivity()
            }.show()
        } else {
            doInitActivity()
        }
    }

    private fun setupLiveOsmMode() {
        downloadedCouncil.values.forEach { it.remove() }
        downloadedOsm.values.forEach { it.remove() }
        downloadedLiveOsm.values.forEach { mapFrag.map.addPolyline(it) }
        removeAndAddRecRoute()
    }


    private fun setupViewMode() {
        downloadedCouncil.values.forEach { mapFrag.map.addPolyline(it) }
        downloadedOsm.values.forEach { mapFrag.map.addPolyline(it) }
        downloadedLiveOsm.values.forEach { it.remove() }
        removeAndAddRecRoute()
    }

    private fun removeAndAddRecRoute() {
        actualRecRoute?.remove()
        actualRecRoute?.apply {
            mapFrag.map.addPolyline(this)
        }

    }

    private fun setLoggingPrefs(prefs: SharedPreferences) {
        gpsService?.setLoggingPrefs(
                (prefs.getString("prefLoggingInterval", "5")?.toInt() ?: 5) * 1000L,
                prefs.getString("prefLoggingDistance", "5")?.toFloat() ?: 5.0f
        )
    }

    private fun uploadSurvey(survey: Survey, currentTrack: Boolean = true) {

        if (consumer == null) {
            onError("You must login to OSM before uploading a survey.")
        } else {
            AlertDialog.Builder(this).setMessage("This will upload your GPX trace to OpenStreetMap in 'Public' mode under your OSM username. Others will be able to see it.\n\n").setNegativeButton("Cancel", null).setPositiveButton("OK") { _, _ ->


                val view: View = layoutInflater.inflate(R.layout.textdialog, null)
                AlertDialog.Builder(this).setTitle(
                        "Please enter a description of your survey: ").setView(view).setPositiveButton("OK")
                { _, _ ->
                    showProgressBar(true)
                    val etNote = (view.findViewById(R.id.etTextInput) as EditText).text.toString()
                    with(survey) {
                        name = "MapThePaths GPS survey"
                        description = etNote
                    }
                    gpxUploader.uploadGpx(survey, etNote, consumer, this::onError) {
                        showProgressBar(false)
                        AlertDialog.Builder(this).setMessage(it).setPositiveButton("OK") { _, _ ->
                            if (currentTrack) {
                                newTrack()
                            }
                        }.show()
                    } // we know consumer's not null
                }.show()
            }.show()
        }

    }

    // turning off permissions in settings forces an activity destroy/recreate. However it appears to,
    // rather unexpectedly, preserve the selected item in the bottom navigation view, so put the liveOsmMode
    // in the Bundle to ensure that the selected item matches the liveOsmMode
    // Also oddly, the isChecked() / setChecked() methods do not give the apparently correct result or have the
    // desired effect when the bottom navigation view is reloaded, i.e. if the 'edit' item is selected on recreate,
    // isChecked() on the edit mode item gives false, and setChecked() on view mode does not select view mode again.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("liveOsmMode", liveOsmMode)
    }

    private fun onError(msg: String) {
        Util.showAlertDialog(this, msg)
        showProgressBar(false)
    }
}
