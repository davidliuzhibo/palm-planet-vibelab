package com.vrgames.planet.core

object Stages {
    val ACTS = listOf("第一章 · 有了生命", "第二章 · 有了城市", "第三章 · 走出星球", "第四章 · 更多的可能")

    private fun erupt(at: Double) = Event(at, Happening.ERUPTION)
    private fun cold(at: Double) = Event(at, Happening.COLD)
    private fun comet(at: Double) = Event(at, Happening.COMET)

    val ALL = listOf(
        Stage("a1", 0, "第一片森林", "先在右边的面板上拿起「森林」，再看向星球上绿色的陆地，捏一下放下。种三片。捏住星球拖动可以转它。", 11, 0.5, 0, 40.0, 0.0, 6, listOf(GoalKind.FORESTS to 3), emptyList(), 100.0, 40.0),
        Stage("a2", 0, "第一个村庄", "村庄旁边要有森林，才能长成城市。造两个村庄，让人口到 10。", 12, 0.5, 0, 40.0, 0.0, 6, listOf(GoalKind.PEOPLE to 10), emptyList(), 112.5, 45.0),
        Stage("a3", 0, "海里的生命", "捏一块海洋，放下鲸群。它们和森林一样吸收碳。", 13, 0.65, 0, 50.0, 0.0, 6, listOf(GoalKind.WHALES to 2, GoalKind.CO2_BELOW to 45), emptyList(), 125.0, 50.0),
        Stage("a4", 0, "火山醒了", "火山喷发会让二氧化碳冲高、气温上升。提前多种树。", 14, 0.5, 2, 40.0, 0.0, 6, listOf(GoalKind.PEOPLE to 15, GoalKind.STABLE to 30), listOf(erupt(35.0)), 150.0, 60.0),
        Stage("b1", 1, "城市要电", "到了「村镇」时代就能造风电场。城市要用电，没电不长。人口到 65 要两座城市。", 21, 0.5, 0, 40.0, 0.0, 6, listOf(GoalKind.PEOPLE to 65), emptyList(), 200.0, 80.0),
        Stage("b2", 1, "越来越热", "城市烧得多，气温往上走。一边发展，一边种树。", 22, 0.5, 1, 45.0, 2.0, 6, listOf(GoalKind.PEOPLE to 60, GoalKind.STABLE to 30), listOf(erupt(60.0)), 225.0, 90.0),
        Stage("b3", 1, "寒潮", "太阳变弱，寒潮还会再来一次。这里反而要让气温高一点。", 23, 0.6, 0, 40.0, -3.0, 6, listOf(GoalKind.PEOPLE to 40, GoalKind.STABLE to 30), listOf(cold(50.0)), 150.0, 60.0),
        Stage("b4", 1, "海洋世界", "陆地很少。一块地也不能浪费，海里放鲸群。", 24, 0.72, 0, 45.0, 0.0, 6, listOf(GoalKind.PEOPLE to 40, GoalKind.WHALES to 3), emptyList(), 300.0, 120.0),
        Stage("c1", 2, "彗星", "彗星会砸坏地上的东西。卫星能提前预警，伤害减半。", 31, 0.5, 1, 40.0, 0.0, 6, listOf(GoalKind.PEOPLE to 70, GoalKind.STABLE to 30), listOf(comet(50.0), erupt(90.0)), 325.0, 130.0),
        Stage("c2", 2, "干旱行星", "海少，气候变得很快。慢一点，稳一点。", 32, 0.3, 1, 50.0, 1.0, 6, listOf(GoalKind.PEOPLE to 70, GoalKind.CO2_BELOW to 55), listOf(erupt(70.0)), 300.0, 120.0),
        Stage("c3", 2, "航天时代", "人口到 150 就进入「航天」时代。造一枚火箭，点火。", 33, 0.5, 0, 40.0, 0.0, 6, listOf(GoalKind.ROCKET to 1), listOf(comet(80.0)), 600.0, 240.0),
        Stage("c4", 2, "走出星球", "所有事一起来：火山、寒潮、彗星。把文明送上太空。", 34, 0.5, 2, 45.0, 1.0, 6, listOf(GoalKind.ROCKET to 1, GoalKind.STABLE to 30), listOf(erupt(60.0), cold(110.0), comet(150.0)), 575.0, 230.0),
        // 2026-11-12: the newer things to place, one level each.
        Stage("d1", 3, "鱼米之乡", "农田让旁边的村庄长得快一半；红树林长在海边，吸碳比森林还多。造两片农田、两片红树林，人口到 40。", 41, 0.55, 0, 45.0, 0.0, 8, listOf(GoalKind.FARMS to 2, GoalKind.MANGROVES to 2, GoalKind.PEOPLE to 40), emptyList(), 300.0, 130.0),
        Stage("d2", 3, "珊瑚的海", "珊瑚礁带来造物点，可海水一热就会白化死去。守住三片珊瑚礁，人口到 30，气候稳定 40 秒。", 42, 0.65, 0, 45.0, 1.0, 8, listOf(GoalKind.REEFS to 3, GoalKind.PEOPLE to 30, GoalKind.STABLE to 40), emptyList(), 300.0, 140.0),
        Stage("d3", 3, "雪山的电", "城市要很多电。水电站只能建在雪山上，电最多；太阳能便宜。建一座水电站，人口到 120。", 43, 0.45, 1, 40.0, 0.0, 8, listOf(GoalKind.HYDRO to 1, GoalKind.PEOPLE to 120), listOf(erupt(140.0)), 520.0, 230.0),
    )

    fun byId(id: String) = ALL.first { it.id == id }
}
