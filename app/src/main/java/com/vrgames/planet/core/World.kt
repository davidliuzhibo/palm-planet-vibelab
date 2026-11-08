package com.vrgames.planet.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Terrain { OCEAN, LAND, MOUNTAIN, VOLCANO }

/** A little real science behind the game, for the result card and the narrator (narration/<id>.ogg). */
class Lore(val id: String, val title: String, val text: String)

object Science {
    val LORE = listOf(
        Lore("forest", "森林和碳", "森林是地球的碳库。树木吸收二氧化碳，把碳存进木头和土壤。全世界的陆地生态系统，每年大约吸收人类排放的三成。这也是砍伐森林会让气候更快变暖的原因。"),
        Lore("whale", "鲸鱼的碳泵", "鲸鱼也能帮地球固碳。它们的粪便富含铁和氮，能养活海面的浮游植物，而浮游植物吸收二氧化碳。一头大鲸死后沉入海底，体内的碳也一起被带到了深海。"),
        Lore("volcano", "火山和气候", "火山喷发会放出二氧化碳，也会把灰尘和硫酸盐送上高空，挡住阳光，让全球短暂变冷。1991 年菲律宾皮纳图博火山喷发后，全球平均气温下降了大约半度。"),
        Lore("rocket", "走出地球", "要绕着地球飞，火箭得达到每秒约七点九公里的第一宇宙速度。人类第一颗人造卫星在 1957 年升空。今天的气象卫星能提前发现寒潮和台风，让我们更早做好准备。"),
    )
    fun of(stageIndex: Int) = LORE[((stageIndex % LORE.size) + LORE.size) % LORE.size]
}

/** What can stand on a tile. */
enum class Build(val title: String, val cost: Int, val onOcean: Boolean, val needs: Era, val line: String) {
    FOREST("森林", 2, false, Era.TRIBE, "吸收二氧化碳，让村庄长成城市。"),
    VILLAGE("村庄", 3, false, Era.TRIBE, "有人了。旁边有森林，能源够，就会长成城市。"),
    WHALE("鲸群", 3, true, Era.TRIBE, "海里的生命。鲸鱼的粪便养活浮游植物，它们也吸收碳。"),
    WIND("风电场", 4, false, Era.TOWN, "干净的电。城市要用电，没有电不长人。"),
    DOME("穹顶基地", 6, false, Era.CITY, "封闭生态，不怕冷热，要用电。"),
    SATELLITE("卫星", 5, true, Era.CITY, "提前报告火山和寒潮，事件的伤害减半。"),
    ROCKET("火箭", 12, false, Era.SPACE, "文明走出这颗星球。要有人口，气候也要稳。"),
}

/** How far a civilization has come, by its people. */
enum class Era(val title: String, val people: Int) {
    TRIBE("部落", 0), TOWN("村镇", 15), CITY("城市", 60), SPACE("航天", 150);
}

class Tile(val id: Int, val x: Double, val y: Double, val z: Double, var terrain: Terrain) {
    var build: Build? = null
    /** Grown from a village. */
    var city = false
    var grow = 0.0
    val neighbors = ArrayList<Int>()
    val free get() = build == null && terrain != Terrain.VOLCANO && terrain != Terrain.MOUNTAIN
}

enum class GoalKind { PEOPLE, FORESTS, WHALES, STABLE, ROCKET, CO2_BELOW }

class Goal(val kind: GoalKind, val n: Int, val text: String) {
    var met = false
}

/** Something that happens in a level at a given time. */
enum class Happening(val title: String, val warn: String) {
    ERUPTION("火山喷发", "火山要喷发了！二氧化碳会冲高。"),
    COLD("寒潮", "寒潮要来了！气温会往下掉一阵。"),
    COMET("彗星", "彗星要撞过来了！会毁掉一块地上的东西。"),
}

class Event(val at: Double, val what: Happening)

class Stage(
    val id: String,
    val act: Int,
    val title: String,
    val ask: String,
    val seed: Long,
    val oceanShare: Double,
    val volcanoes: Int,
    val startCo2: Double,
    /** The sun's strength as degrees added to the climate. */
    val sun: Double,
    val credits: Int,
    val goals: List<Pair<GoalKind, Int>>,
    val events: List<Event>,
    val limit: Double,
    val par: Double,
)

sealed interface Ev {
    data class Note(val text: String) : Ev
    data class Built(val tile: Int, val what: Build) : Ev
    data class Grew(val tile: Int) : Ev
    data class Refused(val why: String) : Ev
    data class Warning(val what: Happening) : Ev
    data class Struck(val what: Happening, val tile: Int) : Ev
    data class EraUp(val era: Era) : Ev
    data class Launch(val tile: Int) : Ev
    data object Goal : Ev
    data object TooHot : Ev
    data object TooCold : Ev
    data object Won : Ev
    data object Lost : Ev
}

/** One level: the tiles and the climate over them. */
class Play(val stage: Stage) {
    val tiles = ArrayList<Tile>()
    val goals = stage.goals.map { (k, n) -> Goal(k, n, text(k, n)) }
    var co2 = stage.startCo2; private set
    var temp = 0.0; private set
    var credits = stage.credits.toDouble(); private set
    var time = 0.0; private set
    var selected: Int? = null; private set
    var over = false; private set
    var won = false; private set
    var era = Era.TRIBE; private set
    /** Seconds the climate has been outside what people can live in; [COLLAPSE] ends the level. */
    var outside = 0.0; private set
    var stable = 0.0; private set
    var launched = false; private set
    private var cold = 0.0
    private var nextEvent = 0
    private val warned = HashSet<Int>()
    private val events = ArrayList<Ev>()
    private var warnedHot = false
    /** The last minutes of the climate, for the record window. */
    val history = ArrayList<Pair<Double, Double>>()
    private var historyClock = 0.0
    private val rnd = java.util.Random(stage.seed)

    init {
        // Tiles spread evenly over the sphere; a neat spiral.
        val golden = PI * (3 - sqrt(5.0))
        for (i in 0 until TILES) {
            val y = 1 - 2.0 * (i + 0.5) / TILES
            val r = sqrt(max(0.0, 1 - y * y))
            val a = golden * i
            tiles += Tile(i, cos(a) * r, y, sin(a) * r, Terrain.LAND)
        }
        for (t in tiles) t.neighbors += tiles.filter { it !== t }.sortedBy { dist(it, t) }.take(5).map { it.id }
        // Land grows in blobs from a few seeds; the rest is sea.
        val land = HashSet<Int>()
        val want = (TILES * (1 - stage.oceanShare)).toInt()
        val frontier = ArrayList<Int>()
        repeat(3) { val s = rnd.nextInt(TILES); land += s; frontier += s }
        while (land.size < want && frontier.isNotEmpty()) {
            val f = frontier[rnd.nextInt(frontier.size)]
            val open = tiles[f].neighbors.filter { it !in land }
            if (open.isEmpty()) { frontier.remove(f); continue }
            val n = open[rnd.nextInt(open.size)]
            land += n
            frontier += n
        }
        for (t in tiles) t.terrain = if (t.id in land) Terrain.LAND else Terrain.OCEAN
        val shuffled = land.toList().shuffled(rnd)
        shuffled.take(stage.volcanoes).forEach { tiles[it].terrain = Terrain.VOLCANO }
        shuffled.drop(stage.volcanoes).take(if (land.size < 12) 1 else 2).forEach { tiles[it].terrain = Terrain.MOUNTAIN }
        temp = targetTemp()
    }

    private fun dist(a: Tile, b: Tile) = acos((a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0))

    private fun text(k: GoalKind, n: Int) = when (k) {
        GoalKind.PEOPLE -> "人口达到 $n"
        GoalKind.FORESTS -> "$n 片森林"
        GoalKind.WHALES -> "$n 群鲸鱼"
        GoalKind.STABLE -> "气候稳定 $n 秒"
        GoalKind.ROCKET -> "发射火箭"
        GoalKind.CO2_BELOW -> "二氧化碳降到 $n 以下"
    }

    fun count(b: Build) = tiles.count { it.build == b }
    val cities get() = tiles.count { it.city }
    val villages get() = tiles.count { it.build == Build.VILLAGE && !it.city }
    val oceanTiles get() = tiles.count { it.terrain == Terrain.OCEAN }

    /** People: towns and domes hold them, more while the climate is kind. */
    val people: Int
        get() {
            val base = villages * 5 + cities * 30 + count(Build.DOME) * 15
            return base
        }

    /** Electricity: + from the wind, - to the towns. */
    val energy: Double get() = count(Build.WIND) * 3.0 - cities * 2.0 - count(Build.DOME) * 1.0

    val livable get() = temp in COLD_LIMIT..HOT_LIMIT

    /** Where the climate is heading: the greenhouse over the sun, and the cold snap if one is on. */
    fun targetTemp(): Double = 14 + stage.sun + 0.2 * (co2 - 40) - (if (cold > 0) 9.0 else 0.0)

    /** How fast the air's carbon moves per second. */
    fun co2Rate(): Double = cities * 0.3 + villages * 0.04 + count(Build.DOME) * 0.03 - count(Build.FOREST) * 0.14 - count(Build.WHALE) * 0.1

    fun canBuild(b: Build, t: Tile): String? {
        if (!t.free) return if (t.terrain == Terrain.VOLCANO) "这里是火山，什么也放不了。" else if (t.terrain == Terrain.MOUNTAIN) "雪山上放不了东西。" else "这里已经有${t.build?.title}了。"
        if (t.terrain == Terrain.OCEAN && !b.onOcean) return "${b.title}要放在陆地上。"
        if (t.terrain != Terrain.OCEAN && b == Build.WHALE) return "鲸鱼要放在海里。"
        if (era.ordinal < b.needs.ordinal) return "${b.title}要到「${b.needs.title}」时代才有。"
        if (credits < b.cost) return "造物点不够：要 ${b.cost}，现在有 ${credits.toInt()}。"
        if (b == Build.ROCKET && !livable) return "气候不稳，火箭不能发射。"
        return null
    }

    /** A pinch on a tile: the player's eye goes there. */
    fun select(id: Int) {
        if (!over && id in tiles.indices) selected = id
    }

    fun build(b: Build, id: Int? = selected): Boolean {
        if (over) return false
        val t = tiles.getOrNull(id ?: -1)
        if (t == null) {
            events += Ev.Refused("先捏一块地方。")
            return false
        }
        canBuild(b, t)?.let {
            events += Ev.Refused(it)
            return false
        }
        credits -= b.cost
        t.build = b
        events += Ev.Built(t.id, b)
        if (b == Build.ROCKET) {
            launched = true
            events += Ev.Launch(t.id)
        }
        return true
    }

    /** Takes down what stands on the selected tile: half the cost back. */
    fun clear(id: Int? = selected) {
        val t = tiles.getOrNull(id ?: -1) ?: return
        val b = t.build ?: return
        if (over) return
        credits += b.cost / 2.0
        t.build = null
        t.city = false
        t.grow = 0.0
    }

    fun step(dt0: Double, between: (() -> Unit)? = null): List<Ev> {
        var left = dt0
        while (left > 1e-9 && !over) {
            val dt = min(left, STEP)
            between?.invoke()
            tick(dt)
            left -= dt
        }
        return events.toList().also { events.clear() }
    }

    private fun tick(dt: Double) {
        time += dt
        if (cold > 0) cold -= dt
        credits = min(99.0, credits + dt * (0.3 + people / 300.0))
        co2 = (co2 + co2Rate() * dt).coerceIn(5.0, 140.0)
        // A sea holds heat: the more ocean, the slower the climate turns.
        val inertia = 6 + 16 * oceanTiles.toDouble() / TILES
        temp += (targetTemp() - temp) * dt / inertia
        grow(dt)
        happenings()
        climate(dt)
        record(dt)
        check()
        if (!over && time >= stage.limit) {
            over = true
            events += Ev.Note("时间到了，这颗星球没能走到那一步。")
            events += Ev.Lost
        }
    }

    private fun grow(dt: Double) {
        for (t in tiles) {
            if (t.build != Build.VILLAGE || t.city) continue
            val woods = t.neighbors.any { tiles[it].build == Build.FOREST }
            if (woods && energy >= 0 && livable) {
                t.grow += dt
                if (t.grow >= GROW_S) {
                    t.city = true
                    events += Ev.Grew(t.id)
                }
            }
        }
        val before = era
        era = Era.entries.last { people >= it.people }
        if (era.ordinal > before.ordinal) events += Ev.EraUp(era)
    }

    private fun happenings() {
        val evs = stage.events
        while (nextEvent < evs.size && time >= evs[nextEvent].at) {
            strike(evs[nextEvent].what)
            nextEvent++
        }
        // A satellite gives warning; so does the record of the sky, a moment before.
        val warnAhead = if (count(Build.SATELLITE) > 0) 12.0 else 4.0
        evs.forEachIndexed { k, e ->
            if (k !in warned && e.at - time in 0.0..warnAhead) {
                warned += k
                events += Ev.Warning(e.what)
            }
        }
    }

    private fun strike(h: Happening) {
        val guard = if (count(Build.SATELLITE) > 0) 0.5 else 1.0
        when (h) {
            Happening.ERUPTION -> {
                co2 += 14 * guard
                val v = tiles.firstOrNull { it.terrain == Terrain.VOLCANO } ?: tiles[0]
                events += Ev.Struck(h, v.id)
            }
            Happening.COLD -> {
                cold = 30.0 * guard
                events += Ev.Struck(h, tiles[0].id)
            }
            Happening.COMET -> {
                val built = tiles.filter { it.build != null && it.build != Build.WHALE }
                val hit = if (built.isEmpty()) tiles[rnd.nextInt(TILES)] else built[rnd.nextInt(built.size)]
                hit.build = null
                hit.city = false
                hit.grow = 0.0
                events += Ev.Struck(h, hit.id)
            }
        }
    }

    private fun climate(dt: Double) {
        val hot = temp > HOT_LIMIT
        val freezing = temp < COLD_LIMIT
        if (hot || freezing) {
            if (people > 0) outside += dt
            if (!warnedHot) {
                warnedHot = true
                events += if (hot) Ev.TooHot else Ev.TooCold
            }
            stable = 0.0
            if (outside >= COLLAPSE) {
                over = true
                events += Ev.Note(if (hot) "太热了，文明撑不下去了。" else "太冷了，文明撑不下去了。")
                events += Ev.Lost
            }
        } else {
            warnedHot = false
            outside = max(0.0, outside - dt * 0.5)
            stable += dt
        }
    }

    private fun record(dt: Double) {
        historyClock -= dt
        if (historyClock <= 0) {
            historyClock = 2.0
            history += temp to co2
            if (history.size > 90) history.removeAt(0)
        }
    }

    private fun check() {
        for (g in goals) {
            val now = when (g.kind) {
                GoalKind.PEOPLE -> people >= g.n
                GoalKind.FORESTS -> count(Build.FOREST) >= g.n
                GoalKind.WHALES -> count(Build.WHALE) >= g.n
                GoalKind.STABLE -> stable >= g.n && people > 0
                GoalKind.ROCKET -> launched
                GoalKind.CO2_BELOW -> co2 < g.n
            }
            if (now && !g.met) {
                g.met = true
                events += Ev.Goal
            }
        }
        if (goals.all { it.met }) {
            over = true
            won = true
            events += Ev.Won
        }
    }

    fun stars(): Int {
        if (!won) return 0
        return 1 + (if (time <= stage.par) 1 else 0) + (if (outside < 0.1 && maxOutside() == 0.0) 1 else 0)
    }

    private var worst = 0.0
    private fun maxOutside(): Double {
        worst = max(worst, outside)
        return worst
    }

    companion object {
        const val TILES = 36
        const val STEP = 0.1
        const val GROW_S = 20.0
        const val HOT_LIMIT = 24.0
        const val COLD_LIMIT = 6.0
        const val COLLAPSE = 30.0
    }
}

/** Plays a level as a careful governor would: power first, then air, then people. */
class Governor(private val p: Play) {
    private var wait = 0.0

    fun update(dt: Double) {
        if (p.over) return
        wait -= dt
        if (wait > 0) return
        wait = 0.5
        val need = what() ?: return
        // Saves up for what is needed, rather than spend on something else.
        if (p.credits < need.cost) return
        val spot = spot(need) ?: return
        if (p.canBuild(need, spot) == null) p.build(need, spot.id)
    }

    private fun what(): Build? {
        val want = { k: GoalKind -> p.goals.firstOrNull { it.kind == k && !it.met } }
        val rocket = want(GoalKind.ROCKET) != null
        if (rocket && p.era == Era.SPACE && p.livable) return Build.ROCKET
        want(GoalKind.WHALES)?.let { return Build.WHALE }
        want(GoalKind.FORESTS)?.let { return Build.FOREST }
        // A village with no woods beside it can never grow: woods first.
        val lonely = p.tiles.any { t -> t.build == Build.VILLAGE && !t.city && t.neighbors.none { p.tiles[it].build == Build.FOREST } && t.neighbors.any { p.tiles[it].free && p.tiles[it].terrain == Terrain.LAND } }
        if (lonely) return Build.FOREST
        // The air: whales before woods, the land is for towns.
        if (p.co2Rate() > 0.02 || p.temp > 19 || want(GoalKind.CO2_BELOW) != null) {
            return if (p.tiles.any { it.free && it.terrain == Terrain.OCEAN }) Build.WHALE else Build.FOREST
        }
        // Keep power ahead of the villages about to become cities.
        val growing = p.tiles.count { it.build == Build.VILLAGE && !it.city }
        if (p.era.ordinal >= Era.TOWN.ordinal && p.energy < 2 && growing > 0) return Build.WIND
        if (p.tiles.none { it.build == Build.SATELLITE } && p.era.ordinal >= Era.CITY.ordinal && p.stage.events.isNotEmpty()) return Build.SATELLITE
        if (p.temp < 9) return null
        val pending = want(GoalKind.PEOPLE) != null || rocket
        if (!pending) return null
        // Out of open land for another village: a dome holds people on no woods.
        val spare = p.tiles.count { it.free && it.terrain == Terrain.LAND }
        return if (spare == 0 && p.era.ordinal >= Era.CITY.ordinal) null else Build.VILLAGE
    }

    /** Villages beside woods, woods beside villages. */
    private fun spot(b: Build): Tile? {
        val open = p.tiles.filter { it.free && (if (b.onOcean && b == Build.WHALE) it.terrain == Terrain.OCEAN else if (b.onOcean) true else it.terrain == Terrain.LAND) }
        if (open.isEmpty()) return null
        return when (b) {
            Build.VILLAGE -> open.maxByOrNull { t -> t.neighbors.count { p.tiles[it].build == Build.FOREST } * 2 - t.neighbors.count { p.tiles[it].build == Build.VILLAGE } }
            Build.FOREST -> open.maxByOrNull { t -> t.neighbors.count { n -> p.tiles[n].build == Build.VILLAGE && p.tiles[n].neighbors.none { p.tiles[it].build == Build.FOREST } } * 3 + t.neighbors.count { p.tiles[it].free && p.tiles[it].terrain == Terrain.LAND } }
            else -> open.first()
        }
    }
}
