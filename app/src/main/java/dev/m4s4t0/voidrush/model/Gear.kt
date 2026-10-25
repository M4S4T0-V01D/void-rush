package dev.m4s4t0.voidrush.model

private fun cr(amount: Int, level: Int) = Price(amount, Currency.CREDITS, level)
private fun co(amount: Int, level: Int) = Price(amount, Currency.CORES, level)
private fun sh(amount: Int, level: Int) = Price(amount, Currency.SHARDS, level)
private val FREE = Price(0)

/**
 * The thruster on the runner's back. Each flies the same (hold to climb) but looks and burns differently; [style] picks
 * the mesh and the exhaust (0 single nozzle, 1 twin boosters, 2 ring drive, 3 fins, 4 void core, 5 wings).
 */
enum class Pack(val title: String, val desc: String, val style: Int, val price: Price) {
    PROTO("Prototype", "The gravity thruster you stole. Still has the asset tag.", 0, FREE),
    TWIN("Twin boosters", "Two nozzles, twice the noise", 1, cr(2500, 3)),
    RING("Ring drive", "A halo of thrust", 2, cr(6000, 7)),
    FINS("Fin pack", "Swept fins that throw sparks", 3, cr(11000, 12)),
    WINGS("Ion wings", "Folding wings on a whisper of ions", 5, co(30, 18)),
    VOID("Void core", "A captured singularity. Don't ask.", 4, sh(60, 25));

    val key get() = "pack:$name"
}

/**
 * The rides that rig tokens hand out. Each takes one hit for the runner and then breaks apart.
 *  - [STRIDER] a stomping mech: hold to jump, lands on drones.
 *  - [FLIP] gravity boots: tap to flip between floor and ceiling.
 *  - [BLINK] a blink drive: tap to jump to the mirrored height across the corridor.
 *  - [EEL] a void eel: hold to rise, release to dive; it pulls credits in.
 *  - [GLIDER] a glider: hold to climb gently, release to glide.
 */
enum class Rig(val title: String, val desc: String, val icon: String, val price: Price) {
    STRIDER("Strider mech", "Hold to jump. Stomps drones flat.", "mech", FREE),
    FLIP("Flip boots", "Tap to flip between floor and ceiling", "flip", FREE),
    GLIDER("Glider", "Hold to climb, let go to glide", "wing", cr(4000, 4)),
    BLINK("Blink drive", "Tap to blink to the other side of the corridor", "blink", cr(9000, 9)),
    EEL("Void eel", "Hold to rise, let go to dive. Pulls credits in.", "eel", co(25, 15));

    val key get() = "rig:$name"
}

/** Up to [Loadout.GADGET_SLOTS] of these ride along on every run. */
enum class Gadget(val title: String, val desc: String, val icon: String, val price: Price) {
    MAGNET("Credit magnet", "Pulls nearby credits in", "magnet", cr(1500, 2)),
    FEATHER("Feather vanes", "Falls more gently", "wing", cr(2200, 3)),
    ANVIL("Anvil boots", "Drops faster for sharper dodges", "anvil", cr(2200, 3)),
    RADAR("Rig radar", "Rig tokens show up more often", "rig", cr(4500, 6)),
    LUCKY("Lucky chip", "More spin tokens", "spin", cr(6000, 8)),
    DAREDEVIL("Daredevil", "Near misses pay 5 credits", "graze", cr(8000, 11)),
    LENS("Prism lens", "Credits are worth 20% more", "credit", co(20, 14)),
    SECOND("Second wind", "The first revive of a run is free", "heart", sh(40, 20));

    val key get() = "gadget:$name"
}

/** Levelled up with credits; [max] levels, each a little stronger. */
enum class Upgrade(val title: String, val desc: String, val icon: String, val max: Int, val base: Int) {
    MAGNET("Magnet reach", "Wider pull for the credit magnet and the eel", "magnet", 5, 900),
    RIGS("Rig armour", "Rigs throw out a shield when they break", "shield", 5, 1200),
    VALUE("Credit value", "Every credit chip is worth 5% more", "credit", 5, 1500),
    THRUST("Thruster tuning", "Snappier climbs and drops", "boost", 5, 1100);

    val key get() = "upgrade:$name"
    /** What the next level costs, or null at the top. */
    fun cost(level: Int) = if (level >= max) null else base * (level + 1) * (level + 2) / 2
}

/** Bought in stacks; one is used at the start of a run if the runner arms it. */
enum class Boost(val title: String, val desc: String, val icon: String, val price: Int) {
    HEADSTART("Head start", "Blast 750 m down the corridor at the start", "boost", 900),
    BUBBLE("Shield bubble", "Start the run with a bubble that takes one hit", "shield", 600),
    MEGA("Mega head start", "Blast 2,000 m down the corridor", "next", 2400);

    val key get() = "boost:$name"
}

/** Everything the runner goes out with. */
data class Loadout(
    val paint: Paint = Paint(),
    val pack: Pack = Pack.PROTO,
    val gadgets: List<Gadget> = emptyList(),
) {
    fun has(g: Gadget) = g in gadgets

    companion object {
        const val GADGET_SLOTS = 2
    }
}

/** What the end-of-run spin can land on. */
data class Prize(val title: String, val icon: String, val credits: Int = 0, val cores: Int = 0, val shards: Int = 0, val boost: Boost? = null, val revive: Boolean = false)

object Spin {
    /** The wheel, with weights. */
    val WHEEL: List<Pair<Prize, Int>> = listOf(
        Prize("250 credits", "credit", credits = 250) to 22,
        Prize("600 credits", "credit", credits = 600) to 16,
        Prize("1,500 credits", "credit", credits = 1500) to 7,
        Prize("A core", "core", cores = 1) to 12,
        Prize("3 cores", "core", cores = 3) to 4,
        Prize("5 prism shards", "shard", shards = 5) to 10,
        Prize("Head start", "boost", boost = Boost.HEADSTART) to 9,
        Prize("Shield bubble", "shield", boost = Boost.BUBBLE) to 9,
        Prize("Free revive", "heart", revive = true) to 6,
        Prize("4,000 credits", "credit", credits = 4000) to 2,
    )

    fun roll(r: kotlin.random.Random): Int {
        var n = r.nextInt(WHEEL.sumOf { it.second })
        for ((i, w) in WHEEL.withIndex()) {
            n -= w.second
            if (n < 0) return i
        }
        return 0
    }
}

data class Reward(val credits: Int, val xp: Int, val cores: Int, val shards: Int = 0) {
    operator fun plus(o: Reward) = Reward(credits + o.credits, xp + o.xp, cores + o.cores, shards + o.shards)
}

object Levels {
    const val MAX = 100

    fun need(level: Int) = 100 + (level - 1) * 60 + (level - 1) * (level - 1) * 3 / 2

    /** (level, XP into it, XP it needs). */
    fun of(xp: Long): Triple<Int, Long, Long> {
        var lvl = 1
        var left = xp
        while (lvl < MAX && left >= need(lvl)) {
            left -= need(lvl)
            lvl++
        }
        return Triple(lvl, left, need(lvl).toLong())
    }
}
