package uk.org.mapthepaths.android


import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.*
import android.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat


import freemap.data.TrackPoint
import kotlinx.coroutines.*

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import kotlin.coroutines.CoroutineContext


class GpsService : Service(), LocationListener, CoroutineScope {

    var logging = false
    private var listeningForUpdates = false
    private var loading = false
    private var loaded = false

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                "uk.org.mapthepaths.android.startLogging" -> {
                    startLogging()
                }
                "uk.org.mapthepaths.android.stopLogging" -> {
                    stopLogging()
                }
                "uk.org.mapthepaths.android.stopIfNotLogging" -> {
                    stopIfNotLogging()
                }
            }
        }
    }

    private var loggingInterval = 5000L
    private val noLoggingInterval = 10000L
    private var loggingDistance = 5.0f
    private val noLoggingDistance = 10.0f


    var survey: Survey? = null
    private val gpxDir = "${Environment.getExternalStorageDirectory().absolutePath}/mapthepaths"
    private val gpxFile = "$gpxDir/mapthepathsCurrentSurvey.gpx"
    private val intentFilter = IntentFilter()


    private var lMgr: LocationManager? = null

    init {

        intentFilter.addAction("uk.org.mapthepaths.android.startLogging")
        intentFilter.addAction("uk.org.mapthepaths.android.stopLogging")
        intentFilter.addAction("uk.org.mapthepaths.android.stopIfNotLogging")

    }


    inner class GpsBinder : Binder() {
        fun getService(): GpsService {
            return this@GpsService
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        logging = prefs.getBoolean("uk.org.mapthepaths.android.logging", false)
    }

    override fun onStartCommand(intent: Intent?, startFlags: Int, id: Int): Int {
        registerReceiver(receiver, intentFilter)

        val channelID = "gpsChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelID, "GPS notifications channel", NotificationManager.IMPORTANCE_DEFAULT)
            val nMgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nMgr.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelID).setContentTitle("MapThePaths: listening for location").setContentText("Listening in the background for your location").build()
        startForeground(1, notification)

        if (logging) {
            saveSurvey() // ensure points logged while activity was not running are saved
            broadcastRender() // broadcast that we want to re-render without having to load the survey
        } else {
            loading = true
            loadSurvey() // we only load the survey if we are not logging
        }

        lMgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!listeningForUpdates) {
            listeningForUpdates = true
            requestUpdatesWithPermissionCheck()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lastKnown = lMgr?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnown != null) {
                sendLocationBroadcast(lastKnown)
            }
        }


        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return GpsBinder()
    }

    override fun onDestroy() {
        saveLoggingAsPref()
        unregisterReceiver(receiver)
        lMgr?.removeUpdates(this)
    }

    fun startLogging() {
        logging = true
        lMgr?.removeUpdates(this)
        requestUpdatesWithPermissionCheck()
        saveLoggingAsPref()
    }

    fun stopLogging() {
        logging = false
        lMgr?.removeUpdates(this)
        requestUpdatesWithPermissionCheck()
        saveSurvey()
        saveLoggingAsPref()
    }


    fun stopIfNotLogging() {
        saveSurvey()
        if (!logging) {
            listeningForUpdates = false
            lMgr?.removeUpdates(this)
            stopSelf()
        }
    }

    override fun onLocationChanged(loc: Location) {
        val time = System.currentTimeMillis()
        sendLocationBroadcast(loc)
        if (logging) {
            val tp = TrackPoint(loc.longitude, loc.latitude, time)
            survey?.add(tp)
        }

    }

    override fun onProviderEnabled(provider: String) {
        sendGpsStatusBroadcast(true)
    }

    override fun onProviderDisabled(provider: String) {
        sendGpsStatusBroadcast(false)
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        sendGpsStatusBroadcast(status == LocationProvider.AVAILABLE)
    }


    fun saveCurrentGpxWithTimestamp(filename: String = "") {
        var fname = filename
        if(fname == "") {
            val sdf = SimpleDateFormat("yyyyMMdd-HHmmss")
            fname = "mtp${sdf.format(survey?.timestamp ?: 0)}.gpx"
        }
        saveSurvey("$gpxDir/$fname", true)
    }


    fun setLoggingPrefs(time: Long, distance: Float) {
        loggingInterval = time
        loggingDistance = distance
        if (logging) {
            lMgr?.removeUpdates(this)
            requestUpdatesWithPermissionCheck()
        }
    }

    private fun requestUpdatesWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lMgr?.requestLocationUpdates(LocationManager.GPS_PROVIDER, if (logging) loggingInterval else noLoggingInterval, if (logging) loggingDistance else noLoggingDistance, this)
        }
    }

    private fun sendLocationBroadcast(loc: Location) {
        // send msg to any receivers informing them of location
        val broadcast = Intent("uk.org.mapthepaths.android.onLocationChanged")
        broadcast.putExtra("lat", loc.latitude)
        broadcast.putExtra("lon", loc.longitude)
        this.sendBroadcast(broadcast)
    }

    private fun sendGpsStatusBroadcast(status: Boolean) {
        val broadcast = Intent("uk.org.mapthepaths.android.gpsStatusChanged")
        broadcast.putExtra("status", status)
        this.sendBroadcast(broadcast)
    }

    private fun saveSurvey(filename: String = gpxFile, newTrack: Boolean = false) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && !loading) {
            val dir = File(gpxDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }// do not try and save the survey if we're in the middle of a load
            launch {

                if (survey?.empty == false) {

                    try {
                        withContext(Dispatchers.IO) {

                            val pw = PrintWriter(FileWriter(filename))
                            survey?.toGPX(pw)
                            pw.close()

                            if (newTrack) {
                                newTrack()
                            }
                        }
                        //   File(tmpFile).copyTo(File(gpxFile), true)
                    } catch (e: Exception) {

                        val broadcast = Intent("uk.org.mapthepaths.android.surveyFileIoError")
                        broadcast.putExtra("error", "Survey save error: $e")
                        sendBroadcast(broadcast)

                    }
                }
            }
        }
    }

    fun newTrack() {
        survey?.clear()
        val f = File(gpxFile)
        if (f.exists()) {
            f.delete()
        }
    }

    private fun loadSurvey() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            //   Log.d("mapthepaths", "loadSurvey()")
            when {

                File(gpxFile).exists() -> { // && !loaded

                    launch {
                        try {
                            withContext(Dispatchers.IO) {
                                survey = GpxParserManager.parse(gpxFile)
                                loaded = true
                                loading = false
                            }
                            broadcastRender()
                        } catch (e: Exception) {
                            loading = false
                            Util.logException(e, "loadSurvey()")

                            val broadcast = Intent("uk.org.mapthepaths.android.surveyFileIoError")
                            broadcast.putExtra("error", "$e")
                            sendBroadcast(broadcast)

                        }
                    }
                }
            }
        }

        if (!loaded) {
            loading = false
            survey = Survey("survey", "my survey")
        }
    }

    private fun broadcastRender() {
        val broadcast = Intent("uk.org.mapthepaths.android.renderSurvey")
        sendBroadcast(broadcast)
    }

    private fun saveLoggingAsPref() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val editor = prefs.edit()
        editor.putBoolean("uk.org.mapthepaths.android.logging", logging)
        editor.apply()
    }
}