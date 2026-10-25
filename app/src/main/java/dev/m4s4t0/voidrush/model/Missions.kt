package dev.m4s4t0.voidrush.model

import org.json.JSONObject
import kotlin.random.Random

/** The counters the game keeps (Game.stats). */
object Stat {
    const val DISTANCE = "distance"
    const val RUNS = "runs"
    const val CHIPS = "chips"
    const val CREDITS = "creditsEarned"
    const val NEAR_MISSES = "nearMisses"
    const val RIGS = "rigs"
    const val ZAPPERS = "zappers"
    const val MISSILES = "missiles"
    const val LASERS = "lasers"
    const val DRONES = "drones"
    const val FLIPS = "flips"
    const val BLINKS = "blinks"
    const val SPINS = "spins"
    const val REVIVES = "revives"
    const val SECONDS = "seconds"
    const val BOUGHT = "bought"
    const val UPGRADES = "upgrades"
    const val MISSIONS = "missions"
    const val STREAK = "streak"
    const val BEST_DISTANCE = "bestDistance"
    const val BEST_CHIPS = "bestChips"
    /** The furthest flown in one run without touching the floor, in metres. */
    const val BEST_AIR = "bestAir"
    /** The furthest run without picking up a single credit. */
    const val BEST_DRY = "bestDry"

    /** Counters that hold a best rather than a running total. */
    val BESTS = setOf(BEST_DISTANCE, BEST_CHIPS, BEST_AIR, BEST_DRY, STREAK)
}

/**
 * A daily mission: push [stat] up by [goal] today. [base] is where the stat stood when it was handed out. A [single]
 * mission has to be done within one run: [best] is the most a run has managed since.
 */
data class Mission(
    val id: String, val stat: String, val goal: Long, val text: String, val icon: String, val credits: Int, val cores: Int,
    val base: Long, val done: Boolean = false, val single: Boolean = false, val best: Long = 0,
) {
    fun progress(stats: Map<String, Long>) = if (single) best.coerceIn(0L, goal) else ((stats[stat] ?: 0L) - base).coerceIn(0L, goal)

    fun toJson(): JSONObject = JSONObject().put("id", id).put("stat", stat).put("goal", goal).put("text", text).put("icon", icon)
        .put("credits", credits).put("cores", cores).put("base", base).put("done", done).put("single", single).put("best", best)

    companion object {
        fun fromJson(j: JSONObject) = Mission(
            j.getString("id"), j.getString("stat"), j.getLong("goal"), j.getString("text"), j.optString("icon", "target"),
            j.getInt("credits"), j.optInt("cores"), j.getLong("base"), j.optBoolean("done"), j.optBoolean("single"), j.optLong("best"),
        )
    }
}

object Missions {
    /** Paid when all three of the day's missions are done. */
    const val ALL_BONUS_CORES = 3

    private class Template(
        val id: String, val stat: String, val goal: (Int) -> Long, val text: (Long) -> String, val icon: String,
        val minLevel: Int = 1, val single: Boolean = false,
    )

    private fun m(n: Long) = "%,d m".format(n)

    private val TEMPLATES = listOf(
        Template("distance", Stat.DISTANCE, { 3000L + it * 400 }, { "Run ${m(it)} in total" }, "distance"),
        Template("chips", Stat.CHIPS, { 400L + it * 40 }, { "Grab $it credit chips" }, "credit"),
        Template("near", Stat.NEAR_MISSES, { 10L + it }, { "Scrape past $it hazards" }, "graze"),
        Template("zappers", Stat.ZAPPERS, { 40L + it * 4 }, { "Slip past $it zappers" }, "zap"),
        Template("missiles", Stat.MISSILES, { 8L + it / 2 }, { "Dodge $it missiles" }, "missile"),
        Template("rigs", Stat.RIGS, { 3L + it / 6 }, { "Ride $it rigs" }, "mech"),
        Template("runs", Stat.RUNS, { 5L + it / 8 }, { "Go on $it runs" }, "runner"),
        Template("one_run", Stat.BEST_DISTANCE, { 800L + it * 60 }, { "Run ${m(it)} in one go" }, "flag", single = true),
        Template("air", Stat.BEST_AIR, { 300L + it * 20 }, { "Fly ${m(it)} without touching the floor" }, "boost", minLevel = 2, single = true),
        Template("dry", Stat.BEST_DRY, { 400L + it * 25 }, { "Run ${m(it)} without grabbing a credit" }, "ghost", minLevel = 3, single = true),
        Template("lasers", Stat.LASERS, { 6L + it / 3 }, { "Outlast $it laser sweeps" }, "laser", minLevel = 3),
        Template("drones", Stat.DRONES, { 3L + it / 6 }, { "Stomp $it drones in the Strider" }, "mech", minLevel = 4),
        Template("flips", Stat.FLIPS, { 15L + it }, { "Flip gravity $it times" }, "flip", minLevel = 4),
        Template("spins", Stat.SPINS, { 2L + it / 10 }, { "Spin the prize wheel $it times" }, "spin", minLevel = 5),
        Template("chips_run", Stat.BEST_CHIPS, { 250L + it * 15 }, { "Grab $it credit chips in one run" }, "credit", minLevel = 6, single = true),
    )

    fun daily(rnd: Random, level: Int, stats: Map<String, Long>): List<Mission> =
        TEMPLATES.filter { it.minLevel <= level }.shuffled(rnd).take(3).map { t ->
            val goal = t.goal(level)
            val credits = 200 + level * 30 + rnd.nextInt(6) * 10
            val cores = if (rnd.nextFloat() < 0.3f) 1 else 0
            Mission(t.id, t.stat, goal, t.text(goal), t.icon, credits, cores, stats[t.stat] ?: 0L, single = t.single)
        }
}

/** A medal: reach [goal] on [stat] once, for [cores]. [unlock] is a reward-only item it hands over. */
data class Medal(val id: String, val title: String, val desc: String, val icon: String, val stat: String, val goal: Long, val cores: Int, val unlock: String? = null)

val MEDALS = listOf(
    Medal("first_run", "Breakout", "Finish your first run", "runner", Stat.RUNS, 1, 1),
    Medal("best_1k", "Out the door", "Run 1,000 m in one go", "flag", Stat.BEST_DISTANCE, 1000, 2),
    Medal("best_3k", "Long haul", "Run 3,000 m in one go", "flag", Stat.BEST_DISTANCE, 3000, 5, "color:venom"),
    Medal("best_6k", "Marathon thief", "Run 6,000 m in one go", "flag", Stat.BEST_DISTANCE, 6000, 10, "trail:phoenix"),
    Medal("best_10k", "Gone for good", "Run 10,000 m in one go", "flag", Stat.BEST_DISTANCE, 10000, 20, "title:the_last_light"),
    Medal("dist_50k", "Frequent runner", "Run 50 km in total", "distance", Stat.DISTANCE, 50_000, 4),
    Medal("dist_500k", "Across the foundry", "Run 500 km in total", "distance", Stat.DISTANCE, 500_000, 12, "finish:FROST"),
    Medal("chips_5k", "Sticky fingers", "Grab 5,000 credit chips", "credit", Stat.CHIPS, 5000, 3),
    Medal("chips_50k", "Embezzler", "Grab 50,000 credit chips", "credit", Stat.CHIPS, 50_000, 10, "decal:STARS"),
    Medal("near_250", "Close shave", "Scrape past 250 hazards", "graze", Stat.NEAR_MISSES, 250, 3),
    Medal("near_2500", "Untouchable", "Scrape past 2,500 hazards", "graze", Stat.NEAR_MISSES, 2500, 10, "burst:implode"),
    Medal("zap_1000", "Grounded", "Slip past 1,000 zappers", "zap", Stat.ZAPPERS, 1000, 5),
    Medal("missile_200", "Heat seeker", "Dodge 200 missiles", "missile", Stat.MISSILES, 200, 5, "trail:voidwake"),
    Medal("laser_100", "Light footed", "Outlast 100 laser sweeps", "laser", Stat.LASERS, 100, 5, "finish:PRISM"),
    Medal("rigs_50", "Joyrider", "Ride 50 rigs", "mech", Stat.RIGS, 50, 5, "color:royal"),
    Medal("drones_100", "Scrap metal", "Stomp 100 drones", "mech", Stat.DRONES, 100, 6, "burst:star"),
    Medal("flips_500", "Which way is up", "Flip gravity 500 times", "flip", Stat.FLIPS, 500, 5, "decal:RUNES"),
    Medal("air_1500", "Never landed", "Fly 1,500 m without touching the floor", "boost", Stat.BEST_AIR, 1500, 8),
    Medal("dry_2000", "Clean hands", "Run 2,000 m without grabbing a credit", "ghost", Stat.BEST_DRY, 2000, 8, "title:heartbreaker"),
    Medal("spins_50", "High roller", "Spin the prize wheel 50 times", "spin", Stat.SPINS, 50, 5),
    Medal("upgrades_10", "Tinkerer", "Buy 10 upgrades", "wrench", Stat.UPGRADES, 10, 4),
    Medal("bought_20", "Collector", "Buy 20 things in the shop", "shop", Stat.BOUGHT, 20, 5),
    Medal("missions_25", "Dutiful", "Finish 25 daily missions", "missions", Stat.MISSIONS, 25, 6),
    Medal("streak_7", "Regular", "Run 7 days in a row", "calendar", Stat.STREAK, 7, 5),
)
