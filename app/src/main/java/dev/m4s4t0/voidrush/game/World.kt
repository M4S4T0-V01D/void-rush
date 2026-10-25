package dev.m4s4t0.voidrush.game

import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Haptics
import dev.m4s4t0.voidrush.audio.Snd
import dev.m4s4t0.voidrush.model.Boost
import dev.m4s4t0.voidrush.model.Gadget
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.Paint
import dev.m4s4t0.voidrush.model.Rig
import dev.m4s4t0.voidrush.model.RunResult
import dev.m4s4t0.voidrush.model.Upgrade
import dev.m4s4t0.voidrush.model.swatch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the world's sounds and buzzes go (stubbed in tests). */
interface Sfx {
    fun play(s: Snd, gain: Float = 1f, pitch: Float = 1f, pan: Float = 0f, minGapMs: Long = 25)
    fun buzz(k: Haptics.Kind, strength: Float = 1f)
}

object DeviceSfx : Sfx {
    override fun play(s: Snd, gain: Float, pitch: Float, pan: Float, minGapMs: Long) = Audio.play(s, gain, pitch, pan, minGapMs)
    override fun buzz(k: Haptics.Kind, strength: Float) = Haptics.buzz(k, strength)
}

object NoSfx : Sfx {
    override fun play(s: Snd, gain: Float, pitch: Float, pan: Float, minGapMs: Long) {}
    override fun buzz(k: Haptics.Kind, strength: Float) {}
}

/** What the HUD shows, sent about 16 times a second. */
data class Hud(
    val distance: Int,
    val credits: Int,
    val spins: Int,
    val nearMisses: Int,
    val rig: Rig?,
    val shield: Boolean,
    /** The head start left (0..1), 0 when there's none. */
    val boost: Float,
    val best: Long,
)

sealed interface WorldEvent {
    data class Callout(val text: String, val icon: String) : WorldEvent
    /** The runner has skidded to a stop: the UI offers a revive. [revives] is how many this run has had. */
    data class Down(val revives: Int) : WorldEvent
    data class Over(val result: RunResult) : WorldEvent
}

interface WorldListener {
    fun hud(h: Hud)
    fun event(e: WorldEvent)
}

/** Everything a run needs from the save, so the world can run headless in tests. */
data class RunConfig(
    val rigs: List<Rig> = listOf(Rig.STRIDER, Rig.FLIP),
    val gadgets: Set<Gadget> = emptySet(),
    val magnet: Int = 0,
    val rigArmour: Int = 0,
    val thrust: Int = 0,
    val chipValue: Float = 1f,
    val boosts: List<Boost> = emptyList(),
    val paint: Paint = Paint(),
    val packStyle: Int = 0,
    val best: Long = 0,
    val seed: Int = Random.nextInt(),
) {
    companion object {
        fun fromGame(boosts: List<Boost> = emptyList()) = RunConfig(
            rigs = Game.rigs,
            gadgets = Game.loadout.gadgets.toSet(),
            magnet = Game.upgradeLevel(Upgrade.MAGNET),
            rigArmour = Game.upgradeLevel(Upgrade.RIGS),
            thrust = Game.upgradeLevel(Upgrade.THRUST),
            chipValue = Game.chipValue(),
            boosts = boosts,
            paint = Game.loadout.paint,
            packStyle = Game.loadout.pack.style,
            best = Game.bestDistance,
        )
    }
}

/** A live wire between two emitter nodes, centred on ([x], [y]), [len] long at angle [ang]. Some spin, some slide. */
class Zapper {
    var active = false
    var x = 0f; var y = 0f
    var len = 0f; var ang = 0f
    var spin = 0f
    var vy = 0f
    var passed = false; var grazed = false
    var seed = 0
    val dx get() = cos(ang) * len * 0.5f
    val dy get() = sin(ang) * len * 0.5f
}

/** A seeker: it tracks the runner from the right edge ([phase] 0), locks on (1), then flies straight across (2). */
class Missile {
    var active = false
    var x = 0f; var y = 0f
    var off = 0f
    var t = 0f
    var phase = 0
    var passed = false
    val shown get() = t >= 0f
}

/** A beam across the whole corridor at height [y]: it warns for [warn] seconds, then fires for [fire]. */
class Laser {
    var active = false
    var y = 0f
    var t = 0f
    var warn = 0f; var fire = 0f
    var grazed = false
    val firing get() = t >= warn && t < warn + fire
    val shown get() = t >= 0f
}

/** A security drone hovering on a bob. The Strider stomps them flat. */
class Drone {
    var active = false
    var x = 0f; var y = 0f
    var base = 0f; var amp = 0f; var ph = 0f
    var grazed = false; var passed = false
    var seed = 0
}

/**
 * One run down the foundry corridor (or the menu's attract loop). The runner stays at a fixed x and everything else
 * scrolls left at [speed]: one unit is a metre. Lives on the GL thread, stepped by the renderer; the UI hears about it
 * through [listener].
 */
class World(val sfx: Sfx = DeviceSfx) {
    companion object {
        const val H = 9f
        /** The floor and ceiling surfaces. */
        const val FLOOR = -H + 1.4f
        const val CEIL = H - 1f
        /** The runner's half height, for standing on the floor. */
        const val RH = 0.9f
        const val ZAP_R = 0.32f
        const val MISSILE_R = 0.42f
        const val LASER_R = 0.5f
        const val DRONE_R = 0.62f
        /** How close (beyond touching) counts as a near miss. */
        const val NEAR = 1.05f
        const val SPAWN_PAD = 3f
        const val START_SPEED = 11f
        const val TOP_SPEED = 26f
        const val BOOST_SPEED = 58f
        const val MAX_SPINS = 3
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ZAP_COLOR = 0xFF7DF9FF.toInt()
        const val DANGER = 0xFFFF3050.toInt()
        const val CHIP_COLOR = 0xFFFFD050.toInt()

        /** The base speed at [distance] metres: quick at first, flattening towards [TOP_SPEED]. */
        fun speedAt(distance: Float) = START_SPEED + (TOP_SPEED - START_SPEED) * (1f - exp(-distance / 3500f))

        /** Chip formations: X a chip, O a big one worth five. */
        private val SHAPES = listOf(
            arrayOf(" XX XX ", "XXXXXXX", "XXXXXXX", " XXXXX ", "  XXX  ", "   X   "),
            arrayOf("X     X", "X     X", " X   X ", " X   X ", "  X X  ", "   O   "),
            arrayOf("   X   ", "  XXX  ", " XXOXX ", "XXOOOXX", " XXOXX ", "  XXX  ", "   X   "),
            arrayOf("XX    ", " XX   ", "  XX  ", "   XXO", "  XX  ", " XX   ", "XX    "),
            arrayOf("XXXXXXXXX", "X       X", "X  OOO  X", "X       X", "XXXXXXXXX"),
            arrayOf("  XXXXX  ", " X     X ", "X  O O  X", "X       X", "X X   X X", " X XXX X ", "  XXXXX  "),
        )

        /** Laser waves as lane sets (5 lanes, 0 at the bottom); each pattern is a list of waves. */
        private val LASER_WAVES = listOf(
            listOf(intArrayOf(0, 2, 4)),
            listOf(intArrayOf(1, 3)),
            listOf(intArrayOf(0, 1, 2)),
            listOf(intArrayOf(2, 3, 4)),
            listOf(intArrayOf(0, 4), intArrayOf(1, 3)),
            listOf(intArrayOf(0, 1), intArrayOf(3, 4), intArrayOf(1, 2, 3)),
            listOf(intArrayOf(4), intArrayOf(3), intArrayOf(2), intArrayOf(1), intArrayOf(0)),
            listOf(intArrayOf(0, 2, 4), intArrayOf(1, 3), intArrayOf(0, 2, 4)),
        )
    }

    enum class State { DEMO, PLAY, DYING, DOWN, OVER }

    var listener: WorldListener? = null
    /** Half the corridor's visible width; set from the view's aspect. */
    var W = 16f
    var state = State.DEMO
        private set
    var cfg = RunConfig(seed = 1)
        private set
    var rnd = Random(1)
    var t = 0f
        private set

    val parts = Particles(1400)
    val picks = Pickups(420)
    val zappers = Array(28) { Zapper() }
    val missiles = Array(8) { Missile() }
    val lasers = Array(8) { Laser() }
    val drones = Array(12) { Drone() }
    val flair = Flair()

    var shake = 0f
    var flash = 0f
    var flashColor = WHITE

    // ---- the runner ----

    val rx get() = -W * 0.45f
    var ry = FLOOR + RH
        private set
    var vy = 0f
        private set
    var grounded = true
        private set
    /** -1: gravity pulls to the floor; +1: to the ceiling (the flip boots). */
    var gsign = -1f
        private set
    var rig: Rig? = null
        private set
    /** Seconds since the rig was mounted, for the mount animation. */
    var rigT = 0f
        private set
    var shield = false
        private set
    var invuln = 0f
        private set
    var boostLeft = 0f
        private set
    private var boostTotal = 0f
    /** The run cycle, in strides. */
    var stride = 0f
        private set
    var thrusting = false
        private set
    /** The ragdoll's tumble once down, radians. */
    var tumble = 0f
        private set
    var deadT = 0f
        private set

    private var holds = 0
    private var holdT = 0f
    private var tapped = false
    private var blinkCool = 0f

    // ---- the run ----

    var distance = 0f
        private set
    var speed = START_SPEED
        private set
    private var resumeSpeed = START_SPEED
    var chips = 0
        private set
    private var chipCredits = 0f
    private var bonusCredits = 0
    var nearMisses = 0
        private set
    private var rigsRidden = 0
    private var zapped = 0
    private var dodged = 0
    private var outlasted = 0
    private var stomped = 0
    private var flips = 0
    private var blinks = 0
    var spins = 0
        private set
    var revives = 0
        private set
    private var seconds = 0f
    private var air = 0f
    private var bestAir = 0f
    private var dry = 0f
    private var bestDry = 0f
    private var cause = "quit"

    private var spawnIn = 0f
    private var laserLock = false
    private var missileT = 0f
    private var rigIn = 0f
    private var spinIn = 0f
    private var hudT = 0f
    private var trailT = 0f
    private var lastRig: Rig? = null

    val credits get() = (chipCredits).roundToInt() + bonusCredits
    val playing get() = state == State.PLAY
    val alive get() = state == State.PLAY || state == State.DEMO
    /** The head start left, 0..1. */
    val boostFrac get() = if (boostTotal > 0f) boostLeft / boostTotal else 0f

    // ---------------------------------------------------------------- lifecycle

    /** Starts a real run. */
    fun start(c: RunConfig) {
        reset(c)
        state = State.PLAY
        boostTotal = c.boosts.sumOf { b -> when (b) { Boost.HEADSTART -> 750; Boost.MEGA -> 2000; else -> 0 }.toInt() }.toFloat()
        boostLeft = boostTotal
        shield = Boost.BUBBLE in c.boosts
        // The breakout: the runner bursts through the lab wall.
        explode(rx - 1.2f, ry + 1.2f, 1.6f, flair.trail)
        repeat(18) {
            val a = rnd.nextFloat() * 2f - 1f
            parts.add(rx - 1.5f, ry + 1f + a * 2f, 6f + rnd.nextFloat() * 8f, a * 6f + 3f, 0.9f, 0.18f + rnd.nextFloat() * 0.2f, 0xFF8A93A8.toInt(), Shape.DIAMOND, 1.2f)
        }
        shake = 0.9f
        sfx.play(Snd.BIG_BOOM, 0.8f, minGapMs = 0)
        sfx.buzz(Haptics.Kind.BOOM)
    }

    /** The menu's attract loop: the runner weaves through chips on autopilot and can't die. */
    fun demo(c: RunConfig) {
        reset(c)
        state = State.DEMO
    }

    private fun reset(c: RunConfig) {
        cfg = c
        rnd = Random(c.seed)
        flair.set(c.paint, swatch(c.paint.glow).argb.toInt())
        t = 0f
        parts.n = 0; picks.n = 0
        zappers.forEach { it.active = false }
        missiles.forEach { it.active = false }
        lasers.forEach { it.active = false }
        drones.forEach { it.active = false }
        ry = FLOOR + RH; vy = 0f; grounded = true; gsign = -1f
        rig = null; rigT = 0f; shield = false; invuln = 0f; boostLeft = 0f; boostTotal = 0f
        stride = 0f; thrusting = false; tumble = 0f; deadT = 0f
        holds = 0; holdT = 0f; tapped = false; blinkCool = 0f
        distance = 0f; speed = START_SPEED; resumeSpeed = START_SPEED
        chips = 0; chipCredits = 0f; bonusCredits = 0; nearMisses = 0
        rigsRidden = 0; zapped = 0; dodged = 0; outlasted = 0; stomped = 0; flips = 0; blinks = 0
        spins = 0; revives = 0; seconds = 0f; air = 0f; bestAir = 0f; dry = 0f; bestDry = 0f; cause = "quit"
        spawnIn = 22f; laserLock = false; missileT = 6f
        rigIn = (380f + rnd.nextFloat() * 220f) * rigScale()
        spinIn = (700f + rnd.nextFloat() * 500f) * spinScale()
        lastRig = null
        shake = 0f; flash = 0f
    }

    private fun rigScale() = if (Gadget.RADAR in cfg.gadgets) 0.6f else 1f
    private fun spinScale() = if (Gadget.LUCKY in cfg.gadgets) 0.55f else 1f

    /** Brings a downed runner back: the screen is cleared and the run carries on. */
    fun revive() {
        if (state != State.DOWN) return
        revives++
        clearHazards()
        state = State.PLAY
        invuln = 2.6f
        speed = resumeSpeed * 0.6f
        ry = 0f; vy = 6f; grounded = false; tumble = 0f; gsign = -1f
        spawnIn = speed * 1.6f
        missileT = 5f
        ring(rx, ry, flair.trail, 0.6f, 14f)
        burst(rx, ry, WHITE, 26, 10f, 0.6f)
        flash = 0.3f; flashColor = flair.trail
        sfx.play(Snd.POWER, minGapMs = 0)
        sfx.buzz(Haptics.Kind.SUCCESS)
    }

    /** Ends the run (from the revive prompt, or a quit mid-run) and reports the result. */
    fun finish(): RunResult {
        val r = result()
        if (state != State.OVER) {
            state = State.OVER
            listener?.event(WorldEvent.Over(r))
        }
        return r
    }

    fun result() = RunResult(
        distance = distance.toInt(), chips = chips, credits = credits, nearMisses = nearMisses, rigs = rigsRidden,
        zappers = zapped, missiles = dodged, lasers = outlasted, drones = stomped, flips = flips, blinks = blinks,
        bestAir = bestAir.toInt(), bestDry = bestDry.toInt(), spins = spins, revives = revives, seconds = seconds, cause = cause,
    )

    private fun clearHazards() {
        zappers.forEach { if (it.active) { it.active = false; puffOut(it.x, it.y) } }
        missiles.forEach { if (it.active) { it.active = false; if (it.shown) puffOut(it.x, it.y) } }
        lasers.forEach { it.active = false }
        drones.forEach { if (it.active) { it.active = false; puffOut(it.x, it.y) } }
        laserLock = false
    }

    private fun puffOut(x: Float, y: Float) {
        if (x > -W - 2f && x < W + 2f) burst(x, y, ZAP_COLOR, 6, 5f, 0.4f)
    }

    // ---------------------------------------------------------------- input

    /** 0 down, 1 move, 2 up, 3 cancel. Only presses matter: anywhere on screen, any finger. */
    fun touch(action: Int, @Suppress("UNUSED_PARAMETER") id: Int, @Suppress("UNUSED_PARAMETER") x: Float, @Suppress("UNUSED_PARAMETER") y: Float) {
        when (action) {
            0 -> { if (holds == 0) { holdT = 0f; tapped = true }; holds++ }
            2 -> { holds = max(0, holds - 1); if (holds == 0) release() }
            3 -> { holds = 0; release() }
        }
    }

    /** For tests and the attract loop: hold or let go. */
    fun hold(on: Boolean) {
        if (on && holds == 0) touch(0, 0, 0f, 0f) else if (!on && holds > 0) touch(3, 0, 0f, 0f)
    }

    private val holding get() = holds > 0

    private fun release() {
        // A short press on the blink drive is a blink.
        if (rig == Rig.BLINK && holdT < 0.2f && state == State.PLAY) blink()
    }

    // ---------------------------------------------------------------- the step

    fun step(dt: Float) {
        t += dt
        if (shake > 0f) shake = max(0f, shake - dt * 2.2f)
        if (flash > 0f) flash = max(0f, flash - dt * 2.5f)
        // Sub-steps keep the fast stuff from tunnelling through the runner.
        val n = max(1, (dt * speed.coerceAtLeast(20f) / 0.25f).toInt().coerceAtMost(8))
        val h = dt / n
        repeat(n) { tick(h) }
        stepParticles(dt)
        hudT -= dt
        if (hudT <= 0f && state != State.DEMO) {
            hudT = 0.06f
            listener?.hud(Hud(distance.toInt(), credits, spins, nearMisses, rig, shield, boostFrac, cfg.best))
        }
    }

    private fun tick(h: Float) {
        if (state == State.DEMO) autopilot()
        if (holding) holdT += h
        if (blinkCool > 0f) blinkCool -= h
        if (invuln > 0f) invuln -= h
        rigT += h

        when (state) {
            State.DEMO, State.PLAY -> {
                val target = if (boostLeft > 0f) BOOST_SPEED else speedAt(distance)
                speed += (target - speed) * min(1f, h * (if (boostLeft > 0f) 3f else 1.2f))
                if (state == State.PLAY) seconds += h
                move(h)
            }
            State.DYING -> {
                deadT += h
                speed = max(0f, speed - (5f + speed * 1.1f) * h)
                ragdoll(h)
                if (deadT > 1.1f && speed < 0.8f && grounded) {
                    speed = 0f
                    state = State.DOWN
                    listener?.event(WorldEvent.Down(revives))
                }
            }
            State.DOWN, State.OVER -> { speed = 0f; return }
        }

        val dx = speed * h
        distance += dx
        if (state == State.PLAY && boostLeft <= 0f) {
            if (grounded && gsign < 0f && rig != Rig.STRIDER) air = 0f else { air += dx; bestAir = max(bestAir, air) }
            dry += dx; bestDry = max(bestDry, dry)
        }
        if (boostLeft > 0f) {
            boostLeft -= dx
            if (boostLeft <= 0f) {
                boostLeft = 0f
                invuln = max(invuln, 1.6f)
                spawnIn = 12f
                sfx.play(Snd.WHOOSH, minGapMs = 0)
            }
        }

        if (state == State.PLAY || state == State.DEMO) spawn(dx)
        scroll(h, dx)
        if (state == State.PLAY && boostLeft <= 0f) collide()
        collect(h)
    }

    private fun autopilot() {
        val target = sin(t * 0.55f) * 4.2f + sin(t * 1.3f) * 1.5f
        hold(ry < target)
    }

    // ---------------------------------------------------------------- the runner moves

    private fun move(h: Float) {
        val wasGrounded = grounded
        val r = rig
        thrusting = false
        if (boostLeft > 0f) {
            // Riding the head start: glide to the middle of the corridor.
            vy = (0.5f - ry) * 3f
            ry += vy * h
            grounded = false
            exhaust(h, true)
            return
        }
        val tune = 1f + cfg.thrust * 0.05f
        val g = 46f * tune * when {
            Gadget.FEATHER in cfg.gadgets -> 0.74f
            Gadget.ANVIL in cfg.gadgets -> 1.32f
            else -> 1f
        }
        when (r) {
            null -> {
                if (holding) { vy += (96f * tune - g) * h; thrusting = true } else vy -= g * h
                vy = vy.coerceIn(if (Gadget.FEATHER in cfg.gadgets) -14f else -21f, 16f)
            }
            Rig.STRIDER -> {
                if (holding && grounded) { vy = 19f; sfx.play(Snd.WHOOSH, 0.6f, 0.7f); grounded = false }
                vy -= (if (holding && vy > 0f) 26f else 62f) * h
            }
            Rig.FLIP -> {
                if (tapped) {
                    gsign = -gsign
                    flips++
                    sfx.play(Snd.WHOOSH, 0.7f, if (gsign > 0f) 1.3f else 1f)
                    sfx.buzz(Haptics.Kind.TICK)
                }
                vy += gsign * 80f * h
                vy = vy.coerceIn(-26f, 26f)
            }
            Rig.GLIDER -> {
                if (holding) vy += 42f * h else vy -= 15f * h
                vy = vy.coerceIn(-4.6f, 9f)
            }
            Rig.BLINK -> {
                vy = if (holding && holdT >= 0.2f) 7f else -4.5f
            }
            Rig.EEL -> {
                vy += (if (holding) 58f else -58f) * h
                vy = vy.coerceIn(-13f, 13f)
            }
        }
        tapped = false
        ry += vy * h
        val lo = FLOOR + half()
        val hi = CEIL - half()
        grounded = false
        if (ry <= lo) {
            ry = lo
            if (vy < 0f) {
                if (!wasGrounded && vy < -12f && r != Rig.EEL) { landing(); }
                vy = if (r == Rig.EEL) -vy * 0.3f else 0f
            }
            grounded = gsign < 0f || r == null
        }
        if (ry >= hi) {
            ry = hi
            if (vy > 0f) vy = if (r == Rig.EEL) -vy * 0.3f else 0f
            grounded = r == Rig.FLIP && gsign > 0f
        }
        if (grounded && (r == null || r == Rig.STRIDER || r == Rig.FLIP)) stride += speed * h * (if (r == Rig.STRIDER) 0.22f else 0.42f)
        exhaust(h, thrusting)
    }

    /** Half the height of whatever's riding the corridor: the runner, or the runner on a rig. */
    fun half() = when (rig) {
        Rig.STRIDER -> 1.7f
        Rig.EEL -> 0.8f
        else -> RH
    }

    private fun landing() {
        repeat(6) {
            parts.add(rx + (rnd.nextFloat() - 0.5f) * 1.2f, FLOOR + 0.1f, -speed * 0.4f + (rnd.nextFloat() - 0.5f) * 6f, rnd.nextFloat() * 2.5f, 0.45f, 0.35f, 0xFF8A93A8.toInt(), Shape.SOFT, 2.5f, 0.6f)
        }
        if (rig == Rig.STRIDER) { shake = max(shake, 0.35f); sfx.play(Snd.ROCK, 0.6f, 0.7f) }
    }

    private fun blink() {
        if (blinkCool > 0f) return
        val from = ry
        val to = (if (abs(ry) > 1.4f) -ry else if (ry >= 0f) -5f else 5f).coerceIn(FLOOR + RH, CEIL - RH)
        ry = to; vy = 0f
        blinkCool = 0.22f
        blinks++
        ring(rx, from, ZAP_COLOR, 0.3f, 6f)
        ring(rx, to, WHITE, 0.3f, 8f)
        for (k in 0 until 10) {
            val f = k / 9f
            parts.add(rx, from + (to - from) * f, -speed * 0.3f, 0f, 0.25f, 0.3f, ZAP_COLOR, Shape.SOFT, 2f, -1f)
        }
        sfx.play(Snd.WHOOSH, 0.8f, 1.5f, minGapMs = 0)
        sfx.buzz(Haptics.Kind.TICK)
    }

    private fun exhaust(h: Float, on: Boolean) {
        trailT -= h
        if (trailT > 0f) return
        trailT = 1f / 60f
        val r = rig
        if (r != null && boostLeft <= 0f) {
            // Rigs leave their own wake.
            if (r == Rig.EEL || r == Rig.GLIDER) flair.puff(parts, rnd, rx - 1.2f, ry, -speed * 0.7f, 0f, 0f, 1f, 0.8f)
            if (r == Rig.BLINK && rnd.nextFloat() < 0.4f) parts.add(rx - 0.5f, ry + (rnd.nextFloat() - 0.5f) * 1.6f, -speed * 0.5f, 0f, 0.3f, 0.12f, ZAP_COLOR, Shape.SPARK, 1f)
            return
        }
        if (!on) {
            if (grounded && rnd.nextFloat() < 0.25f) parts.add(rx - 0.3f, FLOOR + 0.1f, -speed * 0.5f, rnd.nextFloat() * 1.5f, 0.35f, 0.25f, 0xFF6A7488.toInt(), Shape.SOFT, 2f, 0.6f)
            return
        }
        val len = if (boostLeft > 0f) 2f else 1f
        val nozzles = when (cfg.packStyle) { 1 -> floatArrayOf(-0.2f, 0.2f); 2, 4 -> floatArrayOf(0f); else -> floatArrayOf(0f) }
        val fc = flair.flameColor(t)
        for (o in nozzles) {
            val ex = rx - 0.42f + o
            val ey = ry - 0.45f
            val j = { (rnd.nextFloat() - 0.5f) * 2f }
            parts.add(ex, ey, -speed * 0.35f + j(), -16f * len + j() * 2f, 0.16f * len, 0.34f, fc, Shape.SOFT, 3f, -1.3f)
            if (rnd.nextFloat() < 0.6f) parts.add(ex, ey, -speed * 0.3f + j() * 3f, -22f * len + j() * 4f, 0.12f, 0.16f, WHITE, Shape.SPARK, 2f)
            flair.puff(parts, rnd, ex, ey - 0.2f, -speed * 0.85f, -5f * len, 1f, 0f, 1f + len * 0.3f)
        }
        if (cfg.packStyle == 3 && rnd.nextFloat() < 0.4f) parts.add(rx - 0.7f, ry + 0.2f, -speed * 0.6f, (rnd.nextFloat() - 0.5f) * 6f, 0.3f, 0.12f, fc, Shape.SPARK, 1.5f)
    }

    private fun ragdoll(h: Float) {
        vy -= 44f * h
        ry += vy * h
        grounded = false
        if (ry <= FLOOR + 0.5f) {
            ry = FLOOR + 0.5f
            if (vy < -3f) {
                vy = -vy * 0.42f
                landing()
                sfx.play(Snd.HURT, 0.4f, 0.7f, minGapMs = 120)
            } else {
                vy = 0f; grounded = true
            }
        }
        if (ry > CEIL - 0.5f) { ry = CEIL - 0.5f; vy = -abs(vy) * 0.4f }
        tumble += h * (if (grounded) speed * 0.5f else 9f)
        if (grounded && speed > 1f && rnd.nextFloat() < 0.3f) parts.add(rx, FLOOR + 0.1f, -speed * 0.3f, rnd.nextFloat() * 2f, 0.4f, 0.3f, 0xFF8A93A8.toInt(), Shape.SOFT, 2f, 0.8f)
    }

    // ---------------------------------------------------------------- spawning

    private fun difficulty() = (distance / 6000f).coerceIn(0f, 1f)
    private val x0 get() = W + SPAWN_PAD

    private fun spawn(dx: Float) {
        if (boostLeft > 0f) return
        if (rig == null && picks.kindCount(PKind.RIG) == 0) rigIn -= dx
        spinIn -= dx
        if (state == State.PLAY && distance > 260f && !laserLock) {
            missileT -= dx / max(speed, 1f)
            if (missileT <= 0f) {
                launchMissiles()
                val d = difficulty()
                missileT = (8.5f - d * 4.5f) * (0.7f + rnd.nextFloat() * 0.6f)
            }
        }
        if (laserLock) {
            if (lasers.none { it.active }) { laserLock = false; spawnIn = 8f }
            return
        }
        spawnIn -= dx
        if (spawnIn > 0f) return
        val d = difficulty()
        val roll = rnd.nextFloat()
        val w = when {
            state == State.DEMO -> chipPattern()
            rigIn <= 0f && rig == null && cfg.rigs.isNotEmpty() -> { token(PKind.RIG); rigIn = (650f + rnd.nextFloat() * 400f) * rigScale(); 2f }
            spinIn <= 0f && spins + picks.kindCount(PKind.SPIN) < MAX_SPINS -> { token(PKind.SPIN); spinIn = (900f + rnd.nextFloat() * 700f) * spinScale(); 2f }
            distance < 140f -> chipPattern()
            distance > 700f && roll < 0.07f + d * 0.07f -> { laserPattern(); 0f }
            distance > 320f && roll < 0.22f + d * 0.08f -> dronePattern()
            roll < 0.66f -> zapperPattern(d)
            else -> chipPattern()
        }
        // The breathing room shrinks as the run speeds up.
        spawnIn = w + speed * (1.15f - d * 0.5f) * (0.7f + rnd.nextFloat() * 0.6f)
        // A little trail of chips through some of the gaps.
        if (state == State.PLAY && rnd.nextFloat() < 0.35f && w > 0f) {
            val y = FLOOR + 1.5f + rnd.nextFloat() * (CEIL - FLOOR - 3f)
            val n = (spawnIn / 0.8f * 0.5f).toInt().coerceIn(3, 12)
            for (k in 0 until n) chip(x0 + w + 1.5f + k * 0.8f, y)
        }
    }

    private fun Pickups.kindCount(k: Int): Int {
        var c = 0
        for (i in 0 until n) if (kind[i] == k) c++
        return c
    }

    private fun chip(x: Float, y: Float, value: Int = 1) = picks.add(x, y, 0f, 0f, PKind.CHIP, value)

    private fun token(kind: Int) {
        picks.add(x0, FLOOR + 2f + rnd.nextFloat() * (CEIL - FLOOR - 4f), 0f, 0f, kind, 1)
    }

    private fun chipPattern(): Float {
        val s = 0.8f
        val lo = FLOOR + 0.9f
        val hi = CEIL - 0.9f
        fun yIn(span: Float) = lo + span / 2f + rnd.nextFloat() * max(0f, hi - lo - span)
        return when (rnd.nextInt(7)) {
            0 -> {
                val n = 10 + rnd.nextInt(14); val y = yIn(0f)
                for (k in 0 until n) chip(x0 + k * s, y)
                n * s
            }
            1 -> {
                val n = 20 + rnd.nextInt(12); val amp = 1.5f + rnd.nextFloat() * 2f; val y = yIn(amp * 2f)
                for (k in 0 until n) chip(x0 + k * s, y + sin(k * 0.45f) * amp)
                n * s
            }
            2 -> {
                val rows = 3 + rnd.nextInt(3); val cols = 7 + rnd.nextInt(8); val y = yIn(rows * s)
                for (r in 0 until rows) for (c in 0 until cols) chip(x0 + c * s, y - rows * s / 2f + r * s)
                cols * s
            }
            3 -> {
                val n = 14 + rnd.nextInt(6); val slope = if (rnd.nextBoolean()) 0.55f else -0.55f
                val y = yIn(n * s * abs(slope)) - slope * n * s / 2f
                for (k in 0 until n) chip(x0 + k * s, y + slope * k * s)
                n * s
            }
            else -> {
                val shape = SHAPES[rnd.nextInt(SHAPES.size)]
                val y = yIn(shape.size * s) + shape.size * s / 2f
                var wide = 0
                for ((r, row) in shape.withIndex()) {
                    wide = max(wide, row.length)
                    for ((c, ch) in row.withIndex()) when (ch) {
                        'X' -> chip(x0 + c * s, y - r * s)
                        'O' -> chip(x0 + c * s, y - r * s, 5)
                    }
                }
                wide * s
            }
        }
    }

    private fun zapper(x: Float, y: Float, len: Float, ang: Float, spin: Float = 0f, vy: Float = 0f) {
        val z = zappers.firstOrNull { !it.active } ?: return
        z.active = true; z.x = x; z.y = y; z.len = len; z.ang = ang; z.spin = spin; z.vy = vy
        z.passed = false; z.grazed = false; z.seed = rnd.nextInt(1000)
    }

    private fun zapperPattern(d: Float): Float {
        val lo = FLOOR + 0.5f
        val hi = CEIL - 0.5f
        val vert = (PI / 2).toFloat()
        fun yFor(ext: Float) = lo + ext + rnd.nextFloat() * max(0f, hi - lo - ext * 2f)
        fun gap(x: Float) {
            val gap = 4.8f - d * 0.9f
            val gc = lo + gap / 2f + 1f + rnd.nextFloat() * (hi - lo - gap - 2f)
            val bot = gc - gap / 2f - lo
            val top = hi - (gc + gap / 2f)
            if (bot > 1f) zapper(x, lo + bot / 2f, bot, vert)
            if (top > 1f) zapper(x, hi - top / 2f, top, vert)
        }
        return when (rnd.nextInt(if (d < 0.12f) 3 else 7)) {
            0 -> {
                val len = 3f + rnd.nextFloat() * 2.5f + d * 1.5f
                val ang = listOf(0f, vert, vert / 2f, -vert / 2f)[rnd.nextInt(4)]
                zapper(x0 + len / 2f, yFor(abs(sin(ang)) * len / 2f), len, ang)
                len
            }
            1 -> { gap(x0); 1f }
            2 -> {
                val dir = if (rnd.nextBoolean()) 1f else -1f
                val start = if (dir > 0f) lo + 2f else hi - 2f
                val ang = if (rnd.nextBoolean()) 0f else dir * vert / 2f
                for (k in 0 until 3) zapper(x0 + k * 4.5f, start + dir * k * 3.4f, 3f, ang)
                13.5f
            }
            3 -> {
                val len = 4.5f + d * 2.2f
                zapper(x0 + len / 2f, yFor(len / 2f), len, rnd.nextFloat() * PI.toFloat(), (if (rnd.nextBoolean()) 1f else -1f) * (1.1f + d * 1.3f))
                len
            }
            4 -> {
                val len = 3.6f + d
                zapper(x0, yFor(len / 2f), len, vert, vy = (if (rnd.nextBoolean()) 1f else -1f) * (3.5f + d * 4f))
                1f
            }
            5 -> { gap(x0); gap(x0 + 7f + (1f - d) * 3f); 11f }
            else -> {
                // A wall with a hole, then a spinner.
                gap(x0)
                val len = 4f + d * 2f
                zapper(x0 + 9f, yFor(len / 2f), len, 0f, 1.6f + d)
                9f + len / 2f
            }
        }
    }

    private fun laserPattern() {
        laserLock = true
        val waves = LASER_WAVES[rnd.nextInt(LASER_WAVES.size)]
        val lane = (CEIL - FLOOR - 2.4f) / 4f
        for ((w, set) in waves.withIndex()) for (l in set) {
            val z = lasers.firstOrNull { !it.active } ?: break
            z.active = true; z.y = FLOOR + 1.2f + l * lane; z.t = -w * 1.7f; z.warn = 1.6f; z.fire = 1.1f; z.grazed = false
        }
        sfx.play(Snd.WARNING, 0.8f, minGapMs = 0)
    }

    private fun dronePattern(): Float {
        val n = 1 + rnd.nextInt(3)
        for (k in 0 until n) {
            val d = drones.firstOrNull { !it.active } ?: break
            d.active = true; d.x = x0 + k * 4f
            val ground = rnd.nextFloat() < 0.4f
            d.base = if (ground) FLOOR + DRONE_R + 0.2f else FLOOR + 2.5f + rnd.nextFloat() * (CEIL - FLOOR - 5f)
            d.amp = if (ground) 0f else 0.8f + rnd.nextFloat() * 2f
            d.ph = rnd.nextFloat() * 6.283f
            d.y = d.base; d.grazed = false; d.passed = false; d.seed = rnd.nextInt(1000)
        }
        return n * 4f
    }

    private fun launchMissiles() {
        val d = difficulty()
        val n = (1 + (rnd.nextFloat() * (1f + d * 2.2f)).toInt()).coerceAtMost(3)
        for (k in 0 until n) {
            val m = missiles.firstOrNull { !it.active } ?: break
            m.active = true; m.phase = 0; m.t = -k * 0.5f; m.x = W - 0.9f; m.y = ry; m.passed = false
            m.off = if (k == 0) 0f else (if (k % 2 == 1) 2.8f else -2.8f)
        }
    }

    // ---------------------------------------------------------------- the world scrolls

    private fun scroll(h: Float, dx: Float) {
        val gone = -W - 8f
        for (z in zappers) if (z.active) {
            z.x -= dx
            z.ang += z.spin * h
            if (z.vy != 0f) {
                z.y += z.vy * h
                val ext = abs(z.dy)
                if (z.y - ext < FLOOR + 0.5f) { z.y = FLOOR + 0.5f + ext; z.vy = abs(z.vy) }
                if (z.y + ext > CEIL - 0.5f) { z.y = CEIL - 0.5f - ext; z.vy = -abs(z.vy) }
            }
            if (!z.passed && z.x + abs(z.dx) + ZAP_R < rx - 0.6f) {
                z.passed = true
                if (state == State.PLAY) { zapped++; if (z.grazed) nearMiss(rx, ry) }
            }
            if (z.x + abs(z.dx) < gone) z.active = false
        }
        for (m in missiles) if (m.active) {
            m.t += h
            if (m.t < 0f) continue
            when (m.phase) {
                0 -> {
                    if (m.t - h < 0f) sfx.play(Snd.WARNING, 0.55f, 1.3f, minGapMs = 60)
                    m.x = W - 0.9f
                    if (state == State.PLAY) m.y += ((ry + m.off).coerceIn(FLOOR + 0.6f, CEIL - 0.6f) - m.y) * min(1f, h * 5f)
                    if (m.t > 1.35f) m.phase = 1
                }
                1 -> if (m.t > 1.9f) {
                    m.phase = 2
                    m.x = W + 2f
                    sfx.play(Snd.MISSILE, 0.8f)
                }
                else -> {
                    m.x -= (speed + 21f) * h
                    if (rnd.nextFloat() < 0.6f) parts.add(m.x + 0.6f, m.y + (rnd.nextFloat() - 0.5f) * 0.2f, 4f, (rnd.nextFloat() - 0.5f) * 1.5f, 0.35f, 0.32f, 0xFFFFA040.toInt(), Shape.SOFT, 2f, 0.9f)
                    if (!m.passed && m.x < rx - 0.8f) {
                        m.passed = true
                        if (state == State.PLAY) { dodged++; if (abs(m.y - ry) < half() + MISSILE_R + NEAR + 0.4f) nearMiss(rx, ry) }
                    }
                    if (m.x < gone) m.active = false
                }
            }
            // Missiles still on their warning when the run ends just fizzle.
            if (state != State.PLAY && m.phase < 2) m.active = false
        }
        for (l in lasers) if (l.active) {
            val was = l.firing
            l.t += h
            if (l.firing && !was) { sfx.play(Snd.LASER, 0.8f); shake = max(shake, 0.25f) }
            if (l.t >= l.warn + l.fire + 0.3f) {
                l.active = false
                if (state == State.PLAY) { outlasted++; if (l.grazed) nearMiss(rx, ry) }
            }
        }
        for (d in drones) if (d.active) {
            d.x -= dx + 1.6f * h
            d.y = d.base + sin(t * 2.2f + d.ph) * d.amp
            if (!d.passed && d.x < rx - 1f) { d.passed = true; if (d.grazed && state == State.PLAY) nearMiss(rx, ry) }
            if (d.x < gone) d.active = false
        }
    }

    // ---------------------------------------------------------------- collisions

    private val hx = FloatArray(3)
    private val hy = FloatArray(3)
    private var hr = 0.42f

    /** The runner's hit shape: three circles along the body (along x for the eel). */
    private fun body() {
        val r = rig
        val (rad, off, flat) = when (r) {
            Rig.STRIDER -> Triple(0.75f, 0.85f, false)
            Rig.EEL -> Triple(0.55f, 0.85f, true)
            Rig.GLIDER -> Triple(0.5f, 0.45f, false)
            else -> Triple(0.42f, 0.45f, false)
        }
        hr = rad
        for (k in 0..2) {
            val o = (k - 1) * off
            hx[k] = rx + if (flat) o else 0f
            hy[k] = ry + if (flat) 0f else o
        }
    }

    /** How far the runner's body is from a circle at ([x], [y]) of radius [r] (negative: touching). */
    private fun gapTo(x: Float, y: Float, r: Float): Float {
        var best = Float.MAX_VALUE
        for (k in 0..2) best = min(best, hypot(hx[k] - x, hy[k] - y))
        return best - hr - r
    }

    private fun gapToSeg(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Float {
        var best = Float.MAX_VALUE
        val ex = bx - ax; val ey = by - ay
        val l2 = ex * ex + ey * ey
        for (k in 0..2) {
            val f = if (l2 > 0f) (((hx[k] - ax) * ex + (hy[k] - ay) * ey) / l2).coerceIn(0f, 1f) else 0f
            val px = ax + ex * f - hx[k]; val py = ay + ey * f - hy[k]
            best = min(best, sqrt(px * px + py * py))
        }
        return best - hr - r
    }

    private fun collide() {
        body()
        for (z in zappers) if (z.active && abs(z.x - rx) < z.len / 2f + 2.5f) {
            val g = gapToSeg(z.x - z.dx, z.y - z.dy, z.x + z.dx, z.y + z.dy, ZAP_R)
            if (g < 0f) { hit("zapper", ZAP_COLOR); if (state != State.PLAY) return; z.grazed = false; zapArc(z) }
            else if (g < NEAR) z.grazed = true
        }
        for (m in missiles) if (m.active && m.phase == 2 && abs(m.x - rx) < 3f) {
            val g = gapTo(m.x, m.y, MISSILE_R)
            if (g < 0f) {
                m.active = false
                explode(m.x, m.y, 1.1f, 0xFFFFA040.toInt())
                hit("missile", 0xFFFFA040.toInt())
                if (state != State.PLAY) return
            }
        }
        for (l in lasers) if (l.active && l.firing) {
            var g = Float.MAX_VALUE
            for (k in 0..2) g = min(g, abs(hy[k] - l.y))
            g -= hr + LASER_R
            if (g < 0f) { hit("laser", DANGER); if (state != State.PLAY) return }
            else if (g < NEAR) l.grazed = true
        }
        for (d in drones) if (d.active && abs(d.x - rx) < 3f) {
            val g = gapTo(d.x, d.y, DRONE_R)
            if (g < 0f) {
                if (rig == Rig.STRIDER) stomp(d)
                else {
                    d.active = false
                    explode(d.x, d.y, 0.9f, DANGER)
                    hit("drone", DANGER)
                    if (state != State.PLAY) return
                }
            } else if (g < NEAR) d.grazed = true
        }
    }

    private fun zapArc(z: Zapper) {
        for (k in 0 until 8) {
            val a = rnd.nextFloat() * 6.283f
            parts.add(rx, ry, cos(a) * 8f, sin(a) * 8f, 0.3f, 0.2f, ZAP_COLOR, Shape.SPARK, 2f)
        }
        z.passed = z.passed
    }

    private fun stomp(d: Drone) {
        d.active = false
        stomped++
        explode(d.x, d.y, 0.9f, flair.burst)
        flair.kill(parts, rnd, d.x, d.y, 0.8f)
        if (vy < 0f) vy = 12f
        shake = max(shake, 0.5f)
        sfx.play(Snd.BOOM)
        sfx.buzz(Haptics.Kind.HIT)
    }

    /** Something touched the runner: a rig or a bubble takes it, or the run is over. */
    private fun hit(what: String, color: Int) {
        if (invuln > 0f) return
        val r = rig
        when {
            r != null -> {
                rig = null
                lastRig = r
                explode(rx, ry, 1.6f, flair.burst)
                flair.kill(parts, rnd, rx, ry, 1.4f)
                vy = 14f; gsign = -1f
                ry = max(ry, FLOOR + RH)
                invuln = 1.1f + cfg.rigArmour * 0.45f
                shake = max(shake, 1f)
                flash = 0.3f; flashColor = color
                sfx.play(Snd.BIG_BOOM, 0.8f, minGapMs = 0)
                sfx.buzz(Haptics.Kind.BOOM)
                listener?.event(WorldEvent.Callout("${r.title} wrecked", r.icon))
            }
            shield -> {
                shield = false
                invuln = 1.2f
                ring(rx, ry, flair.trail, 0.5f, 12f)
                burst(rx, ry, flair.trail, 24, 10f, 0.5f)
                shake = max(shake, 0.6f)
                sfx.play(Snd.SHIELD, minGapMs = 0)
                sfx.buzz(Haptics.Kind.HIT)
            }
            else -> die(what, color)
        }
    }

    private fun die(what: String, color: Int) {
        cause = what
        state = State.DYING
        deadT = 0f
        resumeSpeed = max(speed, START_SPEED)
        thrusting = false
        holds = 0
        vy = 11f
        explode(rx, ry, 1.4f, color)
        burst(rx, ry, WHITE, 20, 12f, 0.5f)
        shake = 1.5f
        flash = 0.45f; flashColor = color
        sfx.play(if (what == "zapper") Snd.ZAP else Snd.HURT, minGapMs = 0)
        sfx.play(Snd.BIG_BOOM, 0.7f, minGapMs = 0)
        sfx.buzz(Haptics.Kind.BIG_BOOM)
    }

    private fun nearMiss(x: Float, y: Float) {
        nearMisses++
        if (Gadget.DAREDEVIL in cfg.gadgets) bonusCredits += 5
        parts.add(x, y, 0f, 0f, 0.3f, 0.4f, WHITE, Shape.WAVE, 0f, 5f)
        sfx.play(Snd.GRAZE, 0.6f, minGapMs = 80)
        sfx.buzz(Haptics.Kind.TICK, 0.4f)
    }

    // ---------------------------------------------------------------- pickups

    private fun collect(h: Float) {
        if (state == State.DOWN || state == State.OVER) return
        body()
        val canTake = state == State.PLAY || state == State.DEMO
        val reach = when {
            rig == Rig.EEL -> 6f + cfg.magnet * 0.8f
            Gadget.MAGNET in cfg.gadgets -> 4.5f + cfg.magnet * 0.7f
            else -> 0f
        }
        for (i in picks.n - 1 downTo 0) {
            picks.age[i] += h
            if (picks.pulled[i] && canTake) {
                val dx = rx - picks.x[i]; val dy = ry - picks.y[i]
                val d = max(hypot(dx, dy), 1e-3f)
                val v = 30f + speed
                picks.x[i] += dx / d * v * h
                picks.y[i] += dy / d * v * h
            } else {
                picks.x[i] -= speed * h
            }
            if (picks.x[i] < -W - 4f) { picks.remove(i); continue }
            if (!canTake || boostLeft > 0f) continue
            val k = picks.kind[i]
            val r = if (k == PKind.CHIP) (if (picks.value[i] > 1) 0.55f else 0.4f) else 0.9f
            val g = gapTo(picks.x[i], picks.y[i], r)
            if (g < 0f) {
                take(i)
                picks.remove(i)
            } else if (k == PKind.CHIP && reach > 0f && !picks.pulled[i] && g < reach) {
                picks.pulled[i] = true
            }
        }
    }

    private fun take(i: Int) {
        val x = picks.x[i]; val y = picks.y[i]
        when (picks.kind[i]) {
            PKind.CHIP -> {
                val v = picks.value[i]
                chips++
                if (state == State.PLAY) { chipCredits += v * cfg.chipValue; dry = 0f }
                parts.add(x, y, -speed * 0.5f, 0f, 0.22f, if (v > 1) 0.7f else 0.45f, CHIP_COLOR, Shape.SOFT, 2f, -1.5f)
                sfx.play(Snd.COIN, if (v > 1) 0.6f else 0.32f, 1f + (chips % 8) * 0.03f, minGapMs = 35)
            }
            PKind.RIG -> {
                val pool = cfg.rigs.filter { it != lastRig }.ifEmpty { cfg.rigs }
                val r = pool[rnd.nextInt(pool.size)]
                if (state == State.PLAY) rigsRidden++
                mount(r)
            }
            PKind.SPIN -> {
                if (state == State.PLAY) spins++
                ring(x, y, CHIP_COLOR, 0.5f, 10f)
                burst(x, y, CHIP_COLOR, 18, 8f, 0.5f)
                sfx.play(Snd.PICKUP, minGapMs = 0)
                sfx.buzz(Haptics.Kind.CLICK)
                listener?.event(WorldEvent.Callout("Spin token", "spin"))
            }
        }
    }

    private fun mount(r: Rig) {
        rig = r
        rigT = 0f
        lastRig = r
        gsign = -1f
        vy = 0f
        ry = ry.coerceIn(FLOOR + half(), CEIL - half())
        invuln = max(invuln, 0.6f)
        ring(rx, ry, flair.trail, 0.5f, 14f)
        burst(rx, ry, WHITE, 24, 10f, 0.5f)
        flash = 0.25f; flashColor = flair.trail
        sfx.play(Snd.POWER, minGapMs = 0)
        sfx.buzz(Haptics.Kind.SUCCESS)
        listener?.event(WorldEvent.Callout(r.title, r.icon))
    }

    // ---------------------------------------------------------------- effects

    private fun stepParticles(dt: Float) {
        for (i in parts.n - 1 downTo 0) {
            parts.life[i] -= dt
            if (parts.life[i] <= 0f) { parts.remove(i); continue }
            val k = exp(-parts.drag[i] * dt)
            parts.vx[i] *= k; parts.vy[i] *= k
            parts.x[i] += parts.vx[i] * dt
            parts.y[i] += parts.vy[i] * dt
            parts.size[i] = max(0.01f, parts.size[i] + parts.grow[i] * dt)
        }
    }

    fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float, life: Float) {
        for (k in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val s = speed * (0.3f + rnd.nextFloat())
            parts.add(x, y, cos(a) * s - this.speed * 0.3f, sin(a) * s, life * (0.5f + rnd.nextFloat()), 0.18f + rnd.nextFloat() * 0.15f, color, Shape.SPARK, 3f)
        }
    }

    fun ring(x: Float, y: Float, color: Int, life: Float, grow: Float) {
        parts.add(x, y, 0f, 0f, life, 0.3f, color, Shape.WAVE, 0f, grow)
    }

    /** An explosion of radius about [size]: a flash, a shock ring, fire and sparks. */
    fun explode(x: Float, y: Float, size: Float, color: Int) {
        parts.add(x, y, 0f, 0f, 0.18f, size * 2.2f, WHITE, Shape.SOFT, 0f, size * 4f)
        ring(x, y, color, 0.35f + size * 0.1f, size * 9f)
        val fire = (6 + size * 7).toInt()
        for (k in 0 until fire) {
            val a = rnd.nextFloat() * 6.283f
            val s = (1f + rnd.nextFloat() * 3f) * size
            parts.add(x, y, cos(a) * s - speed * 0.4f, sin(a) * s, 0.35f + rnd.nextFloat() * 0.4f, size * (0.5f + rnd.nextFloat() * 0.6f), if (k % 3 == 0) 0xFFFFE0A0.toInt() else color, Shape.SOFT, 2.5f, -size * 0.6f)
        }
        burst(x, y, 0xFFFFF0D0.toInt(), (8 + size * 10).toInt(), 10f * (0.6f + size * 0.4f), 0.5f)
    }
}
