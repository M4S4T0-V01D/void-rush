package dev.m4s4t0.voidrush.game

/*
 * Everything that flies is kept in fixed pools made once at start-up: nothing is allocated while playing, so the
 * garbage collector never stutters a frame. Removal swaps the last live entry into the hole, so loops that remove run
 * backwards.
 */

/** Sprite looks the renderer knows (shared by particles, shots and bullets). */
object Shape {
    const val ORB = 0
    const val RING = 1
    const val SPARK = 2
    const val SOFT = 3
    const val DIAMOND = 4
    const val BEAM = 5
    const val STAR = 6
    const val WAVE = 7
    const val BULLET = 8
    const val HEX = 9
    const val HOLE = 10
    const val FILL = 11
}

class Particles(val cap: Int) {
    var n = 0
    /** How many of the pool to use (the quality setting). */
    var limit = cap
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val life = FloatArray(cap); val max = FloatArray(cap)
    val size = FloatArray(cap); val grow = FloatArray(cap)
    val drag = FloatArray(cap)
    val color = IntArray(cap); val shape = IntArray(cap)

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, pLife: Float, pSize: Float, pColor: Int, pShape: Int, pDrag: Float = 2f, pGrow: Float = 0f) {
        if (n >= limit) return
        val i = n++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = pLife; max[i] = pLife; size[i] = pSize; grow[i] = pGrow; drag[i] = pDrag
        color[i] = pColor; shape[i] = pShape
    }

    fun remove(i: Int) {
        val l = --n
        if (i == l) return
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]
        life[i] = life[l]; max[i] = max[l]; size[i] = size[l]; grow[i] = grow[l]; drag[i] = drag[l]
        color[i] = color[l]; shape[i] = shape[l]
    }
}

object PKind {
    /** A credit chip; the value is how many credits. */
    const val CHIP = 0
    /** A rig token: hands out a ride. */
    const val RIG = 1
    /** A spin token: one more spin of the prize wheel at the end. */
    const val SPIN = 2
}

class Pickups(val cap: Int) {
    var n = 0
    val x = FloatArray(cap); val y = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap)
    val age = FloatArray(cap)
    val kind = IntArray(cap); val value = IntArray(cap)
    /** Pulled in by the magnet (or the eel): they never let go. */
    val pulled = BooleanArray(cap)

    fun add(px: Float, py: Float, pvx: Float, pvy: Float, pKind: Int, pValue: Int): Int {
        if (n >= cap) return -1
        val i = n++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy; age[i] = 0f; kind[i] = pKind; value[i] = pValue; pulled[i] = false
        return i
    }

    fun remove(i: Int) {
        val l = --n
        if (i == l) return
        x[i] = x[l]; y[i] = y[l]; vx[i] = vx[l]; vy[i] = vy[l]; age[i] = age[l]; kind[i] = kind[l]; value[i] = value[l]; pulled[i] = pulled[l]
    }
}

/** A lightning bolt between two points, for a zapper's live wire. */
class Arc {
    var life = 0f
    var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
    var seed = 0
}
