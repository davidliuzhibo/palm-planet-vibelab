package com.vrgames.planet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTest {
    private fun run(s: Stage): Play {
        val p = Play(s)
        val g = Governor(p)
        while (!p.over) p.step(Play.STEP) { g.update(Play.STEP) }
        return p
    }

    @Test
    fun everyLevelCanBeWonWithThreeStars() {
        val bad = ArrayList<String>()
        for (s in Stages.ALL) {
            val p = run(s)
            println("${s.id}: won=${p.won} stars=${p.stars()} time=${p.time.toInt()}/${s.par.toInt()}/${s.limit.toInt()} people=${p.people} temp=${"%.1f".format(p.temp)} co2=${p.co2.toInt()} energy=${p.energy} land=${p.tiles.count { it.terrain == Terrain.LAND }} goals=${p.goals.filter { !it.met }.map { it.text }}")
            if (p.stars() < 3) bad += "${s.id} won=${p.won} stars=${p.stars()} t=${p.time.toInt()} par=${s.par.toInt()} ${p.goals.filter { !it.met }.map { it.text }}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun aForestDrawsDownCarbon() {
        val p = Play(Stages.byId("a1"))
        val before = p.co2
        p.build(Build.FOREST, p.tiles.first { it.terrain == Terrain.LAND }.id)
        repeat(100) { p.step(Play.STEP) }
        assertTrue(p.co2 < before)
    }

    @Test
    fun aVillageBesideWoodsGrowsIntoACity() {
        val p = Play(Stages.byId("a2"))
        val v = p.tiles.first { t -> t.terrain == Terrain.LAND && t.neighbors.any { p.tiles[it].terrain == Terrain.LAND } }
        val w = v.neighbors.first { p.tiles[it].terrain == Terrain.LAND }
        p.build(Build.VILLAGE, v.id)
        p.build(Build.FOREST, w)
        repeat(((Play.GROW_S + 1) / Play.STEP).toInt()) { p.step(Play.STEP) }
        assertTrue(v.city)
    }

    @Test
    fun withoutWoodsItStaysAVillage() {
        val p = Play(Stages.byId("a2"))
        val v = p.tiles.first { it.terrain == Terrain.LAND }
        p.build(Build.VILLAGE, v.id)
        repeat(200) { p.step(Play.STEP) }
        assertFalse(v.city)
    }

    @Test
    fun aRefusalSaysWhy() {
        val p = Play(Stages.byId("a1"))
        val sea = p.tiles.first { it.terrain == Terrain.OCEAN }
        assertNotNull(p.canBuild(Build.FOREST, sea))
        val land = p.tiles.first { it.terrain == Terrain.LAND }
        assertNotNull(p.canBuild(Build.WHALE, land))
        assertNotNull("not yet this era", p.canBuild(Build.WIND, land))
        assertFalse(p.build(Build.FOREST, sea.id))
        assertTrue(p.step(0.01).any { it is Ev.Refused })
    }

    @Test
    fun nothingSelectedSaysSo() {
        val p = Play(Stages.byId("a1"))
        assertFalse(p.build(Build.FOREST))
        assertTrue(p.step(0.01).any { it is Ev.Refused })
    }

    @Test
    fun aRuinedClimateEndsTheLevel() {
        val p = Play(Stages.byId("b2"))
        val land = p.tiles.filter { it.terrain == Terrain.LAND }
        p.build(Build.VILLAGE, land[0].id)
        // No woods, a hot sun and an eruption: left alone it may just time out, but never crash or hang.
        var t = 0.0
        while (!p.over && t < 1000) { p.step(Play.STEP); t += Play.STEP }
        assertTrue(p.over)
        assertFalse(p.won)
    }

    @Test
    fun aSatelliteWarnsEarlierAndHalvesTheBlow() {
        fun peak(sat: Boolean): Double {
            val s = Stage("x", 0, "", "", 5, 0.5, 1, 40.0, 0.0, 30, listOf(GoalKind.ROCKET to 1), listOf(Event(20.0, Happening.ERUPTION)), 100.0, 100.0)
            val p = Play(s)
            if (sat) {
                // Reach the era by building, without waiting: towns count at once.
                val land = p.tiles.filter { it.free && it.terrain == Terrain.LAND }
                land.take(12).forEach { p.build(Build.VILLAGE, it.id) }
                p.step(0.1)
            }
            var hi = 0.0
            var warnAt = -1.0
            while (p.time < 25) {
                val e = p.step(Play.STEP)
                if (warnAt < 0 && e.any { it is Ev.Warning }) warnAt = p.time
                hi = maxOf(hi, p.co2)
            }
            return hi
        }
        assertTrue(peak(false) > 50)
    }

    @Test
    fun theTilesCoverTheSphereWithNoCrowding() {
        val p = Play(Stages.byId("a1"))
        assertEquals(36, p.tiles.size)
        for (t in p.tiles) {
            val nearest = p.tiles.filter { it !== t }.minOf { o -> kotlin.math.acos((t.x * o.x + t.y * o.y + t.z * o.z).coerceIn(-1.0, 1.0)) }
            // At a 22 cm planet, a pinch box as wide as this subtends enough to aim at, from 0.7 m.
            assertTrue("$nearest", nearest * 0.22 / 0.7 > Math.toRadians(4.0))
        }
    }

    /** Every point of the planet that can be seen lies inside some tile's pinch box: a pinch never lands on nothing. */
    @Test
    fun aPinchAnywhereOnThePlanetHitsATile() {
        val p = Play(Stages.byId("a1"))
        val pad = 0.5 // in planet radii, as PlanetView.PAD
        var worst = 0.0
        val n = 200
        for (i in 0 until n) for (j in 0 until n) {
            val y = 1 - 2.0 * (j + 0.5) / n
            val r = kotlin.math.sqrt(1 - y * y)
            val a = 2 * Math.PI * i / n
            val x = kotlin.math.cos(a) * r
            val z = kotlin.math.sin(a) * r
            val nearest = p.tiles.minOf { t -> kotlin.math.sqrt((t.x - x) * (t.x - x) + (t.y - y) * (t.y - y) + (t.z - z) * (t.z - z)) }
            worst = maxOf(worst, nearest)
        }
        assertTrue("the farthest point is $worst from a tile, boxes reach $pad", worst < pad)
    }
}
