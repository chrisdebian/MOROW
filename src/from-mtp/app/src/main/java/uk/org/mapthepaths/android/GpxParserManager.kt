package uk.org.mapthepaths.android


import android.util.Xml
import freemap.data.POI
import freemap.data.TrackPoint
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

object GpxParserManager {

    private val parser = Xml.newPullParser()
    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    init {
        sdf.timeZone = TimeZone.getTimeZone("GMT")
    }


    fun parse(filename: String): Survey {
        val inp = FileInputStream(filename)
        val survey = Survey()

        parser.setInput(inp, null)
        parser.nextTag()

        while (parser.next() != XmlPullParser.END_TAG) {

            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {

                    "name", "desc" -> {
                        val tag = parser.name
                        while (parser.next() != XmlPullParser.END_TAG) {
                            if (parser.eventType == XmlPullParser.TEXT) {
                                if (tag == "name") survey.name = parser.text else survey.description = parser.text

                            }
                        }
                    }

                    "trk" -> {
                        readTrk(survey)
                    }

                    "wpt" -> {
                        val wp = readWpt()
                        survey.add(wp)
                    }

                    else -> {
                        while (parser.next() != XmlPullParser.END_TAG) {
                        }
                    }
                }
            }
        }

        return survey
    }

    private fun readTrk(survey: Survey) {


        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {

                if (parser.name == "trkseg") {

                    val path = readTrkseg()

                    survey.addPath(path)
                } else {
                    while (parser.next() != XmlPullParser.END_TAG) {
                    }
                }
            }
        }


    }

    private fun readTrkseg(): SurveyedPath {

        val path = SurveyedPath()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {

                when (parser.name) {
                    "extensions" -> {
                        val props = readProperties()

                        props.forEach {
                            path.properties[it.key] = it.value
                        }

                    }

                    "trkpt" -> {
                        val tp = readTrkpt()
                        if (tp != null) {
                            path.add(tp)
                        }
                    }
                }
            }
        }

        return path
    }

    private fun readProperties(): HashMap<String, String> {
        val props = hashMapOf<String, String>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "tag") {

                val k = parser.getAttributeValue(null, "k")
                val v = parser.getAttributeValue(null, "v")

                if (k != null && v != null) {
                    props[k] = v
                }
                parser.next()
            }
        }

        return props
    }

    private fun readTrkpt(): TrackPoint? {
        var tp: TrackPoint? = null
        val lat = parser.getAttributeValue(null, "lat")
        val lon = parser.getAttributeValue(null, "lon")

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {

                if (parser.name == "time") {
                    if (parser.next() == XmlPullParser.TEXT) {
                        val time = parser.text

                        tp = TrackPoint(lon.toDouble(), lat.toDouble(), sdf.parse(time).time)

                        parser.next() // end <time>
                    }
                } else {
                    while (parser.next() != XmlPullParser.END_TAG) {

                    }
                }
            }
        }
        return tp
    }

    private fun readWpt(): Waypoint {

        val lat = parser.getAttributeValue(null, "lat")
        val lon = parser.getAttributeValue(null, "lon")
        var name = ""
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "name") {
                if (parser.next() == XmlPullParser.TEXT) {
                    name = parser.text
                }
                parser.next() // end <name>
            }
        }
        return Waypoint(name, lat.toDouble(), lon.toDouble())
    }
}
