package uk.org.mapthepaths.android

import freemap.data.Algorithms
import freemap.data.TrackPoint
import org.xmlpull.v1.XmlSerializer


class SurveyedPath(var properties: HashMap<String, String> =hashMapOf()) {

    var points = mutableListOf<TrackPoint>()
    var bbox = BBox()



    fun add(tp: TrackPoint) {
        points.add(tp)
        bbox.extend(tp.x, tp.y)
    }


    fun simplify() : SurveyedPath {
        val simplified = SurveyedPath(properties)
        simplified.bbox = bbox

        if(points.size >= 2) {
            simplified.points = Algorithms.douglasPeuckerNonRecursive(points.toTypedArray(), 2.5).toMutableList()
        }
        return simplified
    }

    fun clear() {
        points.clear()
        bbox = BBox()
    }

    fun toGPX(xmlSerializer: XmlSerializer, osmiseTags: Boolean = false) {

        xmlSerializer.startTag("", "trkseg")
        xmlSerializer.startTag("", "extensions")
        val props = if(osmiseTags) this.osmiseTags() else properties
        props.forEach {
            xmlSerializer.startTag("", "tag")
            xmlSerializer.attribute("", "k", it.key)
            xmlSerializer.attribute("", "v", it.value)
            xmlSerializer.endTag("", "tag")
        }
        xmlSerializer.endTag("", "extensions")

        points.forEach {
            xmlSerializer.startTag("", "trkpt")
            xmlSerializer.attribute("", "lat", "${it.y}")
            xmlSerializer.attribute("", "lon", "${it.x}")
            xmlSerializer.startTag("","time")
            xmlSerializer.text(it.gpxTimestamp)
            xmlSerializer.endTag("", "time")
            xmlSerializer.endTag("", "trkpt")
        }

        xmlSerializer.endTag("", "trkseg")
    }



    private fun osmiseTags(): HashMap<String, String> {
        val currentPathType: String = properties["type"] ?: "unknown"
        val currentSurface: String = properties["surface"] ?:"unknown"
        val tags  = PathTypes.types[currentPathType]?.osmTags ?: HashMap()
        val srfTags = PathTypes.surfaceTags[currentSurface] ?: HashMap()
        tags += srfTags
        return tags
    }
}