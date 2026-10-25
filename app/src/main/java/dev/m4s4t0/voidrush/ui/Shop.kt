package dev.m4s4t0.voidrush.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Snd
import dev.m4s4t0.voidrush.model.Boost
import dev.m4s4t0.voidrush.model.Currency
import dev.m4s4t0.voidrush.model.Gadget
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.Loadout
import dev.m4s4t0.voidrush.model.Pack
import dev.m4s4t0.voidrush.model.Price
import dev.m4s4t0.voidrush.model.Rig
import dev.m4s4t0.voidrush.model.Upgrade
import dev.m4s4t0.voidui.Pills
import dev.m4s4t0.voidui.Void

private enum class ShopTab(val title: String) { PACKS("Packs"), RIGS("Rigs"), GADGETS("Gadgets"), UPGRADES("Upgrades"), BOOSTS("Boosts") }

@Composable
fun ShopScreen(state: AppState) {
    var tab by rememberSaveable { mutableStateOf(ShopTab.PACKS) }
    Column(Modifier.fillMaxSize().safe()) {
        GameBar("Shop", { state.go(Screen.HOME) }, icon = "shop") { WalletChip(shards = true) }
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            // The left side stays clear: the renderer shows the runner wearing whatever is picked here.
            Box(Modifier.weight(0.8f).fillMaxHeight()) {
                val note = when (tab) {
                    ShopTab.PACKS -> state.previewPack.title
                    ShopTab.RIGS -> state.previewRig?.title ?: "Tap a rig to see it"
                    else -> null
                }
                if (note != null) Text(
                    note, color = Void.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).glass(RoundedCornerShape(50), alpha = 0.6f)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            Column(Modifier.weight(1.2f).fillMaxHeight()) {
                Pills(ShopTab.entries.map { it to it.title }, tab) {
                    tab = it
                    state.previewPack = Game.loadout.pack
                    state.previewRig = null
                }
                Spacer(Modifier.height(8.dp))
                when (tab) {
                    ShopTab.PACKS -> Tiles(Pack.entries) { p ->
                        val owned = Game.owns(p)
                        val on = Game.loadout.pack == p
                        ItemTile(
                            "engine", p.title, p.desc, p.price, owned, on, selected = state.previewPack == p,
                            action = when { on -> "Equipped"; owned -> "Equip"; else -> null },
                        ) {
                            state.previewPack = p
                            when {
                                on -> Unit
                                owned -> { Game.equip(p); Audio.play(Snd.POWER) }
                                else -> if (Game.buy(p.key, p.price)) { Game.equip(p); Audio.play(Snd.BUY) } else Audio.play(Snd.DENY)
                            }
                        }
                    }
                    ShopTab.RIGS -> Tiles(Rig.entries) { r ->
                        val owned = Game.owns(r)
                        ItemTile(r.icon, r.title, r.desc, r.price, owned, false, selected = state.previewRig == r, action = if (owned) "In the mix" else null) {
                            state.previewRig = r
                            if (!owned) { if (Game.buy(r.key, r.price)) Audio.play(Snd.BUY) else Audio.play(Snd.DENY) }
                        }
                    }
                    ShopTab.GADGETS -> Column(Modifier.fillMaxSize()) {
                        val used = Game.loadout.gadgets.size
                        Text(
                            "Gadgets equipped: $used of ${Loadout.GADGET_SLOTS}", color = Void.dim, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Tiles(Gadget.entries) { g ->
                            val owned = Game.owns(g)
                            val on = Game.loadout.has(g)
                            ItemTile(g.icon, g.title, g.desc, g.price, owned, on, action = when { on -> "Equipped"; owned -> "Equip"; else -> null }) {
                                when {
                                    owned -> if (Game.toggleGadget(g)) Audio.play(Snd.POWER) else Audio.play(Snd.DENY)
                                    Game.buy(g.key, g.price) -> { Audio.play(Snd.BUY); if (used < Loadout.GADGET_SLOTS) Game.toggleGadget(g) }
                                    else -> Audio.play(Snd.DENY)
                                }
                            }
                        }
                    }
                    ShopTab.UPGRADES -> Tiles(Upgrade.entries) { u ->
                        val lvl = Game.upgradeLevel(u)
                        val cost = u.cost(lvl)
                        ItemTile(
                            u.icon, u.title, u.desc, cost?.let { Price(it) }, owned = cost == null, on = false,
                            action = if (cost == null) "Maxed" else null, steps = lvl to u.max,
                        ) { if (Game.upgrade(u)) Audio.play(Snd.LEVEL) else Audio.play(Snd.DENY) }
                    }
                    ShopTab.BOOSTS -> Tiles(Boost.entries) { b ->
                        val n = Game.boostCount(b)
                        ItemTile(b.icon, b.title, b.desc, Price(b.price), owned = false, on = false, count = n) {
                            if (Game.buyBoost(b)) Audio.play(Snd.BUY) else Audio.play(Snd.DENY)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> Tiles(items: List<T>, tile: @Composable (T) -> Unit) {
    LazyVerticalGrid(
        GridCells.Adaptive(170.dp), Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp),
    ) { items(items) { tile(it) } }
}

/**
 * One thing to buy or wear. [price] null means it can't be bought (maxed out); [action] replaces the price
 * once it's owned; [steps] shows upgrade pips; [count] shows how many are in stock.
 */
@Composable
fun ItemTile(
    icon: String, title: String, desc: String?, price: Price?, owned: Boolean, on: Boolean,
    selected: Boolean = false, action: String? = null, steps: Pair<Int, Int>? = null, count: Int? = null,
    swatch: Color? = null, onClick: () -> Unit,
) {
    val locked = price != null && !owned && Game.level < price.level
    val edge = when { on -> Void.cyan; selected -> Void.violet; else -> Void.border }
    Column(
        Modifier.fillMaxWidth().squishy(sound = false, onClick = onClick).glass(RoundedCornerShape(16.dp), edge)
            .then(if (on || selected) Modifier.border(1.5.dp, edge, RoundedCornerShape(16.dp)) else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (swatch != null) Box(Modifier.size(26.dp).background(swatch, CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
            else Glyph(if (locked) "lock" else icon, 26.dp, color = if (locked) Void.dim else if (on) Void.cyan else Void.violet, glow = !locked)
            Column(Modifier.weight(1f)) {
                Text(title, color = if (locked) Void.dim else Void.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count != null) Text("In stock: $count", color = Void.dim, fontSize = 11.sp)
            }
        }
        if (desc != null) Text(desc, color = Void.dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        if (steps != null) Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until steps.second) Box(
                Modifier.weight(1f).height(5.dp).background(if (i < steps.first) Void.cyan else Void.panelAlt, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when {
                action != null -> Text(action, color = if (on) Void.cyan else Void.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                locked -> LevelTag(price!!.level)
                price != null && price.currency != Currency.REWARD -> PriceTag(price)
                price != null -> Text("Earned in play", color = Void.dim, fontSize = 11.sp)
            }
        }
    }
}
