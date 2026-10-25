package dev.m4s4t0.voidrush.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Snd
import dev.m4s4t0.voidrush.model.Boost
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidui.Void

@Composable
fun HomeScreen(state: AppState) {
    Box(Modifier.fillMaxSize().safe()) {
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LevelChip()
            WalletChip()
        }
        RoundIcon("settings", Modifier.align(Alignment.TopEnd).padding(12.dp)) { state.go(Screen.SETTINGS) }

        // The title, over the demo run.
        Column(Modifier.align(Alignment.CenterStart).padding(start = 28.dp, bottom = 20.dp)) {
            Text(
                "VOID", color = Void.cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp,
                style = TextStyle(shadow = Shadow(Void.cyan, blurRadius = 18f)),
            )
            Text(
                "RUSH", fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                style = TextStyle(brush = Brush.linearGradient(listOf(Void.violet, Void.cyan)), shadow = Shadow(Void.violet, blurRadius = 30f)),
                modifier = Modifier.bob(4.dp, periodMs = 3200),
            )
            Text("You stole the thruster. Now run.", color = Void.text.copy(alpha = 0.85f), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            if (Game.bestDistance > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph("flag", 16.dp, color = Void.violet, glow = false)
                Text("Best ${fmt(Game.bestDistance)} m", color = Void.dim, fontSize = 13.sp)
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).width(290.dp).fillMaxHeight().padding(top = 70.dp, end = 16.dp, bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            BigButton("play", "Run", "Hold to fly, let go to fall", accent = Void.violet, height = 74.dp) { state.start() }
            BoostRow()
            if (Game.supplyReady) BigButton("supply", "Daily supply drop", "Free credits for showing up", accent = CREDIT, phase = 1, badge = "!") {
                val s = Game.openSupply() ?: return@BigButton
                Audio.play(Snd.BELL)
                state.toast(Toast(System.nanoTime(), "supply", "Supply drop", "Day ${s.streak} in a row", s.credits, s.cores, s.shards))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("shop", "Shop", null, Modifier.weight(1f), accent = Void.cyan, phase = 2) { state.go(Screen.SHOP) }
                BigButton("locker", "Locker", null, Modifier.weight(1f), accent = Void.cyan, phase = 3) { state.go(Screen.LOCKER) }
            }
            val open = Game.missions.count { !it.done }
            BigButton("missions", "Missions", if (open > 0) "$open left today" else "All done today", accent = Void.violet, phase = 4, badge = if (open > 0) "$open" else null) {
                state.go(Screen.MISSIONS)
            }
        }
    }
}

/** The boosts in stock: tap to take one on the next run. */
@Composable
private fun BoostRow() {
    val stocked = Boost.entries.filter { Game.boostCount(it) > 0 }
    if (stocked.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in stocked) {
            val on = b in Game.armed
            Row(
                Modifier.weight(1f).squishy { Game.toggleArmed(b) }.glass(RoundedCornerShape(14.dp), if (on) Void.cyan else Void.border)
                    .then(if (on) Modifier.border(1.5.dp, Void.cyan, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Glyph(b.icon, 18.dp, color = if (on) Void.cyan else Void.dim, glow = on)
                Column {
                    Text(b.title, color = if (on) Void.text else Void.dim, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                    Text(if (on) "On · ${Game.boostCount(b)} left" else "×${Game.boostCount(b)}", color = Void.dim, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}
