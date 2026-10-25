package dev.m4s4t0.voidrush.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Material channel values (see the lit shader). */
object Mat {
    const val PAINT = 0
    const val TRIM = 1
    const val GLOW = 2
    const val DARK = 3
    const val GLASS = 4
}

/**
 * Builds flat-shaded triangle soups (pos3, normal3, material) out of a few primitives: ridge-roofed hulls, faceted
 * slabs, tubes, rings, spikes and lumpy spheres. Everything is placed through [at] (offset, turn about z, scale) and
 * can be mirrored across y = 0.
 */
class MeshBuilder {
    private var d = FloatArray(4096)
    private var n = 0
    var mirror = false
    private var ox = 0f; private var oy = 0f; private var oz = 0f
    private var ca = 1f; private var sa = 0f; private var sc = 1f

    fun at(x: Float = 0f, y: Float = 0f, z: Float = 0f, ang: Float = 0f, s: Float = 1f): MeshBuilder {
        ox = x; oy = y; oz = z; ca = cos(ang); sa = sin(ang); sc = s
        return this
    }

    private fun put(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, m: Int) {
        if (n + 7 > d.size) d = d.copyOf(d.size * 2)
        d[n] = x; d[n + 1] = y; d[n + 2] = z; d[n + 3] = nx; d[n + 4] = ny; d[n + 5] = nz; d[n + 6] = m.toFloat()
        n += 7
    }

    private val tmp = FloatArray(9)

    /** A triangle with normal (nx, ny, nz), in local coordinates. */
    fun tri(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        nx: Float, ny: Float, nz: Float, m: Int,
    ) {
        val p = tmp
        p[0] = ax; p[1] = ay; p[2] = az; p[3] = bx; p[4] = by; p[5] = bz; p[6] = cx; p[7] = cy; p[8] = cz
        val rnx = nx * ca - ny * sa
        val rny = nx * sa + ny * ca
        for (k in 0 until 3) {
            val x = p[k * 3]; val y = p[k * 3 + 1]
            p[k * 3] = ox + (x * ca - y * sa) * sc
            p[k * 3 + 1] = oy + (x * sa + y * ca) * sc
            p[k * 3 + 2] = oz + p[k * 3 + 2] * sc
        }
        for (k in 0 until 3) put(p[k * 3], p[k * 3 + 1], p[k * 3 + 2], rnx, rny, nz, m)
        if (mirror) for (k in 0 until 3) put(p[k * 3], -p[k * 3 + 1], p[k * 3 + 2], rnx, -rny, nz, m)
    }

    /** A triangle whose normal comes from its corners, turned to face along the hint (hx, hy, hz). */
    fun face(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        hx: Float, hy: Float, hz: Float, m: Int,
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l < 1e-7f) return
        nx /= l; ny /= l; nz /= l
        if (nx * hx + ny * hy + nz * hz < 0f) { nx = -nx; ny = -ny; nz = -nz }
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, nx, ny, nz, m)
    }

    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        hx: Float, hy: Float, hz: Float, m: Int,
    ) {
        face(ax, ay, az, bx, by, bz, cx, cy, cz, hx, hy, hz, m)
        face(ax, ay, az, cx, cy, cz, dx, dy, dz, hx, hy, hz, m)
    }

    /**
     * A body with a ridge along y = 0, from a half outline of (x, y, ridge height) triples running nose to tail:
     * sloped roof panels down to the rim at [zTop], walls down to [zBot]. Both sides are built here.
     */
    fun hull(half: FloatArray, zTop: Float, zBot: Float, m: Int) {
        val was = mirror
        mirror = false
        val k = half.size / 3
        for (i in 0 until k - 1) {
            val x0 = half[i * 3]; val y0 = half[i * 3 + 1]; val r0 = half[i * 3 + 2]
            val x1 = half[i * 3 + 3]; val y1 = half[i * 3 + 4]; val r1 = half[i * 3 + 5]
            for (s in intArrayOf(1, -1)) {
                val a = y0 * s; val b = y1 * s
                quad(x0, a, zTop, x1, b, zTop, x1, 0f, r1, x0, 0f, r0, 0f, 0f, 1f, m)
                val ex = x1 - x0; val ey = b - a
                quad(x0, a, zTop, x1, b, zTop, x1, b, zBot, x0, a, zBot, s * ey, -s * ex, 0f, m)
            }
        }
        // Close a blunt nose or tail.
        for (end in intArrayOf(0, k - 1)) {
            val x = half[end * 3]; val y = half[end * 3 + 1]; val r = half[end * 3 + 2]
            if (y <= 1e-4f) continue
            val hx = if (end == 0) 1f else -1f
            face(x, y, zTop, x, -y, zTop, x, 0f, r, hx, 0f, 0f, m)
            quad(x, y, zTop, x, -y, zTop, x, -y, zBot, x, y, zBot, hx, 0f, 0f, m)
        }
        mirror = was
    }

    /** A flat polygon (x, y pairs) with a raised centre at [zPeak], rim at [zTop] and walls down to [zBot]. */
    fun slab(poly: FloatArray, zTop: Float, zPeak: Float, zBot: Float, m: Int, wallM: Int = m) {
        val k = poly.size / 2
        var area = 0f
        var cx = 0f; var cy = 0f
        for (i in 0 until k) {
            val j = (i + 1) % k
            area += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
            cx += poly[i * 2]; cy += poly[i * 2 + 1]
        }
        cx /= k; cy /= k
        val ccw = area > 0f
        for (i in 0 until k) {
            val j = (i + 1) % k
            val ax = poly[i * 2]; val ay = poly[i * 2 + 1]
            val bx = poly[j * 2]; val by = poly[j * 2 + 1]
            face(ax, ay, zTop, bx, by, zTop, cx, cy, zPeak, 0f, 0f, 1f, m)
            val ex = bx - ax; val ey = by - ay
            val s = if (ccw) 1f else -1f
            if (zBot < zTop) quad(ax, ay, zTop, bx, by, zTop, bx, by, zBot, ax, ay, zBot, s * ey, -s * ex, 0f, wallM)
        }
    }

    fun box(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, m: Int) =
        slab(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1), z1, z1, z0, m)

    /** A tapered tube along x at (y, z), from radius [r0] at [x0] to [r1] at [x1], capped at the back with [capM]. */
    fun tube(x0: Float, x1: Float, y: Float, z: Float, r0: Float, r1: Float, seg: Int, m: Int, capM: Int = m) {
        for (i in 0 until seg) {
            val a0 = 2 * PI.toFloat() * i / seg; val a1 = 2 * PI.toFloat() * (i + 1) / seg
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val am = (a0 + a1) / 2
            quad(
                x0, y + c0 * r0, z + s0 * r0, x1, y + c0 * r1, z + s0 * r1, x1, y + c1 * r1, z + s1 * r1, x0, y + c1 * r0, z + s1 * r0,
                (r0 - r1) / (x1 - x0 + 1e-4f), cos(am), sin(am), m,
            )
            face(x0, y, z, x0, y + c0 * r0, z + s0 * r0, x0, y + c1 * r0, z + s1 * r0, -1f, 0f, 0f, capM)
            face(x1, y, z, x1, y + c0 * r1, z + s0 * r1, x1, y + c1 * r1, z + s1 * r1, 1f, 0f, 0f, m)
        }
    }

    /** A flat ring about the origin. */
    fun ring(rIn: Float, rOut: Float, zTop: Float, zBot: Float, seg: Int, m: Int, wallM: Int = m) {
        for (i in 0 until seg) {
            val a0 = 2 * PI.toFloat() * i / seg; val a1 = 2 * PI.toFloat() * (i + 1) / seg
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val cm = cos((a0 + a1) / 2); val sm = sin((a0 + a1) / 2)
            quad(c0 * rIn, s0 * rIn, zTop, c0 * rOut, s0 * rOut, zTop, c1 * rOut, s1 * rOut, zTop, c1 * rIn, s1 * rIn, zTop, 0f, 0f, 1f, m)
            quad(c0 * rOut, s0 * rOut, zTop, c1 * rOut, s1 * rOut, zTop, c1 * rOut, s1 * rOut, zBot, c0 * rOut, s0 * rOut, zBot, cm, sm, 0f, wallM)
            quad(c0 * rIn, s0 * rIn, zTop, c1 * rIn, s1 * rIn, zTop, c1 * rIn, s1 * rIn, zBot, c0 * rIn, s0 * rIn, zBot, -cm, -sm, 0f, wallM)
        }
    }

    /** A four-sided spike from the origin pointing along +x. */
    fun spike(len: Float, w: Float, z: Float, m: Int) {
        val c = floatArrayOf(0f, w, z + w, 0f, -w, z + w, 0f, -w, z - w, 0f, w, z - w)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val ay = c[i * 3 + 1]; val az = c[i * 3 + 2]; val by = c[j * 3 + 1]; val bz = c[j * 3 + 2]
            face(0f, ay, az, 0f, by, bz, len, 0f, z, 0.1f, ay + by, az + bz - 2 * z, m)
        }
    }

    /** A sphere of radius [r], once-subdivided icosahedron, made lumpy by [lump] with a seeded wobble. */
    fun blob(cx: Float, cy: Float, cz: Float, r: Float, lump: Float, seed: Int, m: Int, sub: Int = 1) {
        val (v, f) = Ico.make(sub)
        val fs = seed * 1.37f
        fun rad(x: Float, y: Float, z: Float) = r * (1f + lump * (
            sin(x * 3.1f + fs) * 0.5f + sin(y * 4.3f + fs * 2.1f) * 0.35f + sin(z * 5.7f + fs * 0.7f) * 0.3f + sin((x + y) * 7.9f + fs) * 0.15f
            ) / 1.3f)
        val p = FloatArray(v.size)
        for (i in 0 until v.size / 3) {
            val x = v[i * 3]; val y = v[i * 3 + 1]; val z = v[i * 3 + 2]
            val rr = rad(x, y, z)
            p[i * 3] = cx + x * rr; p[i * 3 + 1] = cy + y * rr; p[i * 3 + 2] = cz + z * rr
        }
        for (t in 0 until f.size / 3) {
            val a = f[t * 3] * 3; val b = f[t * 3 + 1] * 3; val c = f[t * 3 + 2] * 3
            val hx = (p[a] + p[b] + p[c]) / 3 - cx; val hy = (p[a + 1] + p[b + 1] + p[c + 1]) / 3 - cy; val hz = (p[a + 2] + p[b + 2] + p[c + 2]) / 3 - cz
            face(p[a], p[a + 1], p[a + 2], p[b], p[b + 1], p[b + 2], p[c], p[c + 1], p[c + 2], hx, hy, hz, m)
        }
    }

    fun build(): FloatArray = d.copyOf(n)
}

/** Unit icosahedron, subdivided [sub] times. */
object Ico {
    private val cache = HashMap<Int, Pair<FloatArray, IntArray>>()

    fun make(sub: Int): Pair<FloatArray, IntArray> = cache.getOrPut(sub) {
        val t = (1f + sqrt(5f)) / 2f
        val vs = arrayListOf(
            -1f, t, 0f, 1f, t, 0f, -1f, -t, 0f, 1f, -t, 0f, 0f, -1f, t, 0f, 1f, t,
            0f, -1f, -t, 0f, 1f, -t, t, 0f, -1f, t, 0f, 1f, -t, 0f, -1f, -t, 0f, 1f,
        )
        for (i in 0 until 12) {
            val l = sqrt(vs[i * 3] * vs[i * 3] + vs[i * 3 + 1] * vs[i * 3 + 1] + vs[i * 3 + 2] * vs[i * 3 + 2])
            vs[i * 3] /= l; vs[i * 3 + 1] /= l; vs[i * 3 + 2] /= l
        }
        var fs = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )
        repeat(sub) {
            val mids = HashMap<Long, Int>()
            fun mid(a: Int, b: Int): Int {
                val key = if (a < b) a.toLong() * 100000 + b else b.toLong() * 100000 + a
                return mids.getOrPut(key) {
                    var x = (vs[a * 3] + vs[b * 3]) / 2; var y = (vs[a * 3 + 1] + vs[b * 3 + 1]) / 2; var z = (vs[a * 3 + 2] + vs[b * 3 + 2]) / 2
                    val l = sqrt(x * x + y * y + z * z)
                    x /= l; y /= l; z /= l
                    vs.add(x); vs.add(y); vs.add(z)
                    vs.size / 3 - 1
                }
            }
            val out = IntArray(fs.size * 4)
            for (i in 0 until fs.size / 3) {
                val a = fs[i * 3]; val b = fs[i * 3 + 1]; val c = fs[i * 3 + 2]
                val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
                intArrayOf(a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca).copyInto(out, i * 12)
            }
            fs = out
        }
        vs.toFloatArray() to fs
    }
}
