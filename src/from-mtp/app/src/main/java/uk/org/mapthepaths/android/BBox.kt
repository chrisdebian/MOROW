package uk.org.mapthepaths.android

class BBox {

    var w: Double = 0.0
    var s: Double = 0.0
    var e: Double = 0.0
    var n: Double = 0.0
    private var initialised = false

    fun extend(x: Double, y:Double) {
        if (!initialised) {
            initialised = true
            w = x
            s = y
            e = x
            n = y
        } else {
            if (x < w) w = x
            if (y < s) s = y
            if (x > e) e = x
            if (y > n) n = y
        }
    }

    fun extend (other: BBox) {
        if (!initialised) {
            initialised = true
            w = other.w
            s = other.s
            e = other.e
            n = other.n
        } else {
            if (other.w < w) w = other.w
            if (other.s < s) s = other.s
            if (other.e > e) e = other.e
            if (other.n > n) n = other.n
        }
    }
    fun intersects(other: BBox) : Boolean{
        return initialised && other.initialised && ((w in other.w..other.e) || (other.w in w..e)) &&
                ((s in other.s..other.n) || (other.s in s..n))
    }

    fun contains(x: Double, y: Double) : Boolean {
        return x in w..e && y in s..n
    }

    override fun toString() : String {
        return "Bbox [$w,$s,$e,$n]"
    }
}