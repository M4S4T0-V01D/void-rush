package dev.m4s4t0.voidrush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.m4s4t0.voidrush.Updater
import dev.m4s4t0.voidrush.audio.Audio
import dev.m4s4t0.voidrush.audio.Haptics
import dev.m4s4t0.voidrush.audio.Snd
import dev.m4s4t0.voidrush.audio.Song
import dev.m4s4t0.voidrush.game.Hud
import dev.m4s4t0.voidrush.game.RunConfig
import dev.m4s4t0.voidrush.game.WorldEvent
import dev.m4s4t0.voidrush.game.WorldListener
import dev.m4s4t0.voidrush.gl.GameRenderer
import dev.m4s4t0.voidrush.gl.GameView
import dev.m4s4t0.voidrush.model.Currency
import dev.m4s4t0.voidrush.model.Game
import dev.m4s4t0.voidrush.model.GameEvent
import dev.m4s4t0.voidrush.model.Missions
import dev.m4s4t0.voidrush.model.Outcome
import dev.m4s4t0.voidrush.model.Pack
import dev.m4s4t0.voidrush.model.Paint
import dev.m4s4t0.voidrush.model.Rig
import dev.m4s4t0.voidui.Void
import kotlinx.coroutines.delay

/** PLAY is a run; the rest are menus floating over the demo run (or the locker's turntable). */
enum class Screen { HOME, PLAY, SHOP, LOCKER, MISSIONS, SETTINGS }

/** One run, as the HUD sees it. */
class RunState {
    var hud by mutableStateOf<Hud?>(null)
    var paused by mutableStateOf(false)
    /** Caught: waiting on revive-or-give-up. */
    var down by mutableStateOf(false)
    var revives by mutableIntStateOf(0)
    var outcome by mutableStateOf<Outcome?>(null)
    var callout by mutableStateOf<Callout?>(null)
    /** Spins still to take on the results screen, and what the ones taken landed on (indexes into Spin.WHEEL). */
    var spinsLeft by mutableIntStateOf(0)
    val prizes = mutableStateListOf<Int>()
}

data class Callout(val id: Long, val text: String, val icon: String)

/** A celebration card: a glyph, a title, a line, and what it paid. */
data class Toast(val id: Long, val icon: String, val title: String, val sub: String, val credits: Int = 0, val cores: Int = 0, val shards: Int = 0)

/** Shared between the screens and the GL view. */
class AppState {
    lateinit var view: GameView
    var screen by mutableStateOf(Screen.HOME)
    var run by mutableStateOf<RunState?>(null)
    /** What the locker's turntable shows: the fitted paint plus whatever's being tried on. */
    var preview by mutableStateOf(Game.loadout.paint)
    var previewPack by mutableStateOf(Game.loadout.pack)
    var previewRig by mutableStateOf<Rig?>(null)
    val toasts = mutableStateListOf<Toast>()

    fun go(s: Screen) {
        if (s == screen) return
        if (s == Screen.LOCKER || s == Screen.SHOP) { preview = Game.loadout.paint; previewPack = Game.loadout.pack; previewRig = null }
        if (screen == Screen.PLAY) leaveRun()
        screen = s
    }

    private fun leaveRun() {
        run = null
        Audio.muffled = false
        view.gl { paused = false }
    }

    /** Off on a run, with the armed boosts. */
    fun start() {
        if (screen == Screen.PLAY) leaveRun()
        Audio.play(Snd.WHOOSH, 0.9f)
        Haptics.buzz(Haptics.Kind.THUD, 0.6f)
        val cfg = RunConfig.fromGame(Game.useArmed())
        run = RunState()
        screen = Screen.PLAY
        view.gl { paused = false; play(cfg) }
    }

    fun pause(p: Boolean) {
        val r = run ?: return
        if (r.outcome != null || r.down) return
        r.paused = p
        Audio.muffled = p
        view.gl { paused = p }
    }

    fun revive() {
        val r = run ?: return
        if (!r.down || !Game.revive(r.revives)) { Audio.play(Snd.DENY, 0.7f); return }
        r.revives++
        r.down = false
        Audio.muffled = false
        view.gl { world.revive() }
    }

    /** Ends the run: from the revive prompt, or quitting from the pause menu. */
    fun giveUp() {
        val r = run ?: return
        r.down = false
        r.paused = false
        Audio.muffled = false
        view.gl { paused = false; world.finish() }
    }

    /** One turn of the prize wheel. Returns the prize's index. */
    fun spin(): Int? {
        val r = run ?: return null
        if (r.spinsLeft <= 0) return null
        r.spinsLeft--
        val i = Game.spin()
        r.prizes += i
        return i
    }

    fun toast(t: Toast) { toasts += t }

    internal fun onWorld(e: WorldEvent) {
        val r = run ?: return
        when (e) {
            is WorldEvent.Callout -> r.callout = Callout(System.nanoTime(), e.text, e.icon)
            is WorldEvent.Down -> { r.down = true; r.revives = e.revives; Audio.muffled = true }
            is WorldEvent.Over -> {
                r.down = false
                Audio.muffled = false
                val o = Game.finishRun(e.result)
                r.outcome = o
                r.spinsLeft = o.spins
                Audio.play(if (o.newBest) Snd.LEVEL else Snd.DONE, 0.9f)
            }
        }
    }
}

@Composable
fun RushAppUi() {
    val ctx = LocalContext.current
    val state = remember { AppState() }
    val screen = state.screen
    // A new surface when the quality changes (it sets the render scale and multisampling).
    val view = remember(Game.quality, Game.cap60) {
        GameView(ctx, Game.quality, Game.cap60, object : WorldListener {
            override fun hud(h: Hud) { state.run?.hud = h }
            override fun event(e: WorldEvent) = state.onWorld(e)
        }).also { state.view = it }
    }

    // Pause the GL loop and the mixer while the app is in the background; a run in progress pauses itself.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, view) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> { state.pause(true); view.onPause(); Game.saveAll() }
                Lifecycle.Event.ON_START -> Audio.start()
                Lifecycle.Event.ON_STOP -> { Audio.stop(); Game.saveAll() }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // The GL scene follows the screen: the demo run behind the menus, the turntable in the locker and the shop.
    LaunchedEffect(screen, state.preview, state.previewPack, state.previewRig, view) {
        val p: Paint = state.preview
        val pack: Pack = state.previewPack
        val rig = state.previewRig
        when (screen) {
            Screen.PLAY -> Unit
            Screen.LOCKER, Screen.SHOP -> view.gl { locker(p, pack, rig) }
            else -> { val cfg = RunConfig(paint = Game.loadout.paint, packStyle = Game.loadout.pack.style, seed = 7); view.gl { if (mode != GameRenderer.Mode.MENU) menu(cfg) } }
        }
    }
    LaunchedEffect(Game.shake, view) { val s = Game.shake; view.gl { shakeOn = s } }
    // The foundry's neon follows the theme.
    val a1 = Void.violet.toArgb()
    val a2 = Void.cyan.toArgb()
    LaunchedEffect(a1, a2, view) { view.gl { accent = a1; accent2 = a2 } }

    LaunchedEffect(screen) {
        Audio.song = when (screen) {
            Screen.PLAY -> Song.RUN
            Screen.LOCKER, Screen.SHOP -> Song.LOCKER
            else -> Song.MENU
        }
    }

    // The update notification was tapped: the update is in Settings.
    LaunchedEffect(Updater.showRequested) {
        if (Updater.showRequested && state.run == null) { Updater.showRequested = false; Updater.cancelNotification(ctx); state.go(Screen.SETTINGS) }
    }

    // Celebrations.
    LaunchedEffect(Unit) {
        Game.events.collect { e ->
            Audio.play(if (e is GameEvent.LevelUp) Snd.LEVEL else Snd.BELL, 0.9f)
            Haptics.buzz(Haptics.Kind.SUCCESS)
            when (e) {
                is GameEvent.LevelUp -> state.toast(Toast(System.nanoTime(), "level", "Level ${e.level}!", "Level-up bonus", credits = 100 + e.level * 20))
                is GameEvent.MedalEarned -> state.toast(Toast(System.nanoTime(), e.medal.icon, "Medal: ${e.medal.title}", e.medal.desc, cores = e.medal.cores))
                is GameEvent.MissionDone -> {
                    state.toast(Toast(System.nanoTime(), e.mission.icon, "Mission complete", e.mission.text, e.mission.credits, e.mission.cores))
                    if (e.allDone) state.toast(Toast(System.nanoTime() + 1, "trophy", "All of today's missions done!", "Bonus cores. New ones tomorrow", cores = Missions.ALL_BONUS_CORES))
                }
            }
        }
    }

    BackHandler {
        val r = state.run
        when {
            screen == Screen.PLAY && r != null && r.outcome == null && !r.down -> state.pause(!r.paused)
            screen == Screen.PLAY && r?.down == true -> state.giveUp()
            screen == Screen.HOME -> (ctx as? android.app.Activity)?.moveTaskToBack(true)
            else -> state.go(Screen.HOME)
        }
    }

    Box(Modifier.fillMaxSize().background(Void.bg)) {
        // The foundry is always behind everything; the menus float over it.
        key(view) { AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
        when (screen) {
            Screen.HOME -> HomeScreen(state)
            Screen.PLAY -> PlayScreen(state)
            Screen.SHOP -> ShopScreen(state)
            Screen.LOCKER -> LockerScreen(state)
            Screen.MISSIONS -> MissionsScreen(state)
            Screen.SETTINGS -> SettingsScreen(state)
        }
        Toasts(state, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun Toasts(state: AppState, modifier: Modifier) {
    val t = state.toasts.firstOrNull()
    LaunchedEffect(t?.id) {
        if (t != null) {
            delay(2800)
            state.toasts.remove(t)
        }
    }
    AnimatedVisibility(
        t != null,
        modifier.safe().padding(top = 12.dp),
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.8f),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val shown = t ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(22.dp)).background(Void.panel.copy(alpha = 0.96f))
                .border(1.5.dp, Void.cyan.copy(alpha = 0.7f), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Glyph(shown.icon, 40.dp, Modifier.bob(3.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(shown.title, color = Void.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (shown.sub.isNotEmpty()) Text(shown.sub, color = Void.dim, fontSize = 13.sp)
                if (shown.credits > 0 || shown.cores > 0 || shown.shards > 0) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (shown.credits > 0) Money(shown.credits.toLong(), fontSize = 14.sp)
                    if (shown.cores > 0) Money(shown.cores.toLong(), Currency.CORES, fontSize = 14.sp)
                    if (shown.shards > 0) Money(shown.shards.toLong(), Currency.SHARDS, fontSize = 14.sp)
                }
            }
        }
    }
}
