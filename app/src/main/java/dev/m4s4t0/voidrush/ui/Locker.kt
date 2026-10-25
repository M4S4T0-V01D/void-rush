package dev.m4s4t0.voidrush.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Snd
import dev.m4s4t0.voidrush.model.Decal
import dev.m4s4t0.voidrush.model.Finish
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.Look
import dev.m4s4t0.voidrush.model.Paint
import dev.m4s4t0.voidrush.model.Price
import dev.m4s4t0.voidrush.model.SWATCHES
import dev.m4s4t0.voidrush.model.cosmetics
import dev.m4s4t0.voidui.Pills
import dev.m4s4t0.voidui.Void

private enum class LockerTab(val title: String) {
    COLOURS("Colours"), FINISH("Finish"), DECAL("Decal"), TRAIL("Trail"), FLAME("Exhaust"), BURST("Sparkle"), TITLE("Title"), EMBLEM("Emblem");

    val look get() = when (this) { TRAIL -> Look.TRAIL; FLAME -> Look.FLAME; BURST -> Look.BURST; TITLE -> Look.TITLE; EMBLEM -> Look.EMBLEM; else -> null }
}

private enum class Part(val title: String) { PRIMARY("Suit"), SECONDARY("Trim"), GLOW("Glow") }

/**
 * The runner's wardrobe. Tapping anything tries it on; owned things are fitted straight away, and tapping something
 * you're already trying on buys it.
 */
@Composable
fun LockerScreen(state: AppState) {
    var tab by rememberSaveable { mutableStateOf(LockerTab.COLOURS) }
    var part by rememberSaveable { mutableStateOf(Part.PRIMARY) }
    val worn = Game.loadout.paint

    /** Try [next] on; fit [fitted] if it's owned, buy it if it was already being tried. */
    fun pick(next: Paint, fitted: Paint, key: String, price: Price, wasTrying: Boolean) {
        state.preview = next
        when {
            Game.owns(key, price) -> if (fitted != worn) { Game.paint(fitted); Audio.play(Snd.POWER) }
            wasTrying -> if (Game.buy(key, price)) { Game.paint(fitted); Audio.play(Snd.BUY) } else Audio.play(Snd.DENY)
            else -> Audio.play(Snd.TAP, 0.6f)
        }
    }

    Column(Modifier.fillMaxSize().safe()) {
        GameBar("Locker", { state.go(Screen.HOME) }, icon = "locker") { WalletChip(shards = true) }
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Box(Modifier.weight(0.8f).fillMaxHeight()) {
                val p = state.preview
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        p.look(Look.TITLE).title, color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.glass(RoundedCornerShape(50), alpha = 0.6f).padding(horizontal = 14.dp, vertical = 5.dp),
                    )
                    if (p != worn) {
                        Text("Trying on · tap it again to buy", color = Void.dim, fontSize = 11.sp)
                        ActionButton("Back to my look", icon = "retry", primary = false) { state.preview = worn }
                    }
                }
            }
            Column(Modifier.weight(1.2f).fillMaxHeight()) {
                Pills(LockerTab.entries.map { it to it.title }, tab) { tab = it }
                Spacer(Modifier.height(8.dp))
                val p = state.preview
                when (tab) {
                    LockerTab.COLOURS -> Column {
                        Pills(Part.entries.map { it to it.title }, part) { part = it }
                        Spacer(Modifier.height(8.dp))
                        val current = when (part) { Part.PRIMARY -> p.primary; Part.SECONDARY -> p.secondary; Part.GLOW -> p.glow }
                        val mine = when (part) { Part.PRIMARY -> worn.primary; Part.SECONDARY -> worn.secondary; Part.GLOW -> worn.glow }
                        Tiles(SWATCHES) { s ->
                            fun Paint.set() = when (part) { Part.PRIMARY -> copy(primary = s.id); Part.SECONDARY -> copy(secondary = s.id); Part.GLOW -> copy(glow = s.id) }
                            ItemTile(
                                "paint", s.title, null, s.price, Game.owns(s), on = mine == s.id, selected = current == s.id,
                                action = if (Game.owns(s)) (if (mine == s.id) "Wearing" else "Owned") else null, swatch = argbColor(s.argb),
                            ) { pick(p.set(), worn.set(), s.key, s.price, current == s.id) }
                        }
                    }
                    LockerTab.FINISH -> Tiles(Finish.entries) { f ->
                        ItemTile(
                            "diamond", f.title, f.desc, f.price, Game.owns(f), on = worn.finish == f, selected = p.finish == f,
                            action = if (Game.owns(f)) (if (worn.finish == f) "Wearing" else "Owned") else null,
                        ) { pick(p.copy(finish = f), worn.copy(finish = f), f.key, f.price, p.finish == f) }
                    }
                    LockerTab.DECAL -> Tiles(Decal.entries) { d ->
                        ItemTile(
                            "paint", d.title, null, d.price, Game.owns(d), on = worn.decal == d, selected = p.decal == d,
                            action = if (Game.owns(d)) (if (worn.decal == d) "Wearing" else "Owned") else null,
                        ) { pick(p.copy(decal = d), worn.copy(decal = d), d.key, d.price, p.decal == d) }
                    }
                    else -> {
                        val look = tab.look!!
                        Tiles(cosmetics(look)) { c ->
                            val icon = when (look) {
                                Look.EMBLEM -> if (c.id == "none") "close" else c.id
                                Look.TRAIL -> "trail"; Look.FLAME -> "flame"; Look.BURST -> "burst"; Look.TITLE -> "title"
                            }
                            val on = worn.id(look) == c.id
                            ItemTile(
                                icon, c.title, null, c.price, Game.owns(c), on = on, selected = p.id(look) == c.id,
                                action = if (Game.owns(c)) (if (on) "Wearing" else "Owned") else null,
                                swatch = if (c.argb != 0L && look != Look.EMBLEM && look != Look.TITLE) argbColor(c.argb) else null,
                            ) { pick(p.with(c), worn.with(c), c.key, c.price, p.id(look) == c.id) }
                        }
                    }
                }
            }
        }
    }
}
