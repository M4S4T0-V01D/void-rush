package dev.m4s4t0.voidrush

import dev.m4s4t0.voidrush.game.NoSfx
import dev.m4s4t0.voidrush.game.RunConfig
import dev.m4s4t0.voidrush.game.World
import dev.m4s4t0.voidrush.game.WorldEvent
import dev.m4s4t0.voidrush.game.WorldListener
import dev.m4s4t0.voidrush.game.Hud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTest {
    private class Log : WorldListener {
        val events = mutableListOf<WorldEvent>()
        override fun hud(h: Hud) {}
        override fun event(e: WorldEvent) { events += e }
    }

    /** Steps [w] at 60 fps until [done] or [seconds] run out; returns the seconds it took. */
    private fun run(w: World, seconds: Float, input: (Float) -> Unit = {}, done: () -> Boolean): Float {
        var t = 0f
        while (t < seconds && !done()) { input(t); w.step(1 / 60f); t += 1 / 60f }
        return t
    }

    @Test fun idleRunnerGetsCaught() {
        for (seed in 1..6) {
            val w = World(NoSfx).also { it.listener = Log() }
            w.start(RunConfig(seed = seed))
            run(w, 600f) { w.state == World.State.DOWN || w.state == World.State.OVER }
            assertTrue("seed $seed never died", w.state == World.State.DOWN)
            assertTrue(w.result().distance > 20)
        }
    }

    @Test fun demoNeverDies() {
        val w = World(NoSfx)
        w.demo(RunConfig(seed = 3))
        run(w, 300f) { !w.alive }
        assertEquals(World.State.DEMO, w.state)
    }

    @Test fun reviveCarriesOn() {
        val log = Log()
        val w = World(NoSfx).also { it.listener = log }
        w.start(RunConfig(seed = 9))
        run(w, 600f) { w.state == World.State.DOWN }
        val before = w.result().distance
        assertNotNull(log.events.firstOrNull { it is WorldEvent.Down })
        w.revive()
        assertEquals(World.State.PLAY, w.state)
        run(w, 1.5f) { false }
        assertEquals(World.State.PLAY, w.state)
        assertTrue(w.result().distance > before)
        assertEquals(1, w.result().revives)
        w.finish()
        assertEquals(World.State.OVER, w.state)
        assertEquals(1, log.events.count { it is WorldEvent.Over })
        w.finish()
        assertEquals(1, log.events.count { it is WorldEvent.Over })
    }

    @Test fun chipValueScalesCredits() {
        fun play(value: Float): World {
            val w = World(NoSfx)
            w.start(RunConfig(seed = 4, chipValue = value))
            // Bob up and down through the middle of the corridor; same seed and input, so the same run.
            run(w, 600f, { w.hold(w.ry < 0f) }) { w.state != World.State.PLAY }
            return w
        }
        val a = play(1f).result()
        val b = play(1.5f).result()
        assertEquals(a.distance, b.distance)
        assertEquals(a.chips, b.chips)
        assertTrue("collected ${a.chips} chips", a.chips > 0)
        assertTrue("${b.credits} vs ${a.credits}", b.credits > a.credits)
        assertEquals(b.credits.toFloat(), a.credits * 1.5f, a.credits * 0.05f + 1f)
    }
}
