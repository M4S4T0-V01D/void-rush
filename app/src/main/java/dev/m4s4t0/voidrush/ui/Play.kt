package dev.m4s4t0.voidrush.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Haptics
import dev.m4s4t0.voidrush.audio.Snd
import dev.m4s4t0.voidrush.model.Currency
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.Gadget
import dev.m4s4t0.voidrush.model.Levels
import dev.m4s4t0.voidrush.model.Outcome
import dev.m4s4t0.voidrush.model.Spin
import dev.m4s4t0.voidui.Void
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PlayScreen(state: AppState) {
    val run = state.run ?: return
    val o = run.outcome
    Box(Modifier.fillMaxSize()) {
        if (o == null) {
            Hud(state, run)
            if (run.paused) PauseMenu(state)
            if (run.down) RevivePrompt(state, run)
        } else Results(state, run, o)
    }
}

@Composable
private fun Hud(state: AppState, run: RunState) {
    val h = run.hud
    val left = Game.leftHanded
    Box(Modifier.fillMaxSize().safe().padding(10.dp)) {
        // Distance and wallet on one side, pause on the other (mirrored for left hands, so the thumb on the
        // thrust side never covers the numbers).
        Column(Modifier.align(if (left) Alignment.TopEnd else Alignment.TopStart), horizontalAlignment = if (left) Alignment.End else Alignment.Start) {
            Text(
                "${fmt(h?.distance ?: 0)} m", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Void.violet, blurRadius = 16f)),
            )
            val best = h?.best ?: 0L
            if (best > 0) {
                val past = (h?.distance ?: 0) > best
                Text(if (past) "New best!" else "Best ${fmt(best)} m", color = if (past) CREDIT else Void.dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Money((h?.credits ?: 0).toLong(), fontSize = 16.sp, icon = 18.dp)
                if ((h?.spins ?: 0) > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Glyph("spin", 18.dp, color = SHARD)
                    Text("×${h?.spins}", color = SHARD, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            val rig = h?.rig
            if (rig != null || h?.shield == true) Row(
                Modifier.padding(top = 6.dp).glass(RoundedCornerShape(50), alpha = 0.6f).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (rig != null) { Glyph(rig.icon, 16.dp, color = Void.cyan); Text(rig.title, color = Void.text, fontSize = 12.sp) }
                if (h.shield) { Glyph("shield", 16.dp, color = Void.cyan); if (rig == null) Text("Shield", color = Void.text, fontSize = 12.sp) }
            }
            if ((h?.boost ?: 0f) > 0f) Bar(h!!.boost, Modifier.padding(top = 6.dp).width(140.dp), 6.dp, Void.cyan)
        }
        RoundIcon("pause", Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd)) { state.pause(true) }

        // The first few metres: how to fly.
        if ((h?.distance ?: 0) < 40 && Game.bestDistance < 400) Text(
            "Hold anywhere to thrust · let go to fall", color = Void.text, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).glass(RoundedCornerShape(50), alpha = 0.6f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        CalloutBanner(run.callout, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
    }
}

@Composable
private fun CalloutBanner(c: Callout?, modifier: Modifier) {
    var shown by remember { mutableStateOf<Callout?>(null) }
    LaunchedEffect(c?.id) {
        if (c != null) { shown = c; delay(1600); if (shown?.id == c.id) shown = null }
    }
    AnimatedVisibility(shown != null, modifier, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut()) {
        val s = shown ?: c ?: return@AnimatedVisibility
        Row(
            Modifier.glass(RoundedCornerShape(50), Void.cyan.copy(alpha = 0.6f), 0.75f).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Glyph(s.icon, 22.dp, color = Void.cyan)
            Text(s.text, color = Void.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.72f)).squishy(sound = false) {}.safe(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PauseMenu(state: AppState) {
    Scrim {
        Column(
            Modifier.widthIn(max = 340.dp).glass(RoundedCornerShape(24.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Glyph("pause", 40.dp, color = Void.cyan)
            Text("Paused", color = Void.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            ActionButton("Keep running", Modifier.fillMaxWidth(), icon = "play") { state.pause(false) }
            ActionButton("End the run", Modifier.fillMaxWidth(), icon = "flag", primary = false) { state.giveUp() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RoundIcon("sound", on = Game.sounds) { Game.sounds = !Game.sounds; Game.saveSettings() }
                RoundIcon("music", on = Game.music) { Game.music = !Game.music; Game.saveSettings() }
            }
        }
    }
}

@Composable
private fun RevivePrompt(state: AppState, run: RunState) {
    val n = run.revives
    val free = Game.freeRevives > 0 || (n == 0 && Game.loadout.has(Gadget.SECOND))
    val cost = Game.reviveCost(n)
    val can = Game.canRevive(n)
    // Ten seconds to decide, then the run ends by itself.
    var left by remember { mutableIntStateOf(10) }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left--; if (left in 1..3) Audio.play(Snd.TAP, 0.5f) }
        state.giveUp()
    }
    Scrim {
        Column(
            Modifier.widthIn(max = 360.dp).glass(RoundedCornerShape(24.dp), Void.bad.copy(alpha = 0.6f)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph("heart", 44.dp, Modifier.bob(3.dp), color = Void.bad)
            Text("Caught!", color = Void.text, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Get back up and keep the thruster?", color = Void.dim, fontSize = 14.sp, textAlign = TextAlign.Center)
            Bar(left / 10f, Modifier.fillMaxWidth(), 6.dp, Void.bad)
            ActionButton(
                if (free) "Revive · free" else "Revive · $cost ${if (cost == 1) "core" else "cores"}",
                Modifier.fillMaxWidth(), icon = "heart", enabled = can, accent = Void.bad,
            ) { state.revive() }
            if (!free) Money(Game.cores.toLong(), Currency.CORES, fontSize = 13.sp, dimmed = !can)
            ActionButton("Give up", Modifier.fillMaxWidth(), primary = false) { state.giveUp() }
        }
    }
}

@Composable
private fun Results(state: AppState, run: RunState, o: Outcome) {
    val r = o.result
    Row(
        Modifier.fillMaxSize().background(Void.bg.copy(alpha = 0.78f)).safe().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (o.newBest) "New best!" else "Run over", color = if (o.newBest) CREDIT else Void.dim, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "${fmt(r.distance)} m", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Void.violet, blurRadius = 24f)),
            )
            Text("Best ${fmt(Game.bestDistance)} m · ${clock(r.seconds)}", color = Void.dim, fontSize = 13.sp)
            Column(Modifier.glass(RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatLine("credit", "Credit chips", fmt(r.chips))
                if (r.nearMisses > 0) StatLine("graze", "Near misses", fmt(r.nearMisses))
                if (r.rigs > 0) StatLine("mech", "Rigs ridden", fmt(r.rigs))
                if (r.missiles > 0) StatLine("missile", "Missiles dodged", fmt(r.missiles))
                if (r.revives > 0) StatLine("heart", "Revives", fmt(r.revives))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Earned", color = Void.dim, fontSize = 13.sp)
                    Money((r.credits + o.reward.credits).toLong(), fontSize = 16.sp)
                    if (o.reward.cores > 0) Money(o.reward.cores.toLong(), Currency.CORES, fontSize = 16.sp)
                    if (o.reward.shards > 0) Money(o.reward.shards.toLong(), Currency.SHARDS, fontSize = 16.sp)
                    Text("+${fmt(o.reward.xp)} xp", color = Void.violet, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                val (lvl, into, need) = Levels.of(Game.xp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (o.levelAfter > o.levelBefore) "Level up! $lvl" else "Level $lvl", color = Void.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Bar(into.toFloat() / need, Modifier.weight(1f), 6.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Run again", Modifier.weight(1f), icon = "retry", enabled = run.spinsLeft == 0) { state.start() }
                ActionButton("Home", Modifier.weight(1f), icon = "home", primary = false, enabled = run.spinsLeft == 0) { state.go(Screen.HOME) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (o.spins > 0) PrizeWheel(state, run)
            else MissionList(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatLine(icon: String, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Glyph(icon, 16.dp, color = Void.cyan, glow = false)
        Text(label, color = Void.dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** The end-of-run prize wheel: one turn per spin token picked up. */
@Composable
private fun PrizeWheel(state: AppState, run: RunState) {
    val n = Spin.WHEEL.size
    val seg = 360f / n
    val rot = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var landed by remember { mutableStateOf<Int?>(null) }
    val colors = listOf(Void.violet, Void.cyan)

    Text(
        if (run.spinsLeft > 0) "Spin the wheel · ${plural(run.spinsLeft, "spin")} left" else "Prizes",
        color = Void.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(200.dp).graphicsLayer { rotationZ = rot.value }) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until n) {
                    val c = colors[i % 2]
                    drawArc(c.copy(alpha = 0.35f), -90f + i * seg, seg, true)
                    drawArc(c, -90f + i * seg, seg, true, style = Stroke(1.5f))
                }
                drawCircle(Void.panel, size.minDimension * 0.14f)
                drawCircle(Void.cyan, size.minDimension * 0.14f, style = Stroke(3f))
            }
            for ((i, p) in Spin.WHEEL.withIndex()) {
                val a = Math.toRadians((-90f + (i + 0.5f) * seg).toDouble())
                Glyph(
                    p.first.icon, 22.dp,
                    Modifier.align(Alignment.Center).offset((cos(a) * 70).dp, (sin(a) * 70).dp),
                    color = when (p.first.icon) { "credit" -> CREDIT; "core" -> CORE; "shard" -> SHARD; else -> Color.White },
                )
            }
        }
        // The pointer at the top.
        Canvas(Modifier.size(210.dp)) {
            val cx = size.width / 2
            val p = Path().apply { moveTo(cx - 12f, 0f); lineTo(cx + 12f, 0f); lineTo(cx, 26f); close() }
            drawPath(p, Color.White)
        }
    }
    Spacer(Modifier.height(8.dp))
    val l = landed
    Text(
        if (l != null && !spinning) Spin.WHEEL[l].first.title else " ",
        color = CREDIT, fontSize = 18.sp, fontWeight = FontWeight.Black,
    )
    Spacer(Modifier.height(6.dp))
    ActionButton("Spin", Modifier.width(180.dp), icon = "spin", enabled = run.spinsLeft > 0 && !spinning, accent = SHARD) {
        val i = state.spin() ?: return@ActionButton
        spinning = true
        scope.launch {
            // Land with the prize's middle under the pointer, after a few whole turns.
            val target = -(i + 0.5f) * seg
            val now = rot.value
            var end = now - (now % 360f) + 360f * 5 + ((target % 360f) + 360f) % 360f
            if (end - now < 360f * 4) end += 360f
            var lastTick = (now / seg).toInt()
            rot.animateTo(end, tween(3200, easing = FastOutSlowInEasing)) {
                val t = (value / seg).toInt()
                if (t != lastTick) { lastTick = t; Audio.play(Snd.TAP, 0.35f, minGapMs = 30) }
            }
            landed = i
            spinning = false
            Audio.play(Snd.BELL)
            Haptics.buzz(Haptics.Kind.SUCCESS)
        }
    }
}

/** Today's missions and how far along each is. */
@Composable
fun MissionList(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in Game.missions) {
            val p = m.progress(Game.stats)
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), if (m.done) Void.cyan.copy(alpha = 0.6f) else Void.border).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Glyph(if (m.done) "check" else m.icon, 26.dp, color = if (m.done) Void.cyan else Void.violet)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.text, color = Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Bar(p.toFloat() / m.goal, Modifier.weight(1f), 6.dp)
                        Text("${fmt(p)}/${fmt(m.goal)}", color = Void.dim, fontSize = 11.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Money(m.credits.toLong(), fontSize = 12.sp, icon = 14.dp, dimmed = m.done)
                    if (m.cores > 0) Money(m.cores.toLong(), Currency.CORES, fontSize = 12.sp, icon = 14.dp, dimmed = m.done)
                }
            }
        }
    }
}
