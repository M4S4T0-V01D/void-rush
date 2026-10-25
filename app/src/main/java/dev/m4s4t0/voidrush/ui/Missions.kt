package dev.m4s4t0.voidrush.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.m4s4t0.voidrush.model.Currency
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.MEDALS
import dev.m4s4t0.voidrush.model.Missions
import dev.m4s4t0.voidui.SectionTitle
import dev.m4s4t0.voidui.Void

@Composable
fun MissionsScreen(state: AppState) {
    Column(Modifier.fillMaxSize().safe()) {
        GameBar("Missions", { state.go(Screen.HOME) }, icon = "missions", subtitle = "New ones every day") { WalletChip() }
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Today")
                MissionList(Modifier.fillMaxWidth())
                Text(
                    "Finish all of today's missions for ${Missions.ALL_BONUS_CORES} bonus cores.",
                    color = Void.dim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Medals · ${Game.medals.size}/${MEDALS.size}")
                for (m in MEDALS) {
                    val got = m.id in Game.medals
                    val v = (Game.stats[m.stat] ?: 0L).coerceAtMost(m.goal)
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), if (got) CREDIT.copy(alpha = 0.6f) else Void.border).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Glyph(if (got) "medal" else m.icon, 26.dp, color = if (got) CREDIT else Void.dim, glow = got)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(m.title, color = if (got) Void.text else Void.dim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(m.desc, color = Void.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!got) Bar(v.toFloat() / m.goal, Modifier.fillMaxWidth(), 5.dp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Money(m.cores.toLong(), Currency.CORES, fontSize = 12.sp, icon = 14.dp, dimmed = got)
                            if (m.unlock != null) Text("+ a reward", color = Void.violet, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
