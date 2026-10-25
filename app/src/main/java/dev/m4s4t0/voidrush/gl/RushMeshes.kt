package dev.m4s4t0.voidrush.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every mesh in the game, built in side-view space: x forward (the way the runner runs), y up, z towards the camera.
 * The runner is a set of parts posed by the renderer: each limb hangs from its joint at the origin, down along -y.
 * Everything is closed on all sides so the locker's turntable can show it from any angle.
 */
object RushMeshes {
    private const val HALF_PI = (PI / 2).toFloat()

    // ---------------------------------------------------------------- helpers

    /** A convex polygon (x, y pairs) extruded from [zb] to [zf], with both faces raised by [bevel] at the centre. */
    private fun MeshBuilder.solid(poly: FloatArray, zf: Float, zb: Float, m: Int, wallM: Int = m, bevel: Float = 0f) {
        slab(poly, zf, zf + bevel, zb, m, wallM)
        val k = poly.size / 2
        var cx = 0f; var cy = 0f
        for (i in 0 until k) { cx += poly[i * 2]; cy += poly[i * 2 + 1] }
        cx /= k; cy /= k
        for (i in 0 until k) {
            val j = (i + 1) % k
            face(poly[i * 2], poly[i * 2 + 1], zb, poly[j * 2], poly[j * 2 + 1], zb, cx, cy, zb - bevel, 0f, 0f, -1f, m)
        }
    }

    private fun MeshBuilder.block(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, m: Int, bevel: Float = 0f) =
        solid(rect(x0, x1, y0, y1, 0f), z1, z0, m, m, bevel)

    /** A rectangle with its corners cut by [c]. */
    private fun rect(x0: Float, x1: Float, y0: Float, y1: Float, c: Float): FloatArray =
        if (c <= 0f) floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)
        else floatArrayOf(x0 + c, y0, x1 - c, y0, x1, y0 + c, x1, y1 - c, x1 - c, y1, x0 + c, y1, x0, y1 - c, x0, y0 + c)

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 12) = FloatArray(n * 2) { i ->
        val a = 2f * PI.toFloat() * (i / 2) / n
        if (i % 2 == 0) cx + cos(a) * rx else cy + sin(a) * ry
    }

    /** A limb: a tapered tube hanging from the origin down to -[len], at depth [z]. */
    private fun MeshBuilder.limb(len: Float, r0: Float, r1: Float, m: Int, x: Float = 0f, z: Float = 0f) {
        at(x, 0f, 0f, HALF_PI)
        tube(-len, 0f, 0f, z, r1, r0, 8, m)
        at()
    }

    /** A tube standing up from [y0] to [y1] at ([x], [z]). [capM] closes the bottom. */
    private fun MeshBuilder.post(x: Float, y0: Float, y1: Float, z: Float, r0: Float, r1: Float, m: Int, capM: Int = m, seg: Int = 10) {
        at(x, 0f, 0f, HALF_PI)
        tube(y0, y1, 0f, z, r0, r1, seg, m, capM)
        at()
    }

    private fun build(f: MeshBuilder.() -> Unit) = MeshBuilder().apply(f).build()

    // ---------------------------------------------------------------- the runner

    /** From the hips (the origin) up to the shoulders at 0.62. */
    fun torso() = build {
        solid(floatArrayOf(-0.19f, 0.02f, 0.17f, 0.02f, 0.23f, 0.42f, 0.17f, 0.64f, -0.17f, 0.66f, -0.25f, 0.44f), 0.17f, -0.17f, Mat.PAINT, Mat.PAINT, 0.05f)
        // chest plate and the stolen thruster's power line
        solid(floatArrayOf(-0.05f, 0.3f, 0.19f, 0.32f, 0.17f, 0.56f, -0.08f, 0.58f), 0.21f, -0.21f, Mat.TRIM, Mat.TRIM, 0.02f)
        block(-0.22f, 0.12f, 0.36f, 0.4f, -0.225f, 0.225f, Mat.GLOW)
        // belt and buckle
        block(-0.21f, 0.2f, -0.02f, 0.1f, -0.19f, 0.19f, Mat.DARK)
        block(0.13f, 0.21f, 0f, 0.08f, -0.1f, 0.1f, Mat.GLOW)
        // straps over the shoulders
        block(-0.23f, -0.12f, 0.12f, 0.64f, -0.185f, 0.185f, Mat.DARK)
    }

    /** From the neck (the origin) up. The visor faces +x. */
    fun head() = build {
        solid(ellipse(0f, 0.18f, 0.18f, 0.2f, 14), 0.16f, -0.16f, Mat.PAINT, Mat.PAINT, 0.05f)
        solid(rect(0.02f, 0.215f, 0.1f, 0.27f, 0.04f), 0.18f, -0.18f, Mat.GLASS, Mat.GLASS, 0.02f)
        // ear pieces and an antenna
        solid(ellipse(-0.02f, 0.17f, 0.07f, 0.07f, 8), 0.2f, -0.2f, Mat.TRIM)
        post(-0.08f, 0.32f, 0.5f, -0.1f, 0.018f, 0.012f, Mat.DARK, seg = 6)
        blob(-0.08f, 0.51f, -0.1f, 0.035f, 0f, 1, Mat.GLOW, 0)
        // neck
        post(0f, -0.04f, 0.04f, 0f, 0.08f, 0.08f, Mat.DARK, seg = 8)
    }

    fun upperArm() = build {
        blob(0f, -0.02f, 0f, 0.105f, 0.05f, 2, Mat.PAINT, 1)
        limb(0.3f, 0.075f, 0.065f, Mat.TRIM)
    }

    fun foreArm() = build {
        limb(0.27f, 0.068f, 0.058f, Mat.PAINT)
        block(-0.065f, 0.065f, -0.2f, -0.13f, -0.075f, 0.075f, Mat.GLOW)
        blob(0.01f, -0.3f, 0f, 0.07f, 0.1f, 3, Mat.DARK, 1)
    }

    fun thigh() = build {
        blob(0f, 0f, 0f, 0.1f, 0f, 4, Mat.DARK, 1)
        limb(0.42f, 0.1f, 0.082f, Mat.PAINT)
        block(0.02f, 0.11f, -0.36f, -0.2f, -0.085f, 0.085f, Mat.TRIM)
    }

    /** The shin and the boot; the sole is at -0.46. */
    fun shin() = build {
        limb(0.36f, 0.08f, 0.068f, Mat.TRIM)
        solid(floatArrayOf(-0.085f, -0.46f, 0.18f, -0.46f, 0.2f, -0.41f, 0.06f, -0.32f, -0.085f, -0.32f), 0.085f, -0.085f, Mat.DARK, Mat.DARK, 0.02f)
        block(-0.09f, 0.2f, -0.475f, -0.445f, -0.09f, 0.09f, Mat.GLOW)
    }

    /** The flip boots, drawn over the shin: heavy gravity plates. */
    fun flipBoot() = build {
        solid(floatArrayOf(-0.13f, -0.5f, 0.24f, -0.5f, 0.26f, -0.42f, 0.1f, -0.25f, -0.13f, -0.25f), 0.12f, -0.12f, Mat.PAINT, Mat.TRIM, 0.03f)
        block(-0.14f, 0.27f, -0.53f, -0.49f, -0.13f, 0.13f, Mat.GLOW)
        at(0.04f, -0.38f)
        ring(0.05f, 0.08f, 0.135f, -0.135f, 10, Mat.GLOW, Mat.TRIM)
        at()
    }

    // ---------------------------------------------------------------- packs (on the torso, the nozzle near (-0.36, -0.12))

    private fun MeshBuilder.nozzle(x: Float, z: Float, r: Float = 0.1f) {
        post(x, -0.14f, 0.08f, z, r, r * 0.75f, Mat.DARK, Mat.GLOW)
        post(x, -0.17f, -0.12f, z, r * 1.15f, r * 1.1f, Mat.TRIM, Mat.GLOW)
    }

    fun pack(style: Int) = build {
        when (style) {
            1 -> {
                for (z in floatArrayOf(-0.11f, 0.11f)) {
                    post(-0.37f, 0.06f, 0.52f, z, 0.11f, 0.11f, Mat.PAINT, Mat.DARK)
                    post(-0.37f, 0.52f, 0.68f, z, 0.11f, 0.03f, Mat.TRIM)
                    post(-0.37f, 0.3f, 0.36f, z, 0.118f, 0.118f, Mat.GLOW)
                    nozzle(-0.37f, z, 0.09f)
                }
                block(-0.3f, -0.2f, 0.2f, 0.45f, -0.1f, 0.1f, Mat.DARK)
            }
            2 -> {
                block(-0.42f, -0.2f, 0.1f, 0.55f, -0.15f, 0.15f, Mat.TRIM, 0.03f)
                at(-0.42f, 0.36f)
                ring(0.3f, 0.4f, 0.05f, -0.05f, 28, Mat.GLOW, Mat.PAINT)
                ring(0.4f, 0.45f, 0.035f, -0.035f, 28, Mat.PAINT)
                at()
                blob(-0.42f, 0.36f, 0f, 0.13f, 0f, 5, Mat.GLOW, 1)
                nozzle(-0.34f, 0f)
            }
            3 -> {
                block(-0.47f, -0.2f, 0.06f, 0.58f, -0.16f, 0.16f, Mat.PAINT, 0.04f)
                for (z in floatArrayOf(-0.12f, 0.12f)) {
                    solid(floatArrayOf(-0.4f, 0.4f, -0.3f, 0.58f, -0.85f, 0.92f), z + 0.025f, z - 0.025f, Mat.TRIM)
                    solid(floatArrayOf(-0.86f, 0.9f, -0.82f, 0.95f, -0.42f, 0.6f, -0.42f, 0.54f), z + 0.03f, z - 0.03f, Mat.GLOW)
                }
                solid(floatArrayOf(-0.45f, 0.1f, -0.3f, 0.2f, -0.7f, -0.05f), 0.02f, -0.02f, Mat.TRIM)
                nozzle(-0.36f, 0f)
            }
            4 -> {
                block(-0.32f, -0.2f, 0.08f, 0.56f, -0.12f, 0.12f, Mat.DARK)
                blob(-0.46f, 0.32f, 0f, 0.21f, 0.18f, 7, Mat.DARK, 2)
                at(-0.46f, 0.32f)
                ring(0.25f, 0.31f, 0.04f, -0.04f, 24, Mat.GLOW)
                at()
                at(-0.46f, 0.32f, 0f, 0.9f)
                ring(0.34f, 0.37f, 0.02f, -0.02f, 24, Mat.PAINT)
                at()
                nozzle(-0.34f, 0f, 0.07f)
            }
            5 -> {
                block(-0.4f, -0.2f, 0.12f, 0.52f, -0.13f, 0.13f, Mat.TRIM, 0.03f)
                for (z in floatArrayOf(-0.2f, 0.2f)) {
                    solid(floatArrayOf(-0.32f, 0.36f, -0.3f, 0.5f, -1.25f, 1.25f, -1.35f, 1.12f), z + 0.02f, z - 0.02f, Mat.GLASS)
                    solid(floatArrayOf(-0.3f, 0.5f, -0.33f, 0.56f, -1.3f, 1.3f, -1.25f, 1.25f), z + 0.03f, z - 0.03f, Mat.GLOW)
                    solid(floatArrayOf(-0.32f, 0.3f, -0.3f, 0.4f, -1.1f, 0.5f, -1.15f, 0.42f), z + 0.02f, z - 0.02f, Mat.GLASS)
                }
                nozzle(-0.32f, 0f, 0.08f)
            }
            else -> {
                block(-0.46f, -0.2f, 0.06f, 0.58f, -0.16f, 0.16f, Mat.TRIM, 0.04f)
                block(-0.48f, -0.22f, 0.4f, 0.46f, -0.17f, 0.17f, Mat.GLOW)
                // the asset tag
                block(-0.4f, -0.3f, 0.16f, 0.28f, 0.16f, 0.18f, Mat.PAINT)
                post(-0.33f, 0.58f, 0.66f, 0f, 0.06f, 0.04f, Mat.DARK, seg = 8)
                nozzle(-0.36f, 0f)
            }
        }
    }

    // ---------------------------------------------------------------- rigs

    /** The Strider's cockpit tub, centred on the runner's height; the hips are at (0, -0.6, ±0.5). */
    fun striderBody() = build {
        solid(floatArrayOf(-0.95f, -0.15f, -0.7f, -0.75f, 0.6f, -0.75f, 1.05f, -0.3f, 1.0f, 0.15f, -0.9f, 0.2f), 0.55f, -0.55f, Mat.PAINT, Mat.PAINT, 0.08f)
        solid(floatArrayOf(-0.9f, 0.15f, 1.0f, 0.1f, 1.02f, 0.24f, -0.92f, 0.3f), 0.58f, -0.58f, Mat.TRIM)
        block(0.55f, 1.0f, -0.45f, -0.1f, -0.6f, 0.6f, Mat.GLASS, 0.02f)
        block(-0.6f, 0.4f, -0.5f, -0.38f, -0.62f, 0.62f, Mat.GLOW)
        // the stomp cannon and the exhausts
        tube(0.9f, 1.45f, -0.55f, 0.35f, 0.1f, 0.08f, 8, Mat.DARK, Mat.DARK)
        for (z in floatArrayOf(-0.3f, 0.3f)) post(-0.85f, -0.2f, 0.45f, z, 0.08f, 0.07f, Mat.DARK, Mat.GLOW, 8)
        for (z in floatArrayOf(-0.5f, 0.5f)) blob(0f, -0.62f, z, 0.2f, 0f, 9, Mat.DARK, 1)
    }

    /** A Strider thigh, 0.7 long. */
    fun striderThigh() = build {
        limb(0.7f, 0.16f, 0.13f, Mat.TRIM)
        block(-0.06f, 0.2f, -0.55f, -0.15f, -0.12f, 0.12f, Mat.PAINT, 0.03f)
        blob(0f, -0.7f, 0f, 0.15f, 0f, 11, Mat.DARK, 1)
    }

    /** A Strider shin and foot; the sole is at -0.82. */
    fun striderShin() = build {
        limb(0.66f, 0.12f, 0.1f, Mat.PAINT)
        post(-0.14f, -0.6f, -0.1f, 0f, 0.04f, 0.04f, Mat.GLOW, seg = 6)
        solid(floatArrayOf(-0.35f, -0.82f, 0.45f, -0.82f, 0.38f, -0.66f, -0.25f, -0.64f), 0.22f, -0.22f, Mat.DARK, Mat.DARK, 0.03f)
        block(-0.36f, 0.46f, -0.85f, -0.8f, -0.23f, 0.23f, Mat.GLOW)
    }

    /** The glider's wing, built facing the camera; the renderer tilts it back. */
    fun gliderWing() = build {
        solid(floatArrayOf(1.0f, 0f, -0.9f, 0.85f, -1.3f, 0.75f, -0.75f, 0f, -1.3f, -0.75f, -0.9f, -0.85f), 0.03f, -0.03f, Mat.PAINT)
        solid(floatArrayOf(1.0f, 0f, 0.95f, 0.08f, -0.95f, 0.9f, -0.9f, 0.85f), 0.05f, -0.05f, Mat.GLOW)
        solid(floatArrayOf(1.0f, 0f, -0.9f, -0.85f, -0.95f, -0.9f, 0.95f, -0.08f), 0.05f, -0.05f, Mat.GLOW)
        tube(-0.8f, 1.0f, 0f, 0f, 0.05f, 0.04f, 6, Mat.DARK)
    }

    /** The glider's A-frame, from the wing (0, 0) down to the bar the runner holds (0, -1). */
    fun gliderFrame() = build {
        for (s in floatArrayOf(-1f, 1f)) for (z in floatArrayOf(-0.35f, 0.35f)) {
            at(0f, 0f, 0f, HALF_PI + 0.17f * s)
            tube(-1.02f, 0f, 0f, z, 0.025f, 0.025f, 5, Mat.DARK)
            at()
        }
        block(-0.15f, 0.2f, -1.04f, -0.97f, -0.4f, 0.4f, Mat.TRIM)
    }

    /** The blink drive: a hover disc, built flat facing the camera; the renderer tips it over. */
    fun blinkDisc() = build {
        solid(ellipse(0f, 0f, 0.7f, 0.7f, 20), 0.07f, -0.07f, Mat.DARK, Mat.TRIM, 0.02f)
        ring(0.72f, 0.86f, 0.06f, -0.06f, 28, Mat.GLOW, Mat.PAINT)
        ring(0.3f, 0.38f, 0.1f, 0.04f, 20, Mat.GLOW)
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f
            at(cos(a) * 0.55f, sin(a) * 0.55f, 0f, a)
            block(-0.08f, 0.08f, -0.03f, 0.03f, 0.06f, 0.1f, Mat.PAINT)
        }
        at()
    }

    /** The eel's head, facing +x, about 0.6 across. */
    fun eelHead() = build {
        blob(0f, 0f, 0f, 0.55f, 0.12f, 13, Mat.PAINT, 2)
        solid(floatArrayOf(0.1f, -0.3f, 0.75f, -0.18f, 0.7f, -0.05f, 0.1f, -0.05f), 0.3f, -0.3f, Mat.DARK, Mat.DARK, 0.04f)
        for (z in floatArrayOf(-0.3f, 0.3f)) blob(0.3f, 0.15f, z, 0.12f, 0f, 14, Mat.GLOW, 1)
        for (s in floatArrayOf(-1f, 1f)) solid(floatArrayOf(-0.2f, 0.35f, 0.1f, 0.45f, -0.65f, 0.85f), 0.25f * s + 0.04f, 0.25f * s - 0.04f, Mat.TRIM)
        solid(floatArrayOf(-0.35f, 0.3f, 0.05f, 0.5f, -0.55f, 0.95f), 0.03f, -0.03f, Mat.GLOW)
    }

    /** One body segment of the eel, radius about 0.45, with a dorsal fin. */
    fun eelSegment() = build {
        blob(0f, 0f, 0f, 0.45f, 0.08f, 15, Mat.PAINT, 1)
        solid(floatArrayOf(-0.25f, 0.3f, 0.2f, 0.32f, -0.15f, 0.72f), 0.03f, -0.03f, Mat.GLOW)
        block(-0.3f, 0.3f, -0.2f, -0.08f, -0.47f, 0.47f, Mat.TRIM)
    }

    // ---------------------------------------------------------------- hazards

    /** A zapper emitter; the wire leaves along +x. */
    fun zapNode() = build {
        solid(rect(-0.34f, 0.2f, -0.32f, 0.32f, 0.1f), 0.3f, -0.3f, Mat.DARK, Mat.TRIM, 0.05f)
        block(-0.4f, -0.3f, -0.26f, 0.26f, -0.32f, 0.32f, Mat.PAINT)
        at(0.2f, 0f, 0f, 0f)
        tube(0f, 0.18f, 0f, 0f, 0.2f, 0.12f, 10, Mat.TRIM, Mat.TRIM)
        at()
        blob(0.4f, 0f, 0f, 0.13f, 0f, 16, Mat.GLOW, 1)
        for (s in floatArrayOf(-1f, 1f)) block(-0.2f, 0.12f, 0.28f * s - 0.04f, 0.28f * s + 0.04f, -0.31f, 0.31f, Mat.GLOW)
    }

    /** A seeker missile pointing -x. */
    fun missile() = build {
        tube(-0.55f, 0.55f, 0f, 0f, 0.2f, 0.2f, 12, Mat.PAINT)
        tube(-1.0f, -0.55f, 0f, 0f, 0.02f, 0.2f, 12, Mat.TRIM)
        tube(0.55f, 0.75f, 0f, 0f, 0.17f, 0.13f, 12, Mat.DARK, Mat.DARK)
        tube(-0.2f, -0.05f, 0f, 0f, 0.21f, 0.21f, 12, Mat.GLOW)
        for (k in 0 until 4) {
            val a = k * HALF_PI + 0.785f
            val c = cos(a); val s = sin(a)
            // fins in the (y, z) cross-section, swept back
            face(0.3f, c * 0.18f, s * 0.18f, 0.72f, c * 0.18f, s * 0.18f, 0.78f, c * 0.48f, s * 0.48f, 0f, -s, c, Mat.TRIM)
            face(0.3f, c * 0.18f, s * 0.18f, 0.72f, c * 0.18f, s * 0.18f, 0.78f, c * 0.48f, s * 0.48f, 0f, s, -c, Mat.TRIM)
        }
    }

    /** A security drone looking -x. */
    fun drone() = build {
        blob(0f, 0f, 0f, 0.5f, 0.04f, 17, Mat.DARK, 2)
        solid(ellipse(0f, -0.02f, 0.52f, 0.14f, 14), 0.4f, -0.4f, Mat.PAINT, Mat.PAINT, 0.05f)
        blob(-0.38f, 0.04f, 0f, 0.2f, 0f, 18, Mat.GLOW, 1)
        for (s in floatArrayOf(-1f, 1f)) {
            block(-0.05f, 0.05f, 0.4f, 0.62f, s * 0.3f - 0.04f, s * 0.3f + 0.04f, Mat.TRIM)
            block(-0.42f, 0.42f, 0.62f, 0.67f, s * 0.3f - 0.1f, s * 0.3f + 0.1f, Mat.GLOW)
        }
        post(0.1f, -0.75f, -0.4f, 0f, 0.03f, 0.05f, Mat.TRIM, Mat.GLOW, 6)
    }

    /** A laser emitter, looking +x. */
    fun laserEmitter() = build {
        solid(rect(-0.7f, 0.3f, -0.42f, 0.42f, 0.12f), 0.4f, -0.4f, Mat.DARK, Mat.TRIM, 0.05f)
        tube(0.3f, 0.55f, 0f, 0f, 0.3f, 0.24f, 12, Mat.TRIM, Mat.TRIM)
        blob(0.55f, 0f, 0f, 0.2f, 0f, 19, Mat.GLOW, 1)
        for (s in floatArrayOf(-1f, 1f)) block(-0.6f, 0.2f, s * 0.42f - 0.05f, s * 0.42f + 0.05f, -0.42f, 0.42f, Mat.GLOW)
    }

    /** The rig token: a crate with the foundry's mark on every side. */
    fun crate() = build {
        solid(rect(-0.5f, 0.5f, -0.5f, 0.5f, 0.12f), 0.5f, -0.5f, Mat.TRIM, Mat.TRIM, 0.04f)
        for (s in floatArrayOf(-1f, 1f)) {
            solid(rect(-0.3f, 0.3f, -0.3f, 0.3f, 0.1f), s * 0.56f, s * 0.48f, Mat.GLOW)
            block(-0.53f, 0.53f, s * 0.42f - 0.05f, s * 0.42f + 0.05f, -0.53f, 0.53f, Mat.DARK)
        }
    }
}
