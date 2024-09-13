package uk.org.mapthepaths.android

import android.graphics.Color

data class TypeInfo(val rowColour: Int, val osmColour: Int, val displayName: String, val shortName: String, val waymark: Int, val osmTags: HashMap<String, String>)


object PathTypes {


    var types = linkedMapOf(

            "Fo" to TypeInfo
             (Color.argb(128, 0, 255, 0),
            Color.rgb(0, 128, 0),
            "public footpath",
                     "Footpath",
                     R.drawable.footpath,
                     hashMapOf("highway" to "footway", "designation" to "public_footpath")),


            "Br" to TypeInfo
            (Color.argb(128, 255, 128, 0),
                    Color.rgb(170, 85, 0),
                     "public bridleway",
                    "Bridleway",
                     R.drawable.bridleway,
                    hashMapOf("highway" to "bridleway", "designation" to "public_bridleway")),


            "Re" to TypeInfo
            (Color.argb(128, 255, 0, 255),
                    Color.rgb(192, 0, 192),
                    "restricted byway",
                    "Restricted byway",
                    R.drawable.restricted_byway,
                    hashMapOf("highway" to "footway", "designation" to "restricted_byway")),

            "BO" to TypeInfo
            (Color.argb(128, 255, 0, 0),
                    Color.rgb(192, 0, 0),
                    "byway open to all traffic",
                    "BOAT",
                    R.drawable.byway,
                    hashMapOf("highway" to "footway", "designation" to "byway_open_to_all_traffic")),

            "Pe" to TypeInfo
            (Color.argb(128, 0, 255, 255),
                    Color.rgb(0, 128, 128),
                    "permissive footpath",
                    "Permissive",
                    R.drawable.permissive,
                   hashMapOf("highway" to "footway", "foot" to "permissive", "designation" to ""))
    )


    var surfaceTags = linkedMapOf("grass path" to hashMapOf("surface" to "grass"),
            "dirt path" to hashMapOf("surface" to "dirt"),
            "gravel path" to hashMapOf("surface" to "gravel"),
            "concrete path" to hashMapOf("surface" to "concrete"),
            "grass track" to hashMapOf("highway" to "track", "surface" to "grass"),
            "dirt track" to hashMapOf("highway" to "track", "surface" to "dirt"),
            "gravel track" to hashMapOf("highway" to "track", "surface" to "gravel"),
            "unpaved service road" to hashMapOf("highway" to "service", "surface" to "unpaved"),
            "paved service road" to hashMapOf("highway" to "service", "surface" to "paved"),
            "steps" to hashMapOf("highway" to "steps"))


    private val typeKeys = types.keys.toTypedArray()


    fun getTypeInfo(index: Int) : TypeInfo? {
       return types[typeKeys[index]]
    }

    fun getTypeInfoForDesignation (designation: String) : TypeInfo? {
        types.forEach {
            if (it.value.osmTags["designation"] == designation) {
                return it.value
            }
        }
        types.forEach {
            if(it.value.osmTags["foot"] == designation) {
                return it.value
            }
        }
        return null
    }

    fun getDisplayNames() :Array<String> {
        return Array(typeKeys.size) { getTypeInfo(it)?.displayName ?: ""}
    }
}