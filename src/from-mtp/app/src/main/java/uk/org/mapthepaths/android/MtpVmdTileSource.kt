package uk.org.mapthepaths.android


import uk.co.ordnancesurvey.android.maps.MapTile
import uk.co.ordnancesurvey.android.maps.WebTileSource


class MtpVmdTileSource(tr: String) : WebTileSource(null) {

    private val tileRoot = tr


    override fun uriStringForTile(tile: MapTile): String {

        return "$tileRoot/${tile.x}/${tile.y}.png"
    }

}