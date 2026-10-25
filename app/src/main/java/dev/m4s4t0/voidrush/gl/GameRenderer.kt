package dev.m4s4t0.voidrush.gl

import android.opengl.GLES20.GL_ARRAY_BUFFER
import android.opengl.GLES20.GL_BLEND
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_DEPTH_BUFFER_BIT
import android.opengl.GLES20.GL_DEPTH_TEST
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_LEQUAL
import android.opengl.GLES20.GL_ONE
import android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA
import android.opengl.GLES20.GL_TRIANGLE_STRIP
import android.opengl.GLES20.glBindBuffer
import android.opengl.GLES20.glBlendFunc
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glDepthFunc
import android.opengl.GLES20.glDepthMask
import android.opengl.GLES20.glDisable
import android.opengl.GLES20.glDisableVertexAttribArray
import android.opengl.GLES20.glDrawArrays
import android.opengl.GLES20.glEnable
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.GLES20.glViewport
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import dev.m4s4t0.voidrush.game.Flair
import dev.m4s4t0.voidrush.game.Particles
import dev.m4s4t0.voidrush.game.PKind
import dev.m4s4t0.voidrush.game.RunConfig
import dev.m4s4t0.voidrush.game.Shape
import dev.m4s4t0.voidrush.game.World
import dev.m4s4t0.voidrush.game.WorldListener
import dev.m4s4t0.voidrush.model.Pack
import dev.m4s4t0.voidrush.model.Paint
import dev.m4s4t0.voidrush.model.Quality
import dev.m4s4t0.voidrush.model.Rig
import dev.m4s4t0.voidrush.model.swatch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Draws the foundry, the runner and everything trying to stop them. Everything here runs on the GL thread; the UI talks
 * to it through [GameView.gl]. The camera looks straight at the corridor, which spans ±[World.H] vertically and
 * ±[World.W] across.
 */
class GameRenderer(private val quality: Quality, listener: WorldListener) : GLSurfaceView.Renderer {
    enum class Mode { MENU, LOCKER, PLAY }

    val world = World().also { it.listener = listener; it.parts.limit = (it.parts.cap * quality.particles).toInt() }
    var mode = Mode.MENU
        private set
    var paused = false
    var shakeOn = true
    var viewW = 1
    var viewH = 1
    /** The suite theme's accent and secondary colours, for the corridor's lights. */
    var accent = 0xFF8B5CF6.toInt()
    var accent2 = 0xFF22D3EE.toInt()

    private lateinit var lit: Program
    private lateinit var sprite: Program
    private lateinit var back: Program
    private val batch = Batch(7000)
    private val quadVbo = floats(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val meshes = HashMap<Any, LitMesh>()
    private fun mesh(key: Any, make: () -> FloatArray) = meshes.getOrPut(key) { LitMesh(make()) }

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val stack = Array(16) { FloatArray(16) }
    private var depth = 0
    private var aspect = 1.7f
    private var w = 1
    private var h = 1
    private var last = 0L
    private var time = 0f
    private var started = false
    private val camZ = World.H / tan(Math.toRadians(20.0)).toFloat()
    private var camX = 0f
    private var camY = 0f

    // The locker's turntable, and what it shows.
    private var yaw = 0.6f
    private var yawV = 0f
    private var lockerPaint = Paint()
    private var lockerPack = Pack.PROTO.style
    private var lockerRig: Rig? = null

    // The runner's eased state.
    private var flipA = 0f
    private var groundK = 1f
    private var thrustK = 0f
    private var lastRig: Rig? = null
    private val pose = FloatArray(10)
    private val pa = FloatArray(10)
    private val pb = FloatArray(10)

    // ---------------------------------------------------------------- commands

    /** The attract loop behind the home screen. */
    fun menu(cfg: RunConfig = RunConfig(seed = 7)) {
        mode = Mode.MENU; paused = false
        world.demo(cfg)
        started = true
    }

    /** The turntable, showing the runner in [paint] wearing [pack], optionally on [rig]. */
    fun locker(paint: Paint, pack: Pack, rig: Rig? = null) {
        if (mode != Mode.LOCKER) { yaw = 0.6f; yawV = 0f }
        mode = Mode.LOCKER; paused = false
        lockerPaint = paint; lockerPack = pack.style; lockerRig = rig
    }

    fun play(cfg: RunConfig) {
        mode = Mode.PLAY; paused = false
        world.start(cfg)
        flipA = 0f; groundK = 1f; thrustK = 0f
        started = true
    }

    fun drag(dx: Float) { yaw += dx * 0.01f; yawV = 0f }
    fun fling(v: Float) { yawV = v * 0.012f }

    // ---------------------------------------------------------------- GL

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        lit = Program(Shaders.LIT_VS, Shaders.LIT_FS)
        sprite = Program(Shaders.SPRITE_VS, Shaders.SPRITE_FS)
        back = Program(Shaders.SKY_VS, Shaders.FOUNDRY_FS)
        batch.init()
        meshes.clear()
        glClearColor(0f, 0f, 0f, 1f)
        glDepthFunc(GL_LEQUAL)
        last = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width; h = height
        glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
        world.W = World.H * aspect
        Matrix.perspectiveM(proj, 0, 40f, aspect, 1f, 80f)
        if (!started) menu()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - last) / 1e9f
        // The menu and the locker don't need more than 30 frames a second.
        if (mode != Mode.PLAY && dt < 1f / 31f) {
            try { Thread.sleep(((1f / 30f - dt) * 1000f).toLong().coerceIn(0L, 33L)) } catch (_: InterruptedException) {}
            dt = (System.nanoTime() - last) / 1e9f
        }
        last = System.nanoTime()
        dt = min(dt, 0.1f)
        time += dt
        when (mode) {
            Mode.MENU -> world.step(dt)
            Mode.PLAY -> if (!paused) world.step(dt)
            Mode.LOCKER -> { yaw += yawV * dt * 60f; yawV *= 0.94f; if (abs(yawV) < 0.0005f) yaw += dt * 0.35f }
        }
        if (mode != Mode.LOCKER && !(mode == Mode.PLAY && paused)) ease(dt)

        camX = 0f; camY = 0f
        if (mode == Mode.PLAY && shakeOn && world.shake > 0f) {
            val s = world.shake * world.shake * 0.35f
            camX = (world.rnd.nextFloat() - 0.5f) * s; camY = (world.rnd.nextFloat() - 0.5f) * s
        }
        Matrix.setLookAtM(view, 0, camX, camY, camZ, camX, camY, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        drawBack()
        spritePass { if (mode == Mode.LOCKER) lockerPad() else underGlows() }
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        lit.use()
        lit.f("uTime", time)
        lit.v3("uEye", camX, camY, camZ)
        if (mode == Mode.LOCKER) drawLocker() else drawWorld()
        glDisable(GL_DEPTH_TEST)
        spritePass { if (mode == Mode.LOCKER) lockerSprites() else worldSprites() }
        glDepthMask(true)
    }

    private inline fun spritePass(draw: () -> Unit) {
        glDepthMask(false)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        sprite.use()
        sprite.m4("uVp", vp)
        sprite.f("uZ", 0f)
        batch.begin()
        draw()
        batch.flush(sprite)
        glDisable(GL_BLEND)
    }

    // ---------------------------------------------------------------- backdrop

    private fun drawBack() {
        back.use()
        back.v2("uRes", w.toFloat(), h.toFloat())
        back.f("uTime", time)
        back.f("uOct", quality.nebula.toFloat())
        val scroll = if (mode == Mode.LOCKER) time * 0.4f else world.distance / World.H
        back.f("uScroll", scroll + camX / World.H)
        back.rgb("uA", accent)
        back.rgb("uB", accent2)
        back.f("uFloor", (World.FLOOR - camY) / World.H)
        back.f("uCeil", (World.CEIL - camY) / World.H)
        back.f("uFlash", if (mode == Mode.PLAY) world.flash else 0f)
        back.rgb("uFlashCol", world.flashColor)
        back.f("uDim", if (mode == Mode.LOCKER) 0.55f else 1f)
        val ap = back.a("aPos")
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glEnableVertexAttribArray(ap)
        glVertexAttribPointer(ap, 2, GL_FLOAT, false, 8, quadVbo)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(ap)
    }

    // ---------------------------------------------------------------- matrices

    private fun push() { System.arraycopy(model, 0, stack[depth++], 0, 16) }
    private fun pop() { System.arraycopy(stack[--depth], 0, model, 0, 16) }
    private fun tr(x: Float, y: Float, z: Float = 0f) = Matrix.translateM(model, 0, x, y, z)
    private fun rz(rad: Float) = Matrix.rotateM(model, 0, rad * DEG, 0f, 0f, 1f)
    private fun rx(rad: Float) = Matrix.rotateM(model, 0, rad * DEG, 1f, 0f, 0f)
    private fun ry(rad: Float) = Matrix.rotateM(model, 0, rad * DEG, 0f, 1f, 0f)
    private fun sc(s: Float) = Matrix.scaleM(model, 0, s, s, s)

    private fun paint(p: Paint) {
        lit.rgb("uC0", swatch(p.primary).argb.toInt())
        lit.rgb("uC1", swatch(p.secondary).argb.toInt())
        lit.rgb("uC2", swatch(p.glow).argb.toInt())
        lit.f("uFinish", p.finish.ordinal.toFloat())
        lit.f("uDecal", p.decal.ordinal.toFloat())
    }

    private fun colors(c0: Int, c1: Int, c2: Int, finish: Float = 1f) {
        lit.rgb("uC0", c0); lit.rgb("uC1", c1); lit.rgb("uC2", c2)
        lit.f("uFinish", finish); lit.f("uDecal", 0f)
    }

    private fun draw(m: LitMesh, flash: Float = 0f) {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        lit.m4("uMvp", mvp)
        lit.m4("uModel", model)
        lit.f("uFlash", flash)
        m.draw(lit)
    }

    private fun draw(key: String, flash: Float = 0f, make: () -> FloatArray) = draw(mesh(key, make), flash)

    // ---------------------------------------------------------------- the runner's pose

    // Pose slots: lean, front thigh, front knee, back thigh, back knee, front arm, front elbow, back arm, back elbow, head.
    private fun set(p: FloatArray, vararg v: Float) = v.copyInto(p)
    private fun lerp(out: FloatArray, a: FloatArray, b: FloatArray, f: Float) { for (i in out.indices) out[i] = a[i] + (b[i] - a[i]) * f }

    private fun run(p: FloatArray, ph: Float) {
        val s = sin(ph); val c = cos(ph)
        set(
            p, -0.2f,
            s * 0.75f + 0.1f, -(0.25f + max(0f, -c) * 1.2f),
            -s * 0.75f + 0.1f, -(0.25f + max(0f, c) * 1.2f),
            -s * 0.8f, 1.2f + s * 0.3f, s * 0.8f, 1.2f - s * 0.3f,
            0.08f + sin(ph * 2f) * 0.03f,
        )
    }

    private fun ease(dt: Float) {
        val k = min(1f, dt * 12f)
        flipA += ((if (world.gsign > 0f) 1f else 0f) - flipA) * k
        groundK += ((if (world.grounded) 1f else 0f) - groundK) * min(1f, dt * 16f)
        thrustK += ((if (world.thrusting) 1f else 0f) - thrustK) * k
    }

    /** Works out [pose] for the runner as the world has them, on [r]. */
    private fun posePlay(r: Rig?) {
        val ph = world.stride * PI.toFloat()
        val wob = sin(time * 9f)
        when {
            !world.alive -> set(
                pose, 0f, 0.5f + wob * 0.3f, -0.6f, -0.3f - wob * 0.2f, -0.9f,
                2.5f + sin(time * 7f) * 0.4f, 0.4f, -2.4f + cos(time * 6f) * 0.4f, 0.5f, 0.2f,
            )
            r == Rig.STRIDER || r == Rig.EEL -> set(pose, -0.05f, 1.5f, -1.5f, 1.4f, -1.4f, 0.9f + wob * 0.05f, 0.7f, 0.7f, 0.8f, 0.05f)
            r == Rig.GLIDER -> {
                val sw = sin(time * 3f) * 0.15f
                set(pose, 0.05f, 0.25f + sw, -0.35f, -0.1f - sw, -0.5f, 2.95f, 0.05f, 3.05f, 0.05f, -0.1f)
            }
            r == Rig.BLINK -> set(pose, -0.1f, 0.65f, -1.0f, -0.25f, -0.55f, 1.3f + wob * 0.1f, 0.5f, -1.1f, 0.4f, 0f)
            else -> {
                run(pb, ph)
                // In the air: hunched over the controls while thrusting, flailing on the way down.
                set(pa, -0.25f + 0.1f * wob, 0.45f + wob * 0.15f, -0.9f, -0.1f - wob * 0.15f, -0.5f, 2.5f + wob * 0.3f, 0.5f, 2.2f - wob * 0.3f, 0.6f, 0.15f)
                set(pose, -0.12f, 0.35f, -0.45f, 0.1f, -0.8f, -0.25f, 1.9f, -0.35f, 2.0f, -0.05f)
                lerp(pa, pa, pose, thrustK)
                lerp(pose, pa, pb, groundK)
            }
        }
    }

    /** The runner, hips at the origin of [model] facing +x. */
    private fun runner(p: Paint, pack: Int, boots: Boolean, seated: Boolean, flash: Float) {
        paint(p)
        if (!seated) {
            leg(-0.1f, pose[3], pose[4], boots, flash)
            leg(0.1f, pose[1], pose[2], boots, flash)
        }
        push()
        rz(pose[0])
        draw("torso", flash) { RushMeshes.torso() }
        draw("pack$pack", flash) { RushMeshes.pack(pack) }
        arm(-0.22f, pose[7], pose[8], flash)
        arm(0.22f, pose[5], pose[6], flash)
        push(); tr(0f, 0.66f); rz(pose[9]); draw("head", flash) { RushMeshes.head() }; pop()
        pop()
    }

    private fun leg(z: Float, thigh: Float, knee: Float, boots: Boolean, flash: Float) {
        push()
        tr(0f, 0f, z); rz(thigh)
        draw("thigh", flash) { RushMeshes.thigh() }
        tr(0f, -0.42f); rz(knee)
        draw("shin", flash) { RushMeshes.shin() }
        if (boots) draw("boot", flash) { RushMeshes.flipBoot() }
        pop()
    }

    private fun arm(z: Float, shoulder: Float, elbow: Float, flash: Float) {
        push()
        tr(-0.02f, 0.56f, z); rz(shoulder)
        draw("upper", flash) { RushMeshes.upperArm() }
        tr(0f, -0.3f); rz(elbow)
        draw("fore", flash) { RushMeshes.foreArm() }
        pop()
    }

    // ---------------------------------------------------------------- rigs

    private fun rigColors(p: Paint) {
        val c = swatch(p.secondary).argb.toInt()
        colors(0xFF3A4156.toInt(), c, swatch(p.glow).argb.toInt(), 2f)
    }

    /** The Strider, its body at the origin; the runner rides in it. */
    private fun strider(p: Paint, ph: Float, air: Float, flash: Float) {
        rigColors(p)
        for (side in intArrayOf(-1, 1)) {
            val q = ph + if (side < 0) PI.toFloat() else 0f
            val th = (0.5f + sin(q) * 0.45f) * (1f - air) + 0.95f * air
            val kn = (-1.0f - max(0f, cos(q)) * 0.45f) * (1f - air) - 1.6f * air
            push()
            tr(0f, -0.6f, side * 0.5f); rz(th)
            draw("sThigh", flash) { RushMeshes.striderThigh() }
            tr(0f, -0.7f); rz(kn)
            push(); rz(-(th + kn)); draw("sShin", flash) { RushMeshes.striderShin() }; pop()
            pop()
        }
        draw("sBody", flash) { RushMeshes.striderBody() }
    }

    private fun drawRig(r: Rig, p: Paint, flash: Float, preview: Boolean) {
        val ph = world.stride * PI.toFloat()
        when (r) {
            Rig.STRIDER -> {
                val air = if (preview) 0f else 1f - groundK
                push(); tr(0f, 0.23f); strider(p, if (preview) time * 2f else ph, air, flash)
                tr(-0.15f, -0.05f, 0f); sc(0.9f); runner(p, -1, false, true, flash); pop()
            }
            Rig.GLIDER -> {
                push(); tr(0f, -0.12f); sc(0.9f)
                runner(p, -1, false, false, flash)
                pop()
                val tilt = if (preview) 0f else (world.vy / 20f).coerceIn(-0.4f, 0.4f)
                push()
                tr(0.05f, 1.55f); rz(tilt)
                rigColors(p)
                draw("gFrame", flash) { RushMeshes.gliderFrame() }
                rx(-1.35f); sc(1.4f)
                draw("gWing", flash) { RushMeshes.gliderWing() }
                pop()
            }
            Rig.BLINK -> {
                push(); tr(0f, 0.05f); sc(0.9f); runner(p, -1, false, false, flash); pop()
                push()
                tr(0f, -0.95f + sin(time * 4f) * 0.04f); rx(-1.35f); rz(time * 3f)
                rigColors(p)
                draw("disc", flash) { RushMeshes.blinkDisc() }
                pop()
            }
            Rig.EEL -> {
                val climb = if (preview) 0f else atan2(world.vy, max(world.speed, 6f)).coerceIn(-0.5f, 0.5f)
                push()
                rz(climb)
                rigColors(p)
                for (i in 5 downTo 1) {
                    val s = 1f - i * 0.09f
                    push()
                    tr(0.55f - i * 0.62f, sin(time * 5f - i * 0.9f) * 0.05f * i, 0f); sc(s); rz(sin(time * 5f - i * 0.9f + 0.5f) * 0.2f)
                    draw("eelSeg", flash) { RushMeshes.eelSegment() }
                    pop()
                }
                push(); tr(0.9f, 0.05f); draw("eelHead", flash) { RushMeshes.eelHead() }; pop()
                tr(-0.15f, 0.42f); sc(0.9f); runner(p, -1, false, true, flash)
                pop()
            }
            Rig.FLIP -> { push(); tr(0f, -0.12f); sc(0.9f); runner(p, -1, true, false, flash); pop() }
        }
    }

    // ---------------------------------------------------------------- the world

    private fun drawWorld() {
        val wd = world
        // The runner, blinking while invulnerable.
        val blink = mode == Mode.PLAY && wd.invuln > 0f && wd.alive && ((time * 14f).toInt() and 1) == 0
        if (!blink) {
            Matrix.setIdentityM(model, 0)
            tr(wd.rx, wd.ry)
            val r = wd.rig
            if (r != lastRig) lastRig = r
            if (!wd.alive) rz(if (wd.grounded) PI.toFloat() / 2f else wd.tumble)
            rx(flipA * PI.toFloat())
            if (r != null && wd.rigT < 0.25f) sc(0.6f + wd.rigT / 0.25f * 0.4f)
            posePlay(r)
            if (r == null || !wd.alive) {
                tr(0f, -0.12f); sc(0.9f)
                runner(wd.cfg.paint, wd.cfg.packStyle, false, false, if (wd.invuln > 0f) 0.15f else 0f)
            } else {
                drawRig(r, wd.cfg.paint, 0f, false)
            }
        }

        // Zapper emitters.
        colors(0xFF2C3244.toInt(), 0xFF8A93A8.toInt(), World.ZAP_COLOR, 2f)
        for (z in wd.zappers) if (z.active) {
            for (end in intArrayOf(-1, 1)) {
                Matrix.setIdentityM(model, 0)
                tr(z.x + z.dx * end, z.y + z.dy * end)
                rz(z.ang + if (end > 0) PI.toFloat() else 0f)
                draw("zap") { RushMeshes.zapNode() }
            }
        }
        // Missiles in flight.
        colors(0xFFB8BCC8.toInt(), World.DANGER, 0xFFFFA040.toInt(), 2f)
        for (m in wd.missiles) if (m.active && m.phase == 2) {
            Matrix.setIdentityM(model, 0)
            tr(m.x, m.y); rx(time * 6f)
            draw("missile") { RushMeshes.missile() }
        }
        // Laser emitters, sliding in from the edges.
        colors(0xFF262B3A.toInt(), 0xFF6A7488.toInt(), World.DANGER, 2f)
        for (l in wd.lasers) if (l.active && l.shown) {
            val end = l.warn + l.fire
            val slide = min(1f, min(l.t / 0.3f, (end + 0.4f - l.t) / 0.4f)).coerceIn(0f, 1f)
            for (side in intArrayOf(-1, 1)) {
                Matrix.setIdentityM(model, 0)
                tr(side * (wd.W + 0.8f - slide * 1.3f), l.y)
                if (side > 0) rz(PI.toFloat())
                draw("emitter", if (l.firing) 0.25f else 0f) { RushMeshes.laserEmitter() }
            }
        }
        // Drones.
        colors(0xFF2E3448.toInt(), 0xFF8A93A8.toInt(), World.DANGER, 2f)
        for (d in wd.drones) if (d.active) {
            Matrix.setIdentityM(model, 0)
            tr(d.x, d.y); rz(sin(time * 2.2f + d.seed) * 0.12f); ry(0.35f)
            draw("drone") { RushMeshes.drone() }
        }
        // Rig crates.
        colors(0xFF5A6070.toInt(), 0xFF8A93A8.toInt(), accent, 2f)
        val pk = wd.picks
        for (i in 0 until pk.n) if (pk.kind[i] == PKind.RIG) {
            Matrix.setIdentityM(model, 0)
            tr(pk.x[i], pk.y[i] + sin(pk.age[i] * 3f) * 0.15f); ry(pk.age[i] * 1.6f); rx(0.3f); sc(0.7f)
            draw("crate") { RushMeshes.crate() }
        }
    }

    /** Glows that belong behind the meshes so they never hide them. */
    private fun underGlows() {
        val wd = world
        for (z in wd.zappers) if (z.active) {
            seg(z.x - z.dx, z.y - z.dy, z.x + z.dx, z.y + z.dy, 0.9f, World.ZAP_COLOR, 0.18f, Shape.SOFT)
        }
        for (d in wd.drones) if (d.active) batch.dot(d.x, d.y, 1.3f, World.DANGER, 0.12f, Shape.SOFT)
        if (wd.alive && wd.rig == null) batch.dot(wd.rx, wd.ry, 1.6f, swatch(wd.cfg.paint.glow).argb.toInt(), 0.08f, Shape.SOFT)
    }

    private fun worldSprites() {
        val wd = world
        // Zapper arcs: two jagged bolts that re-strike twenty times a second.
        for (z in wd.zappers) if (z.active) {
            val len = hypot(z.dx, z.dy).coerceAtLeast(0.01f)
            val ux = z.dx / len; val uy = z.dy / len
            val ax = z.x - z.dx + ux * 0.45f; val ay = z.y - z.dy + uy * 0.45f
            val bx = z.x + z.dx - ux * 0.45f; val by = z.y + z.dy - uy * 0.45f
            val strike = (time * 20f).toInt()
            for (k in 0 until 2) bolt(ax, ay, bx, by, z.seed * 31 + strike * 7 + k * 101, if (k == 0) World.ZAP_COLOR else World.WHITE, if (k == 0) 0.22f else 0.09f)
            batch.dot(ax, ay, 0.35f, World.WHITE, 0.6f, Shape.ORB)
            batch.dot(bx, by, 0.35f, World.WHITE, 0.6f, Shape.ORB)
        }
        // Lasers: a warning line while charging, then the beam.
        for (l in wd.lasers) if (l.active && l.shown) {
            if (l.t < l.warn) {
                val charge = l.t / l.warn
                seg(-wd.W, l.y, wd.W, l.y, 0.05f + charge * 0.06f, World.DANGER, 0.4f + 0.3f * sin(time * 40f), Shape.BEAM)
                for (side in intArrayOf(-1, 1)) batch.dot(side * (wd.W - 0.35f), l.y, 0.2f + charge * 0.5f, World.DANGER, 0.5f + charge * 0.5f, Shape.SOFT)
            } else if (l.firing) {
                val fl = 1f + 0.08f * sin(time * 70f)
                seg(-wd.W, l.y, wd.W, l.y, World.LASER_R * 1.6f * fl, World.DANGER, 0.35f, Shape.SOFT)
                seg(-wd.W, l.y, wd.W, l.y, World.LASER_R * fl, World.DANGER, 0.95f, Shape.BEAM)
                seg(-wd.W, l.y, wd.W, l.y, World.LASER_R * 0.55f * fl, World.WHITE, 0.9f, Shape.BEAM)
            }
        }
        // Missile warnings at the right edge, then the flame behind them.
        for (m in wd.missiles) if (m.active && m.shown) {
            if (m.phase < 2) {
                val locked = m.phase == 1
                val on = !locked || ((time * 12f).toInt() and 1) == 0
                val c = if (locked) World.DANGER else 0xFFFFB020.toInt()
                val x = wd.W - 0.9f
                batch.dot(x, m.y, 0.75f, c, 0.25f, Shape.SOFT)
                batch.dot(x, m.y, 0.6f, c, if (on) 0.95f else 0.4f, Shape.RING)
                batch.dot(x, m.y, 0.3f, c, if (on) 1f else 0.5f, Shape.DIAMOND)
            } else {
                val f = 1f + 0.2f * sin(time * 50f)
                batch.quad(m.x + 1.3f, m.y, 0.55f * f, 0.2f, 1f, 0f, 0xFFFFA040.toInt(), 0.9f, Shape.SOFT)
                batch.dot(m.x + 1.05f, m.y, 0.22f, World.WHITE, 0.9f, Shape.ORB)
            }
        }
        // Pickups.
        val pk = wd.picks
        for (i in 0 until pk.n) {
            val bob = 1f + 0.12f * sin(pk.age[i] * 8f + i)
            when (pk.kind[i]) {
                PKind.CHIP -> {
                    val r = (if (pk.value[i] > 1) 0.46f else 0.28f) * bob
                    batch.dot(pk.x[i], pk.y[i], r * 2f, World.CHIP_COLOR, 0.14f, Shape.SOFT)
                    batch.dot(pk.x[i], pk.y[i], r, World.CHIP_COLOR, 1f, Shape.HEX)
                }
                PKind.RIG -> {
                    batch.dot(pk.x[i], pk.y[i], 1.2f * bob, accent, 0.18f, Shape.SOFT)
                    batch.dot(pk.x[i], pk.y[i], 0.95f * bob, accent, 0.7f, Shape.RING)
                }
                PKind.SPIN -> {
                    val c = Flair.rainbow(time * 0.4f + i * 0.1f)
                    batch.dot(pk.x[i], pk.y[i], 1.0f * bob, c, 0.2f, Shape.SOFT)
                    batch.dot(pk.x[i], pk.y[i], 0.5f * bob, c, 1f, Shape.STAR)
                }
            }
        }
        particleSprites(wd.parts)
        // The runner's thruster flame, and the shield bubble.
        if (wd.alive) {
            if (wd.rig == null && thrustK > 0.05f) {
                val dir = if (flipA > 0.5f) 1f else -1f
                val nx = wd.rx - 0.36f
                val ny = wd.ry + dir * 0.24f
                val len = (0.5f + if (wd.boostLeft > 0f) 0.6f else 0f) * thrustK * (1f + 0.15f * sin(time * 47f))
                val fc = wd.flair.flameColor(time)
                batch.quad(nx, ny + dir * len, len, 0.17f, 0f, 1f, fc, 0.85f, Shape.SOFT)
                batch.quad(nx, ny + dir * len * 0.55f, len * 0.55f, 0.08f, 0f, 1f, World.WHITE, 0.9f, Shape.SOFT)
            }
            if (wd.shield) {
                val r = wd.half() + 0.5f + 0.05f * sin(time * 5f)
                batch.dot(wd.rx, wd.ry, r, accent2, 0.12f, Shape.SOFT)
                batch.dot(wd.rx, wd.ry, r, accent2, 0.55f, Shape.RING)
            }
        }
        if (mode == Mode.PLAY && wd.flash > 0f) batch.quad(0f, 0f, wd.W + 2f, World.H + 2f, 1f, 0f, wd.flashColor, wd.flash * 0.35f, Shape.FILL)
    }

    private fun particleSprites(p: Particles) {
        for (i in 0 until p.n) {
            val f = p.life[i] / p.max[i]
            val sh = p.shape[i]
            if (sh == Shape.SPARK) {
                val sp = hypot(p.vx[i], p.vy[i])
                if (sp > 0.5f) {
                    batch.quad(p.x[i], p.y[i], p.size[i] + sp * 0.025f, p.size[i] * 0.45f, p.vx[i] / sp, p.vy[i] / sp, p.color[i], f, Shape.SPARK)
                    continue
                }
            }
            batch.dot(p.x[i], p.y[i], p.size[i], p.color[i], if (sh == Shape.WAVE || sh == Shape.RING) f else min(1f, f * 1.6f), sh)
        }
    }

    /** A jagged bolt from a to b, six segments, jittered by [seed]. */
    private fun bolt(ax: Float, ay: Float, bx: Float, by: Float, seed: Int, col: Int, w: Float) {
        val dx = bx - ax; val dy = by - ay
        val len = hypot(dx, dy).coerceAtLeast(0.01f)
        val nx = -dy / len; val ny = dx / len
        var s = seed
        var px = ax; var py = ay
        for (k in 1..6) {
            val f = k / 6f
            s = s * 1103515245 + 12345
            val j = if (k == 6) 0f else (((s ushr 16) and 0x7FFF) / 32767f - 0.5f) * 0.9f
            val x = ax + dx * f + nx * j; val y = ay + dy * f + ny * j
            seg(px, py, x, y, w, col, 0.95f)
            px = x; py = y
        }
    }

    /** A line of sprite from ([x0], [y0]) to ([x1], [y1]). */
    private fun seg(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, col: Int, a: Float, shape: Int = Shape.BEAM) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = hypot(dx, dy).coerceAtLeast(0.01f)
        batch.quad((x0 + x1) / 2f, (y0 + y1) / 2f, len / 2f + w, w, dx / len, dy / len, col, a, shape)
    }

    // ---------------------------------------------------------------- the locker

    private val lockerX get() = -world.W * 0.3f
    private val lockerFloor = -4.6f

    private fun drawLocker() {
        Matrix.setIdentityM(model, 0)
        val r = lockerRig
        val s = if (r == Rig.STRIDER) 1.6f else 2.6f
        val lift = when (r) { Rig.STRIDER -> 1.93f; Rig.BLINK -> 1.1f; Rig.GLIDER -> 0.9f; Rig.EEL -> 0.85f; else -> 0.9f }
        tr(lockerX, lockerFloor + lift * s + sin(time * 1.6f) * 0.05f, 2f)
        ry(yaw); sc(s)
        // Idle: a slow breath, arms loose.
        val br = sin(time * 1.6f)
        set(pose, -0.03f, 0.08f, -0.12f, -0.06f, -0.1f, 0.12f + br * 0.03f, 0.35f, -0.1f - br * 0.03f, 0.3f, 0.05f + br * 0.02f)
        if (r == null) {
            tr(0f, -0.12f); sc(0.9f)
            runner(lockerPaint, lockerPack, false, false, 0f)
        } else {
            posePlayPreview(r)
            drawRig(r, lockerPaint, 0f, true)
        }
    }

    private fun posePlayPreview(r: Rig) {
        val wob = sin(time * 1.6f) * 0.05f
        when (r) {
            Rig.STRIDER, Rig.EEL -> set(pose, -0.05f, 1.5f, -1.5f, 1.4f, -1.4f, 0.9f + wob, 0.7f, 0.7f, 0.8f, 0.05f)
            Rig.GLIDER -> set(pose, 0.05f, 0.25f + wob, -0.35f, -0.1f - wob, -0.5f, 2.95f, 0.05f, 3.05f, 0.05f, -0.1f)
            Rig.BLINK -> set(pose, -0.1f, 0.65f, -1.0f, -0.25f, -0.55f, 1.3f + wob, 0.5f, -1.1f, 0.4f, 0f)
            Rig.FLIP -> {}
        }
    }

    private fun lockerPad() {
        val c = swatch(lockerPaint.glow).argb.toInt()
        val p = 0.5f + 0.5f * sin(time * 2f)
        batch.dot(lockerX, lockerFloor + 1f, 4.5f, c, 0.1f, Shape.SOFT)
        batch.quad(lockerX, lockerFloor - 0.1f, 3.6f, 0.55f, 1f, 0f, c, 0.35f, Shape.SOFT)
        batch.quad(lockerX, lockerFloor - 0.1f, 3.0f + p * 0.3f, 0.4f + p * 0.05f, 1f, 0f, c, 0.4f * (1f - p * 0.5f), Shape.RING)
    }

    private fun lockerSprites() {
        val c = swatch(lockerPaint.glow).argb.toInt()
        for (k in 0 until 14) {
            val f = ((time * 0.25f + k / 14f) % 1f)
            val x = lockerX + sin(k * 2.39f) * 3.2f
            batch.dot(x, lockerFloor + f * 9f, 0.06f + (k % 3) * 0.03f, c, (1f - f) * 0.7f, Shape.ORB)
        }
    }

    private companion object {
        const val DEG = (180.0 / PI).toFloat()
    }
}
