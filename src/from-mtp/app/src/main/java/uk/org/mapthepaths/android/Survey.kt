package uk.org.mapthepaths.android


import android.util.Xml
import freemap.data.TrackPoint
import java.io.Writer

data class Waypoint(val label: String, val lat: Double, val lon: Double)

class Survey(var name: String = "", var description: String = "") {

    val paths = mutableListOf<SurveyedPath>()
    private var bbox = BBox()
    private var adding = false // prevent ConcurrentModificationException?
    private val waypoints = mutableListOf<Waypoint>()

    fun newSeg(properties: HashMap<String, String> = HashMap()) {
        if (paths.size > 0 && paths.last().points.size < 2) {
            paths.remove(paths.last())
        }
        val seg = SurveyedPath(properties)
        paths.add(seg)
    }

    fun add(tp: TrackPoint) {
        adding = true
        val copyPoints = MutableList(paths.last().points.size) { TrackPoint(paths.last().points[it]) }
        copyPoints.add(tp)
        paths.last().clear()
        copyPoints.forEach {
            paths.last().add(it)
        }
        bbox.extend(paths.last().bbox)
        adding = false
    }

    fun addPath(trkseg: SurveyedPath) {
        paths.add(trkseg)
        bbox.extend(trkseg.bbox)
    }

    fun clear() {
        paths.clear()
        waypoints.clear()
    }

    val empty: Boolean
        get() {
            var isEmpty = false
            if (waypoints.isEmpty()) {
                isEmpty = paths.size == 0
                paths.forEach {
                    isEmpty = isEmpty.or(it.points.size == 0)
                }
            }
            return isEmpty
        }

    val timestamp: Long
        get() = if(empty) 0 else paths[0].points[0].timestamp

    fun toGPX(writer: Writer, osmiseTags: Boolean = false) {
        if (!adding) {
            val xmlSerializer = Xml.newSerializer()
            xmlSerializer.setOutput(writer)
            xmlSerializer.startDocument("UTF-8", true)
            xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
            xmlSerializer.startTag("", "gpx")
            xmlSerializer.startTag("", "name")
            xmlSerializer.text(name)
            xmlSerializer.endTag("", "name")
            xmlSerializer.startTag("", "desc")
            xmlSerializer.text(description)
            xmlSerializer.endTag("", "desc")
            xmlSerializer.startTag("", "trk")
            paths.forEach { it.toGPX(xmlSerializer, osmiseTags) }
            xmlSerializer.endTag("", "trk")

            waypoints.forEach {
                xmlSerializer.startTag("", "wpt")
                xmlSerializer.attribute("", "lat", "${it.lat}")
                xmlSerializer.attribute("", "lon", "${it.lon}")

                xmlSerializer.startTag("", "name")
                xmlSerializer.text(it.label)
                xmlSerializer.endTag("", "name")

                xmlSerializer.endTag("", "wpt")
            }
            xmlSerializer.endTag("", "gpx")
            xmlSerializer.endDocument()
        }
    }

    fun simplify(): Survey {
        val simplified = Survey(name, description)
        paths.forEach { simplified.addPath(it.simplify()) }
        return simplified
    }

    fun add(wp: Waypoint) {
        waypoints.add(wp)
    }
}