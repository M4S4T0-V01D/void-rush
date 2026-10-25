package dev.m4s4t0.voidrush.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

/** Something worth celebrating on screen. */
sealed interface GameEvent {
    data class LevelUp(val level: Int) : GameEvent
    data class MedalEarned(val medal: Medal) : GameEvent
    data class MissionDone(val mission: Mission, val allDone: Boolean) : GameEvent
}

/** How hard the GPU works: the render scale, anti-aliasing, how many sparks fly and how rich the backdrop is. */
enum class Quality(val title: String, val scale: Float, val msaa: Boolean, val particles: Float, val nebula: Int) {
    LOW("Battery saver", 0.6f, false, 0.45f, 1),
    MEDIUM("Balanced", 0.8f, false, 0.75f, 2),
    HIGH("Beautiful", 1f, true, 1f, 3),
}

/** What a run brought home, from the engine. Distances are in metres. */
data class RunResult(
    val distance: Int,
    val chips: Int,
    val credits: Int,
    val nearMisses: Int = 0,
    val rigs: Int = 0,
    val zappers: Int = 0,
    val missiles: Int = 0,
    val lasers: Int = 0,
    val drones: Int = 0,
    val flips: Int = 0,
    val blinks: Int = 0,
    val bestAir: Int = 0,
    val bestDry: Int = 0,
    val spins: Int = 0,
    val revives: Int = 0,
    val seconds: Float = 0f,
    /** What ended it: "zapper", "missile", "laser", "drone" or "quit". */
    val cause: String = "quit",
)

/** The debrief: what the run earned on top of what was picked up, and the spins it won. */
data class Outcome(
    val result: RunResult,
    val reward: Reward,
    val newBest: Boolean,
    val levelBefore: Int,
    val levelAfter: Int,
    val spins: Int,
)

/**
 * Everything the runner has: credits, cores, prism shards, XP, stats, owned gear, upgrades, boosts and daily missions.
 * Compose state, so the UI follows along; saved to SharedPreferences. Nothing leaves the device.
 */
object Game {
    private var prefs: SharedPreferences? = null
    private val rnd = Random(System.nanoTime())

    var credits by mutableIntStateOf(0)
        private set
    var cores by mutableIntStateOf(0)
        private set
    /** Prism shards: the rare currency, from the spin, medals and the daily drop. */
    var shards by mutableIntStateOf(0)
        private set
    var xp by mutableLongStateOf(0L)
        private set
    var loadout by mutableStateOf(Loadout())
        private set
    val stats = mutableStateMapOf<String, Long>()
    private val owned = mutableStateListOf<String>()
    private val upgrades = mutableStateMapOf<String, Int>()
    /** Boosts in stock, and the ones armed for the next run. */
    val boosts = mutableStateMapOf<String, Int>()
    val armed = mutableStateListOf<Boost>()
    val medals = mutableStateListOf<String>()
    val missions = mutableStateListOf<Mission>()
    var supplyReady by mutableStateOf(false)
        private set
    /** Free revives won on the wheel, used before cores. */
    var freeRevives by mutableIntStateOf(0)
        private set

    var sounds by mutableStateOf(true)
    var music by mutableStateOf(true)
    var volume by mutableStateOf(0.8f)
    var musicVolume by mutableStateOf(0.6f)
    var haptics by mutableStateOf(true)
    var quality by mutableStateOf(Quality.MEDIUM)
    /** Holds the game to 60 frames a second even on faster screens, which saves a lot of battery. */
    var cap60 by mutableStateOf(true)
    var showHitbox by mutableStateOf(false)
    var shake by mutableStateOf(true)
    /** Tap anywhere to flip (rigs), instead of only on the right half. */
    var leftHanded by mutableStateOf(false)

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    val level get() = Levels.of(xp).first
    val bestDistance get() = stats[Stat.BEST_DISTANCE] ?: 0L

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("game", Context.MODE_PRIVATE)
        load()
        visit(LocalDate.now())
        saveAll()
    }

    /** For tests: a fresh runner with nothing saved. */
    fun initForTest() {
        prefs = null
        clearState()
        credits = 250; cores = 3
        visit(LocalDate.now())
    }

    /** For tests: set the wallet and xp directly. */
    internal fun setForTest(credits: Int = this.credits, cores: Int = this.cores, xp: Long = this.xp) {
        this.credits = credits; this.cores = cores; this.xp = xp
    }

    private fun clearState() {
        stats.clear(); owned.clear(); upgrades.clear(); boosts.clear(); armed.clear(); medals.clear(); missions.clear()
        credits = 0; cores = 0; shards = 0; xp = 0; freeRevives = 0; loadout = Loadout()
    }

    private fun load() {
        val p = prefs ?: return
        credits = p.getInt("credits", 250)
        cores = p.getInt("cores", 3)
        shards = p.getInt("shards", 0)
        xp = p.getLong("xp", 0)
        freeRevives = p.getInt("freeRevives", 0)
        sounds = p.getBoolean("sounds", true)
        music = p.getBoolean("music", true)
        volume = p.getFloat("volume", 0.8f)
        musicVolume = p.getFloat("musicVolume", 0.6f)
        haptics = p.getBoolean("haptics", true)
        quality = runCatching { Quality.valueOf(p.getString("quality", null)!!) }.getOrDefault(Quality.MEDIUM)
        cap60 = p.getBoolean("cap60", true)
        showHitbox = p.getBoolean("showHitbox", false)
        shake = p.getBoolean("shake", true)
        leftHanded = p.getBoolean("leftHanded", false)
        readMap("stats") { k, j -> stats[k] = j.getLong(k) }
        readMap("upgrades") { k, j -> upgrades[k] = j.getInt(k) }
        readMap("boosts") { k, j -> boosts[k] = j.getInt(k) }
        owned.addAll(p.getStringSet("owned", emptySet())!!)
        medals.addAll(p.getStringSet("medals", emptySet())!!)
        loadout = runCatching { loadoutFrom(JSONObject(p.getString("loadout", null)!!)) }.getOrDefault(Loadout())
        val ms = runCatching { JSONArray(p.getString("missions", "[]")) }.getOrNull()
        if (ms != null) for (i in 0 until ms.length()) runCatching { missions += Mission.fromJson(ms.getJSONObject(i)) }
    }

    private fun readMap(key: String, put: (String, JSONObject) -> Unit) {
        val j = runCatching { JSONObject(prefs!!.getString(key, "{}")!!) }.getOrNull() ?: return
        j.keys().forEach { runCatching { put(it, j) } }
    }

    private fun str(key: String) = prefs?.getString(key, null)
    private fun putStr(key: String, v: String) { prefs?.edit()?.putString(key, v)?.apply() }

    // ---- visiting: the daily supply drop and missions ----

    private fun visit(today: LocalDate) {
        val last = str("lastVisit")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        when {
            last == null -> stats[Stat.STREAK] = 1
            last == today -> Unit
            last.plusDays(1) == today -> stats[Stat.STREAK] = (stats[Stat.STREAK] ?: 0) + 1
            else -> stats[Stat.STREAK] = 1
        }
        putStr("lastVisit", today.toString())
        supplyReady = str("supplyDay") != today.toString()
        if (str("missionDay") != today.toString() || missions.isEmpty()) {
            missions.clear()
            missions.addAll(Missions.daily(rnd, level, stats))
            putStr("missionDay", today.toString())
        }
        checkMedals()
    }

    /** The daily supply drop's contents. */
    data class Supply(val credits: Int, val cores: Int, val shards: Int, val streak: Int)

    fun openSupply(): Supply? {
        if (!supplyReady) return null
        supplyReady = false
        putStr("supplyDay", LocalDate.now().toString())
        val streak = (stats[Stat.STREAK] ?: 1).toInt()
        val amount = 200 + level * 25 + 50 * streak.coerceAtMost(10) + rnd.nextInt(8) * 10
        // A core a day, a handful and some prism shards on every seventh day in a row.
        val c = if (streak % 7 == 0) 5 else 1
        val s = if (streak % 7 == 0) 10 else 1
        credits += amount
        cores += c
        shards += s
        add(Stat.CREDITS, amount.toLong())
        saveAll()
        return Supply(amount, c, s, streak)
    }

    // ---- owning, buying, upgrading ----

    fun owns(key: String, price: Price) = price.free || key in owned
    fun has(key: String) = key in owned
    fun owns(s: Swatch) = owns(s.key, s.price)
    fun owns(f: Finish) = owns(f.key, f.price)
    fun owns(d: Decal) = owns(d.key, d.price)
    fun owns(c: Cosmetic) = owns(c.key, c.price)
    fun owns(p: Pack) = owns(p.key, p.price)
    fun owns(r: Rig) = owns(r.key, r.price)
    fun owns(g: Gadget) = owns(g.key, g.price)

    /** Hands over something only a medal can give (or anything else, for free). */
    fun grant(key: String) {
        if (key.isEmpty() || key in owned) return
        owned += key
        saveAll()
    }

    fun canAfford(price: Price) = level >= price.level && when (price.currency) {
        Currency.CREDITS -> credits >= price.amount
        Currency.CORES -> cores >= price.amount
        Currency.SHARDS -> shards >= price.amount
        Currency.REWARD -> false
    }

    /** Buys whatever [key] names for [price]. Returns false if it's out of reach. */
    fun buy(key: String, price: Price): Boolean {
        if (owns(key, price)) return true
        if (!canAfford(price)) return false
        when (price.currency) {
            Currency.CREDITS -> credits -= price.amount
            Currency.CORES -> cores -= price.amount
            Currency.SHARDS -> shards -= price.amount
            Currency.REWARD -> return false
        }
        owned += key
        add(Stat.BOUGHT)
        checkMedals()
        saveAll()
        return true
    }

    fun upgradeLevel(u: Upgrade): Int = upgrades[u.key] ?: 0

    fun upgrade(u: Upgrade): Boolean {
        val lvl = upgradeLevel(u)
        val cost = u.cost(lvl) ?: return false
        if (credits < cost) return false
        credits -= cost
        upgrades[u.key] = lvl + 1
        add(Stat.UPGRADES)
        checkMedals()
        saveAll()
        return true
    }

    fun boostCount(b: Boost) = boosts[b.key] ?: 0

    fun buyBoost(b: Boost): Boolean {
        if (credits < b.price) return false
        credits -= b.price
        boosts[b.key] = boostCount(b) + 1
        saveAll()
        return true
    }

    /** Arms or disarms [b] for the next run (only one head start at a time). */
    fun toggleArmed(b: Boost) {
        if (b in armed) armed.remove(b)
        else if (boostCount(b) > 0) {
            if (b == Boost.HEADSTART) armed.remove(Boost.MEGA)
            if (b == Boost.MEGA) armed.remove(Boost.HEADSTART)
            armed += b
        }
    }

    /** Takes the armed boosts out of stock for a run that's starting. */
    fun useArmed(): List<Boost> {
        val used = armed.filter { boostCount(it) > 0 }
        used.forEach { boosts[it.key] = boostCount(it) - 1 }
        armed.clear()
        armed.addAll(used.filter { boostCount(it) > 0 })
        if (used.isNotEmpty()) saveAll()
        return used
    }

    fun equip(p: Pack) {
        if (!owns(p)) return
        loadout = loadout.copy(pack = p)
        saveAll()
    }

    /** Fits [g] in a free gadget slot, or takes it off if it's fitted. False if the slots are full. */
    fun toggleGadget(g: Gadget): Boolean {
        if (!owns(g)) return false
        val now = loadout.gadgets
        loadout = when {
            g in now -> loadout.copy(gadgets = now - g)
            now.size >= Loadout.GADGET_SLOTS -> return false
            else -> loadout.copy(gadgets = now + g)
        }
        saveAll()
        return true
    }

    fun paint(p: Paint) {
        loadout = loadout.copy(paint = p)
        saveAll()
    }

    /** The rigs a rig token can hand out. */
    val rigs get() = Rig.entries.filter(::owns)

    // ---- revives and the spin ----

    /** What the [n]th revive of a run (from 0) costs in cores: 1, 2, 4, 8... */
    fun reviveCost(n: Int) = 1 shl n.coerceIn(0, 6)

    /** True if the [n]th revive this run can be paid (by a free revive, the Second wind gadget on the first, or cores). */
    fun canRevive(n: Int) = freeRevives > 0 || (n == 0 && loadout.has(Gadget.SECOND)) || cores >= reviveCost(n)

    fun revive(n: Int): Boolean {
        when {
            n == 0 && loadout.has(Gadget.SECOND) -> Unit
            freeRevives > 0 -> freeRevives--
            cores >= reviveCost(n) -> cores -= reviveCost(n)
            else -> return false
        }
        add(Stat.REVIVES)
        saveAll()
        return true
    }

    /** Spins the wheel once and pays out. Returns the index of the prize on [Spin.WHEEL]. */
    fun spin(): Int {
        val i = Spin.roll(rnd)
        val p = Spin.WHEEL[i].first
        credits += p.credits
        cores += p.cores
        shards += p.shards
        if (p.credits > 0) add(Stat.CREDITS, p.credits.toLong())
        p.boost?.let { boosts[it.key] = boostCount(it) + 1 }
        if (p.revive) freeRevives++
        add(Stat.SPINS)
        checkMissions()
        checkMedals()
        saveAll()
        return i
    }

    // ---- banking a run ----

    /** What a chip is worth with the Credit value upgrade and the Prism lens. */
    fun chipValue(): Float = (1f + upgradeLevel(Upgrade.VALUE) * 0.05f) * (if (loadout.has(Gadget.LENS)) 1.2f else 1f)

    /** Banks a finished run and works out its reward. */
    fun finishRun(r: RunResult): Outcome {
        val before = level
        val newBest = r.distance > bestDistance
        add(Stat.RUNS)
        add(Stat.DISTANCE, r.distance.toLong())
        add(Stat.CHIPS, r.chips.toLong())
        add(Stat.NEAR_MISSES, r.nearMisses.toLong())
        add(Stat.RIGS, r.rigs.toLong())
        add(Stat.ZAPPERS, r.zappers.toLong())
        add(Stat.MISSILES, r.missiles.toLong())
        add(Stat.LASERS, r.lasers.toLong())
        add(Stat.DRONES, r.drones.toLong())
        add(Stat.FLIPS, r.flips.toLong())
        add(Stat.BLINKS, r.blinks.toLong())
        add(Stat.SECONDS, r.seconds.toLong())
        best(Stat.BEST_DISTANCE, r.distance.toLong())
        best(Stat.BEST_CHIPS, r.chips.toLong())
        best(Stat.BEST_AIR, r.bestAir.toLong())
        best(Stat.BEST_DRY, r.bestDry.toLong())
        // One-run missions remember the best run since they were handed out.
        val single = mapOf(Stat.BEST_DISTANCE to r.distance, Stat.BEST_CHIPS to r.chips, Stat.BEST_AIR to r.bestAir, Stat.BEST_DRY to r.bestDry)
        for (i in missions.indices) {
            val m = missions[i]
            val v = single[m.stat] ?: continue
            if (m.single && v > m.best) missions[i] = m.copy(best = v.toLong())
        }

        // XP for distance, and a bonus for beating the record.
        val bonus = if (newBest && r.distance >= 500) 100 + r.distance / 20 else 0
        val reward = Reward(bonus, r.distance / 10 + r.nearMisses * 2 + 5, 0)
        credits += r.credits + reward.credits
        add(Stat.CREDITS, (r.credits + reward.credits).toLong())
        gainXp(reward.xp)
        checkMissions()
        checkMedals()
        saveAll()
        return Outcome(r, reward, newBest, before, level, r.spins)
    }

    private fun gainXp(n: Int) {
        val before = level
        xp += n
        for (l in before + 1..level) {
            _events.tryEmit(GameEvent.LevelUp(l))
            credits += 100 + l * 20
        }
    }

    // ---- stats, missions, medals ----

    fun add(stat: String, n: Long = 1) {
        if (n == 0L) return
        stats[stat] = (stats[stat] ?: 0) + n
    }

    private fun best(stat: String, v: Long) {
        if (v > (stats[stat] ?: 0)) stats[stat] = v
    }

    private fun checkMissions() {
        for (i in missions.indices) {
            val m = missions[i]
            if (m.done || m.progress(stats) < m.goal) continue
            missions[i] = m.copy(done = true)
            add(Stat.MISSIONS)
            credits += m.credits
            cores += m.cores
            gainXp(40 + level * 4)
            val all = missions.all { it.done }
            if (all) cores += Missions.ALL_BONUS_CORES
            _events.tryEmit(GameEvent.MissionDone(m, all))
        }
    }

    private fun checkMedals() {
        for (m in MEDALS) {
            if (m.id in medals || (stats[m.stat] ?: 0) < m.goal) continue
            medals += m.id
            cores += m.cores
            m.unlock?.let { if (it !in owned) owned += it }
            _events.tryEmit(GameEvent.MedalEarned(m))
        }
    }

    // ---- saving ----

    private val SETTINGS = listOf("sounds", "music", "volume", "musicVolume", "haptics", "quality", "cap60", "showHitbox", "shake", "leftHanded")

    fun saveSettings() {
        val p = prefs ?: return
        p.edit()
            .putBoolean("sounds", sounds).putBoolean("music", music).putFloat("volume", volume).putFloat("musicVolume", musicVolume)
            .putBoolean("haptics", haptics).putString("quality", quality.name).putBoolean("cap60", cap60)
            .putBoolean("showHitbox", showHitbox).putBoolean("shake", shake).putBoolean("leftHanded", leftHanded)
            .apply()
    }

    fun saveAll() {
        val p = prefs ?: return
        saveSettings()
        p.edit()
            .putInt("credits", credits).putInt("cores", cores).putInt("shards", shards).putLong("xp", xp).putInt("freeRevives", freeRevives)
            .putString("stats", JSONObject(stats.toMap()).toString())
            .putString("upgrades", JSONObject(upgrades.toMap()).toString())
            .putString("boosts", JSONObject(boosts.toMap()).toString())
            .putStringSet("owned", owned.toSet())
            .putStringSet("medals", medals.toSet())
            .putString("loadout", loadoutJson(loadout).toString())
            .putString("missions", JSONArray(missions.map { it.toJson() }).toString())
            .apply()
    }

    /** Back to a brand-new runner (settings stay). */
    fun reset() {
        val p = prefs ?: return
        val e = p.edit()
        p.all.keys.filter { it !in SETTINGS }.forEach { e.remove(it) }
        e.commit()
        clearState()
        load()
        visit(LocalDate.now())
        saveAll()
    }

    fun loadoutJson(l: Loadout): JSONObject = JSONObject()
        .put("pack", l.pack.name).put("gadgets", JSONArray(l.gadgets.map { it.name }))
        .put("c1", l.paint.primary).put("c2", l.paint.secondary).put("c3", l.paint.glow)
        .put("finish", l.paint.finish.name).put("decal", l.paint.decal.name)
        .put("trail", l.paint.trail).put("flame", l.paint.flame).put("burst", l.paint.burst)
        .put("title", l.paint.title).put("emblem", l.paint.emblem)

    fun loadoutFrom(j: JSONObject): Loadout {
        fun <T : Enum<T>> e(values: List<T>, key: String, def: T) = values.firstOrNull { it.name == j.optString(key) } ?: def
        val d = Loadout()
        val gs = j.optJSONArray("gadgets")
        val gadgets = if (gs == null) emptyList() else (0 until gs.length()).mapNotNull { i -> Gadget.entries.firstOrNull { it.name == gs.optString(i) } }
        return Loadout(
            Paint(
                j.optString("c1", d.paint.primary), j.optString("c2", d.paint.secondary), j.optString("c3", d.paint.glow),
                e(Finish.entries, "finish", d.paint.finish), e(Decal.entries, "decal", d.paint.decal),
                j.optString("trail", d.paint.trail), j.optString("flame", d.paint.flame), j.optString("burst", d.paint.burst),
                j.optString("title", d.paint.title), j.optString("emblem", d.paint.emblem),
            ),
            e(Pack.entries, "pack", d.pack),
            gadgets.distinct().take(Loadout.GADGET_SLOTS),
        )
    }
}
