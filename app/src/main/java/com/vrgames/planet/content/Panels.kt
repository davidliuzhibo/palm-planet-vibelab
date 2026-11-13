package com.vrgames.planet.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.planet.GUIDE_ID
import com.vrgames.planet.LEVELS_ID
import com.vrgames.planet.core.Build
import com.vrgames.planet.core.Play
import com.vrgames.planet.core.Terrain
import com.vrgames.planet.core.Stages
import com.vrgames.planet.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_RESULT = "result"
const val PANEL_SHOW = "show"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"
const val PANEL_BUILD = "build"

val Ink = Color(0xFFEFF4FF)
val Star = Color(0xFFFFD27A)
val Sky = Color(0xFF9CD3E8)
val Mint = Color(0xFF9FE3A8)
val Dim = Color(0xFFCFC4B2)
val Alarm = Color(0xFFFF7B5C)
private val PanelBg = Color(0xEA0E1228)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF20E1228)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.016f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0015f), Color(0x889FB8FF), shape).padding(c.m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.008f)),
    ) { content() }
}

/** The step that moves the game on. */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, color: Color = Star, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Text(
        text,
        Modifier.clip(shape).background(color).border(c.m(0.002f), Color(0xFFFFF4E0), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.024f), vertical = c.m(0.013f)),
        fontSize = c.sz(0.024f),
        color = Color(0xFF1A1420),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.014f), vertical = c.m(0.008f)),
        fontSize = c.sz(0.017f),
        color = color,
    )
}

/** Text on the smoked glass of the armillary stand (the frame model is added by the stage). */
@Composable
private fun Glass(c: PhysicalLengthConverter, width: Float, height: Float, content: @Composable () -> Unit) {
    Column(
        Modifier.width(c.m(width)).height(c.m(height)).padding(horizontal = c.m(0.018f), vertical = c.m(0.006f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.006f), Alignment.CenterVertically),
    ) { content() }
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

private fun clock(s: Double): String = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    Glass(c, 0.62f, 0.36f) {
        Game.version // Read in here: the same arguments, so the box would skip its content.
        Text("掌心星球", fontSize = c.sz(0.046f), color = Star)
        Text("一颗小星球在你面前。种下森林，放进鲸群，建起城市，看气候怎么变，最后把火箭送上天。", fontSize = c.sz(0.018f), color = Ink)
        Text("怎么玩", fontSize = c.sz(0.02f), color = Mint)
        Text("· 每一关要做什么，写在左边的面板上：玩的时候记得看左边。\n· 先在右边面板上拿起想造的东西。\n· 再看向星球捏一下放下：绿圈能放，红圈不能。\n· 捏住星球拖动，或捏住后转手腕，星球就跟着转。\n· 城市越多气温越高：一边发展，一边种树。", fontSize = c.sz(0.0165f), color = Ink)
        if (Game.stars > 0) Text("已得 ${Game.stars} / ${Game.maxStars} 颗星", fontSize = c.sz(0.016f), color = Mint)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, if (Game.stars > 0) "接着造 →" else "创世 →") { Game.start() }
            if (Game.gold) Pill(c, "群星之夜", Star) { Game.show() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    Glass(c, 0.58f, 0.34f) {
        Game.version
        val s = Game.stage
        Text("第 ${Stages.ALL.indexOf(s) + 1} 关 · ${s.title}", fontSize = c.sz(0.034f), color = Star)
        Text(Stages.ACTS[s.act], fontSize = c.sz(0.016f), color = Sky)
        Text(s.ask, fontSize = c.sz(0.0185f), color = Ink)
        Text("三星：${clock(s.par)} 内完成，气候一次也没有失控。", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, "开始 →") { Game.begin() }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

/** A row of a reading, with its bar. */
@Composable
private fun Reading(c: PhysicalLengthConverter, name: String, value: String, share: Float, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
        Text(name, Modifier.width(c.m(0.062f)), fontSize = c.sz(0.0145f), color = Dim)
        Box(Modifier.width(c.m(0.13f)).height(c.m(0.009f)).clip(RoundedCornerShape(c.m(0.004f))).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(tint))
        }
        Text(value, fontSize = c.sz(0.0155f), color = tint)
    }
}

@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    Glass(c, 0.35f, 0.34f) {
        Game.version
        val p = Game.play ?: return@Glass
        Text("第 ${Stages.ALL.indexOf(p.stage) + 1} 关 · ${p.stage.title}", fontSize = c.sz(0.02f), color = Star)
        Text("${p.era.title}时代　用时 ${clock(p.time)}", fontSize = c.sz(0.015f), color = Sky)
        val t = p.temp
        val tTint = if (t > Play.HOT_LIMIT || t < Play.COLD_LIMIT) Alarm else if (t > Play.HOT_LIMIT - 3 || t < Play.COLD_LIMIT + 3) Star else Mint
        Reading(c, "气温", "%.1f℃".format(t), ((t + 5) / 40).toFloat(), tTint)
        val rate = p.co2Rate()
        Reading(c, "二氧化碳", "${p.co2.toInt()} " + if (rate > 0.02) "↑" else if (rate < -0.02) "↓" else "→", (p.co2 / 120).toFloat(), if (p.co2 > 70) Alarm else Sky)
        Reading(c, "电力", "%+.0f".format(p.energy), ((p.energy + 10) / 20).toFloat(), if (p.energy < 0) Alarm else Mint)
        Reading(c, "人口", "${p.people}", (p.people / 200f), Ink)
        Reading(c, "造物点", "${p.credits.toInt()}", (p.credits / 20).toFloat(), Star)
        if (p.outside > 0) Text("气候失控！还能撑 ${(Play.COLLAPSE - p.outside).toInt()} 秒", fontSize = c.sz(0.018f), color = Alarm)
        for (g in p.goals) Text((if (g.met) "✔ " else "○ ") + g.text, fontSize = c.sz(0.0165f), color = if (g.met) Mint else Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "◀ 转") { Game.turn(-1) }
            Pill(c, "转 ▶") { Game.turn(1) }
            Pill(c, "重来") { Game.retry() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "档案", Star) { toggle(GUIDE_ID) }
            Pill(c, "关卡") { toggle(LEVELS_ID) }
        }
    }
}

/** What can be built on the tile in front. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildPanel(c: PhysicalLengthConverter) {
    Glass(c, 0.35f, 0.36f) {
        Game.version
        val p = Game.play ?: return@Glass
        val held = Game.holding
        // First the thing, then where it goes (2026-11-09 review).
        if (held != null) {
            Text("手上：${held.title}　${held.cost} 点", fontSize = c.sz(0.02f), color = Star)
            Text("看向星球，捏一下放下：绿圈能放，红圈不能。${held.line}", fontSize = c.sz(0.0125f), color = Ink)
        } else {
            Text("先在下面拿一样东西", fontSize = c.sz(0.02f), color = Star)
            Text("捏住星球拖动，或捏住后转动手腕，都能转星球。", fontSize = c.sz(0.0125f), color = Dim)
            // The level's goals are on the left panel; eyes stay on the planet and this panel (device test 2026-11-12).
            Text("← 这一关的任务在左边面板上，记得看一眼", fontSize = c.sz(0.0135f), color = Star)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f)), verticalArrangement = Arrangement.spacedBy(c.m(0.005f)), maxItemsInEachRow = 3) {
            for (b in Build.entries) {
                if (p.era.ordinal < b.needs.ordinal) continue
                val color = when {
                    b == held -> Mint
                    p.credits >= b.cost -> Ink
                    else -> Dim
                }
                Pill(c, (if (b == held) "✓ " else "") + "${b.title} ${b.cost}", color) { Game.hold(b) }
            }
        }
        if (held != null) Pill(c, "放下手上的", Dim) { Game.hold(null) }
        // The tile last pinched with nothing in hand: what is there, and taking it down.
        val t = if (held == null) p.selected?.let { p.tiles[it] } else null
        if (t != null) {
            Text(
                when {
                    t.build != null -> if (t.city) "这里：城市" else "这里：${t.build?.title}"
                    t.terrain == Terrain.OCEAN -> "这里：海洋"
                    t.terrain == Terrain.VOLCANO -> "这里：火山"
                    t.terrain == Terrain.MOUNTAIN -> "这里：雪山（只能建水电站）"
                    else -> "这里：空地"
                },
                fontSize = c.sz(0.015f), color = Star,
            )
            if (t.build != null) Pill(c, "拆掉（退一半点数）", Alarm) { Game.clearTile() }
        }
    }
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    Glass(c, 0.58f, 0.46f) {
        Game.version
        val p = Game.play ?: return@Glass
        if (p.won) {
            Text("${p.stage.title}　${stars(p.stars())}", fontSize = c.sz(0.034f), color = Star)
            val l = com.vrgames.planet.core.Science.of(com.vrgames.planet.core.Stages.ALL.indexOf(p.stage))
            Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
                Text(l.title, fontSize = c.sz(0.018f), color = Star)
                Pill(c, if (Game.speaking == l.id) "■ 停止" else "▶ 听讲解", Star) { if (Game.speaking == l.id) Game.stopNarration() else Game.speak(l.id) }
            }
            Text(l.text, fontSize = c.sz(0.0155f), color = Ink)
            Text("用时 ${clock(p.time)}，人口 ${p.people}，气温 %.1f℃。".format(p.temp), fontSize = c.sz(0.018f), color = Ink)
            if (p.stars() < 3) Text("三星：${clock(p.stage.par)} 内完成，气候一次也没有失控。", fontSize = c.sz(0.015f), color = Dim)
        } else {
            Text("${p.stage.title}　文明中断了", fontSize = c.sz(0.034f), color = Alarm)
            Text("看看气温和二氧化碳是怎么走到这一步的（档案窗口有曲线），再来一次。", fontSize = c.sz(0.018f), color = Ink)
        }
        for (g in p.goals) Text((if (g.met) "✔ " else "○ ") + g.text, fontSize = c.sz(0.0165f), color = if (g.met) Mint else Ink)
        val tr = Game.trophies
        if (tr > Game.trophiesBefore) Text(
            when (tr) {
                1 -> "得到铜奖杯！十二关全过。"
                2 -> "得到银奖杯！"
                else -> "得到金奖杯！解锁「群星之夜」。"
            },
            fontSize = c.sz(0.019f), color = Mint,
        )
        Text("总星数 ${Game.stars} / ${Game.maxStars}", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            if (p.won && Stages.ALL.indexOf(p.stage) < Stages.ALL.size - 1) CallToAction(c, "下一关 →") { Game.next() }
            Pill(c, "再来一次", Star) { Game.retry() }
            Pill(c, "档案", Sky) { toggle(GUIDE_ID) }
            if (Game.gold) Pill(c, "群星之夜", Star) { Game.show() }
        }
    }
}

@Composable
fun ShowPanel(c: PhysicalLengthConverter) {
    Glass(c, 0.32f, 0.17f) {
        Text("群星之夜", fontSize = c.sz(0.03f), color = Star)
        Text("十二关全部三星。这颗星球把火箭一枚枚送上了天。", fontSize = c.sz(0.017f), color = Ink)
        Pill(c, "结束") { Game.endShow() }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.7f)).clip(shape).background(PanelBg).padding(c.m(0.012f)), fontSize = c.sz(0.024f), color = Star)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Star) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.28f) {
        Game.version
        Pill(c, if (Game.window == LEVELS_ID) "收起关卡" else "关卡（选关、奖杯）", Star) { toggle(LEVELS_ID) }
        Pill(c, if (Game.window == GUIDE_ID) "收起档案" else "星球档案") { toggle(GUIDE_ID) }
        if (Game.gold) Pill(c, "群星之夜", Star) { Game.menuOpen = false; Game.show() }
        Pill(c, "把星球摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.0125f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定清空进度", Alarm) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Star)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
