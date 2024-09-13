package uk.org.mapthepaths.android

import de.westnordost.osmapi.OsmConnection
import de.westnordost.osmapi.map.data.OsmLatLon
import de.westnordost.osmapi.traces.GpsTraceDetails
import de.westnordost.osmapi.traces.GpsTracesDao
import de.westnordost.osmapi.traces.GpsTrackpoint
import kotlinx.coroutines.*
import oauth.signpost.OAuthConsumer
import oauth.signpost.basic.DefaultOAuthConsumer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

// Role: to convert a Survey into a GPS trace suitable for use by osmapi.
fun Survey.getOsmGpsTrace(): ArrayList<GpsTrackpoint> {

    val trackpoints = arrayListOf<GpsTrackpoint>()
    paths.forEach { path ->
        var first = true
        path.points.forEach {
            trackpoints.add(GpsTrackpoint(OsmLatLon(it.y, it.x)).apply {
                isFirstPointInTrackSegment = first
                time = Date(it.timestamp)
            })
            first = false
        }
    }
    return trackpoints

}

class GpxUploader(val apiUrl: String, val userAgent: String, private val scope: CoroutineScope) {
    fun uploadGpx(unsimplifiedSurvey: Survey, message: String, consumer: OAuthConsumer?, onError: (String) -> Unit, onSuccess: (String) -> Unit) {

        val gpsTrace = unsimplifiedSurvey.getOsmGpsTrace()
        try {
            scope.launch {

                try {
                    var id = 0L

                    if (consumer != null) {
                        val localConsumer = DefaultOAuthConsumer(consumer.consumerKey, consumer.consumerSecret)
                        localConsumer.setTokenWithSecret(consumer.token, consumer.tokenSecret)
                        val uploadConn = OsmConnection(apiUrl, userAgent, localConsumer)

                        withContext(Dispatchers.IO) {
                            id = GpsTracesDao(uploadConn).create("mtp${System.currentTimeMillis() / 1000}.gpx", GpsTraceDetails.Visibility.PUBLIC, message, gpsTrace)
                        }
                        if (scope.coroutineContext.isActive) {
                            onSuccess("Uploaded to OSM with trace ID $id.\n")
                        }
                    }

                } catch (e: Exception) {
                    if (scope.coroutineContext.isActive) {
                        onError("$e. (If using MapThePaths 0.2.3 or higher for the first time, this can be resolved by logging out of OSM and logging in again. The OSM connection info has changed.)")
                    }
                }
            }
        } catch (e: Exception) {
            Util.logException(e, "upload")
            //     Log.d("mapthepaths", "EXCEPTION $e")
        }

    }
}