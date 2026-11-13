package com.vrgames.planet.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.vrgames.planet.GUIDE_ID
import com.vrgames.planet.LEVELS_ID
import com.vrgames.planet.core.Play
import com.vrgames.planet.core.Stages
import com.vrgames.planet.game.Game

private val CardBg = Color(0x26EFF4FF)

/** 关卡: every level with its stars (pick one to play), and the three trophies. */
@Composable
fun LevelsWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WindowHeader(LEVELS_ID, "关卡", 32.sp)
        Text("总星数 ${Game.stars} / ${Game.maxStars}。点一关就能玩。", fontSize = 22.sp, color = Ink)
        Text(
            (if (Game.bronze) "🥉 铜奖杯：十二关全过　" else "铜奖杯：十二关全过（未得）　") +
                (if (Game.silver) "🥈 银奖杯　" else "银奖杯：三分之二的星（未得）　") +
                (if (Game.gold) "🥇 金奖杯：群星之夜" else "金奖杯：全部三星（未得）"),
            fontSize = 21.sp, color = Mint,
        )
        for ((k, s) in Stages.ALL.withIndex()) {
            if (k == 0 || Stages.ALL[k - 1].act != s.act) Text(Stages.ACTS[s.act], fontSize = 25.sp, color = Sky)
            val open = Game.open(s)
            val st = Game.best[s.id] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg)
                    .clickable(enabled = open) { Game.closeWindow(LEVELS_ID); Game.intro(s) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${k + 1}. ${s.title}", fontSize = 24.sp, color = if (open) Ink else Dim)
                Spacer(Modifier.weight(1f))
                Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 23.sp, color = Star)
            }
        }
    }
}

/** A line of the climate record. */
@Composable
private fun Curve(values: List<Double>, lo: Double, hi: Double, tint: Color, band: Pair<Double, Double>? = null) {
    Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))) {
        fun y(v: Double) = size.height * (1 - ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)).toFloat()
        band?.let { (a, b) ->
            drawRect(Color(0x228FE3B0), topLeft = Offset(0f, y(b)), size = androidx.compose.ui.geometry.Size(size.width, y(a) - y(b)))
        }
        if (values.size >= 2) for (i in 1 until values.size) {
            val x0 = size.width * (i - 1) / (values.size - 1)
            val x1 = size.width * i / (values.size - 1)
            drawLine(tint, Offset(x0, y(values[i - 1])), Offset(x1, y(values[i])), strokeWidth = 4f)
        }
    }
}

/** 星球档案: the record of this planet's climate as curves, and why it goes the way it goes. */
@Composable
fun GuideWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(GUIDE_ID, "星球档案", 32.sp)
        val h = Game.play?.history.orEmpty().toList()
        Text("气温（绿色带是能住人的范围）", fontSize = 22.sp, color = Sky)
        Curve(h.map { it.first }, -5.0, 35.0, Star, Play.COLD_LIMIT to Play.HOT_LIMIT)
        Text("二氧化碳", fontSize = 22.sp, color = Sky)
        Curve(h.map { it.second }, 0.0, 120.0, Sky)
        for ((t, d) in GUIDE) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
                Text(t, fontSize = 24.sp, color = Star)
                Text(d, fontSize = 20.sp, color = Ink)
            }
        }
    }
}

private val GUIDE = listOf(
    "怎么玩" to "先在右边面板上拿起想造的东西，再看向星球捏一下放下（绿圈能放，红圈不能），拿着的东西可以连放几处。捏住星球拖动、或捏住后转动手腕，星球跟着转；面板上的箭头也能转。",
    "温室效应" to "二氧化碳让热量跑不出去。这里二氧化碳每多 5，气温就升 1℃。地球大气里的二氧化碳工业革命后多了约一半。",
    "碳循环" to "森林和海里的浮游植物吸收碳，城市排放碳。两边打平，气温就稳了。一棵成年树一年大约吸收 20 公斤二氧化碳。",
    "海洋" to "水升温很慢，所以海越多，气候变化越慢。海洋吸收了地球多余热量的九成。这就是干旱行星那么难的原因。",
    "火山" to "火山喷发放出二氧化碳，让气温慢慢升。真实的大喷发还会喷出尘埃，反而先冷几年。",
    "时代" to "人口 15 进入村镇时代（风电场），60 进入城市时代（穹顶、卫星），150 进入航天时代（火箭）。",
    "失控" to "气温低于 6℃ 或高于 24℃ 就是失控。连续撑不过 30 秒，文明就中断了。",
    "星星" to "完成一星；在三星时间内完成再加一星；气候一次也没失控再加一星。",
    "奖杯" to "十二关全过：铜奖杯。拿到三分之二的星：银奖杯。全部三星：金奖杯，解锁「群星之夜」。",
)
