package dev.m4s4t0.voidrush

import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.Levels
import dev.m4s4t0.voidrush.model.Pack
import dev.m4s4t0.voidrush.model.RunResult
import dev.m4s4t0.voidrush.model.Spin
import dev.m4s4t0.voidrush.model.Upgrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelTest {
    @Before fun fresh() = Game.initForTest()

    @Test fun reviveCostsDouble() {
        assertEquals(listOf(1, 2, 4, 8), (0..3).map(Game::reviveCost))
        Game.setForTest(cores = 3)
        assertTrue(Game.revive(0)); assertTrue(Game.revive(1))
        assertEquals(0, Game.cores)
        assertFalse(Game.canRevive(2))
    }

    @Test fun buyingNeedsLevelAndMoney() {
        Game.setForTest(credits = 100_000)
        assertFalse("level 1 can't buy a level 3 pack", Game.buy(Pack.TWIN.key, Pack.TWIN.price))
        Game.setForTest(xp = 100_000)
        assertTrue(Game.buy(Pack.TWIN.key, Pack.TWIN.price))
        assertTrue(Game.owns(Pack.TWIN))
        assertEquals(100_000 - Pack.TWIN.price.amount, Game.credits)
    }

    @Test fun upgradesMaxOut() {
        Game.setForTest(credits = 10_000_000, xp = 1_000_000)
        val u = Upgrade.MAGNET
        repeat(u.max) { assertTrue(Game.upgrade(u)) }
        assertFalse(Game.upgrade(u))
        assertNull(u.cost(u.max))
    }

    @Test fun finishingPaysAndRecordsBest() {
        val c = Game.credits
        val o = Game.finishRun(RunResult(distance = 1200, chips = 80, credits = 80, spins = 2))
        assertTrue(o.newBest)
        assertEquals(1200L, Game.bestDistance)
        assertTrue(Game.credits >= c + 80)
        assertEquals(2, o.spins)
        assertFalse(Game.finishRun(RunResult(distance = 300, chips = 0, credits = 0)).newBest)
    }

    @Test fun wheelAndLevels() {
        repeat(200) { assertTrue(Game.spin() in Spin.WHEEL.indices) }
        assertEquals(1, Levels.of(0).first)
        assertTrue(Levels.of(1_000_000).first > 10)
    }
}
