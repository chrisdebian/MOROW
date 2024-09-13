package uk.org.mapthepaths.android


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

import java.io.BufferedReader
import java.io.InputStreamReader

import java.net.URL

class MtpDownloader(private val serverUrl: Map<String, String>, private val scope: CoroutineScope, var displayCallback: ((String, DoubleArray, String, String) -> Unit)?,
                    private var onError: ((String) -> Unit)?) {

    private var runSuccessCallback = true

    fun download(layer: String, bbox: DoubleArray, onSuccess: () -> Unit) {

        scope.launch {
            try {
                var json = ""
                withContext(Dispatchers.IO) {
                    val url = URL("${serverUrl[layer]}&bbox=" + bbox.joinToString(","))

                    //     Log.d("mapthepaths", "Calling this URL :$url")

                    val http = url.openConnection()
                    val inp = http.getInputStream()
                    val reader = BufferedReader(InputStreamReader(inp))
                    var line: String? = ""


                    while (line != null) {

                        line = reader.readLine()
                        json += line ?: ""
                    }


                }

                val featureArray = if (layer == "council") JSONArray(json) else JSONObject(json).getJSONArray("features")

                for (i in 0 until featureArray.length()) {
                    var designation = "none"
                    val curFeature = featureArray.getJSONObject(i)
                    val id = if (layer == "council") curFeature.getJSONObject("_id").getString("\$oid") else curFeature.getJSONObject("properties").getString("osm_id")

                    when (layer) {
                        "osm" -> {
                            val props = curFeature.getJSONObject("properties")
                            designation = if (props.has("designation")) props.getString("designation") else if (props.has("foot")) props.getString("foot") else "none"
                        }

                        "council" -> {
                            designation = curFeature.getJSONObject("properties").getString("Description").substring(0, 2)
                        }
                    }

                    when (curFeature.getJSONObject("geometry").getString("type")) {
                        // 020419 ensure that it's a LineString, other geometries can potentially be returned from the OSM server.
                        // For now we ignore non-LineStrings; TODO investigate what other geometries can be returned
                        "LineString" -> {
                            val latLons = processLineString(curFeature.getJSONObject("geometry").getJSONArray("coordinates"))
                            displayCallback?.invoke(id, latLons, layer, designation)


                        }

                        // some footways appear to be polygons - maybe they're circular??
                        "Polygon" -> {
                            val latLons = processLineString(curFeature.getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0))
                            displayCallback?.invoke(id, latLons, layer, designation)

                        }
                    }

                }
                if (runSuccessCallback) {
                    onSuccess()
                }

            } catch (e: Exception) {

                onError?.invoke("$e")
                //    Log.d("mapthepaths", "$e")

            }
        }
    }

    private fun processLineString(coords: JSONArray): DoubleArray {

        var latLons = DoubleArray(0)
        for (j in 0 until coords.length()) {
            latLons += coords.getJSONArray(j).getDouble(1)
            latLons += coords.getJSONArray(j).getDouble(0)
        }
        return latLons
    }

    fun onDestroy() {
        displayCallback = null
        runSuccessCallback = false
        onError = null
    }
}