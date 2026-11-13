package com.vrgames.planet.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vrgames.planet.core.Build
import com.vrgames.planet.core.Ev
import com.vrgames.planet.core.Play
import com.vrgames.planet.core.Governor
import com.vrgames.planet.core.Happening
import com.vrgames.planet.core.Stage
import com.vrgames.planet.core.Stages
import com.vrgames.planet.render.Music
import com.vrgames.planet.render.PlanetView
import com.vrgames.planet.render.Sfx
import com.vrgames.planet.render.Sound

/**
 * State the stage, its panels and the windows share. [play] is the level being played; the frame
 * loop steps it and turns its events into notes, sounds and bursts. Best stars per level are saved.
 */
object Game {
    private const val PREFS = "planet"
    private const val TAG = "PlanetGame"

    enum class Phase { CARD, INTRO, PLAYING, RESULT, SHOW }

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    val best = LinkedHashMap<String, Int>()
    var play: Play? = null; private set
    /** A new [play]: the view builds its land. */
    var playVersion by mutableIntStateOf(0); private set
    var stage by mutableStateOf(Stages.ALL[0]); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var window by mutableStateOf<String?>(null); private set
    private var windowCopy: Any? = null
    var closeWindow: (String) -> Unit = {}
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var faceRequest by mutableIntStateOf(0)
    var view: PlanetView? = null
    var sound: Sound? = null
    var music: Music? = null
    var trophiesBefore = 0
    /** The test hook, and the show's driver. */
    var bot: Governor? = null
    var timeScale = 1.0
    var debugView by mutableStateOf<String?>(null)
    var debugOpen by mutableStateOf<String?>(null)
    private var lastSecond = -1

    val stars get() = best.values.sum()
    val maxStars get() = Stages.ALL.size * 3
    val bronze get() = Stages.ALL.all { (best[it.id] ?: 0) > 0 }
    val silver get() = stars >= maxStars * 2 / 3
    val gold get() = stars >= maxStars
    val trophies get() = if (gold) 3 else if (silver) 2 else if (bronze) 1 else 0

    fun init(context: Context) {
        appContext = context.applicationContext
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            p.getString("best", "")!!.split(",").filter { it.contains(":") }.forEach {
                val (k, v) = it.split(":")
                best[k] = v.toIntOrNull() ?: 0
            }
        }
        Log.i(TAG, "stars $stars/$maxStars")
        AutoPilot.register(context)
    }

    fun say(text: String) {
        note = text
        noteVersion++
    }

    fun open(s: Stage): Boolean {
        val k = Stages.ALL.indexOf(s)
        return k == 0 || (best[Stages.ALL[k - 1].id] ?: 0) > 0 || (best[s.id] ?: 0) > 0
    }

    /** From the card: the first level not cleared yet. */
    fun start() = intro(Stages.ALL.firstOrNull { (best[it.id] ?: 0) == 0 } ?: Stages.ALL.last())

    private fun fresh(s: Stage) {
        stage = s
        synchronized(lock) { play = Play(s) }
        bot = null
        playVersion++
    }

    fun intro(s: Stage) {
        holding = null
        if (!open(s)) {
            say("这一关还没开放：先过前面的。")
            return
        }
        fresh(s)
        phase = Phase.INTRO
        version++
    }

    fun begin() {
        fresh(stage)
        phase = Phase.PLAYING
        music?.resume()
        Log.i(TAG, "begin ${stage.id}")
        say("先在右边面板上拿起一样东西，再看向星球捏一下放下。")
        version++
    }

    fun retry() { stopNarration(); begin() }

    fun next() {
        stopNarration()
        val k = Stages.ALL.indexOf(stage)
        if (k + 1 < Stages.ALL.size) intro(Stages.ALL[k + 1]) else toCard()
    }

    fun toCard() {
        stopNarration()
        phase = Phase.CARD
        version++
    }

    /**
     * What the hand holds, chosen from the tray (2026-11-09 review: choose the thing first, then
     * where it goes). It stays in hand for more of the same until put down.
     */
    var holding by mutableStateOf<Build?>(null)

    fun hold(b: Build?) {
        holding = if (holding == b) null else b
        sound?.play(Sfx.SELECT)
        hoverTile?.let { hover(it) }
        version++
    }

    /** The tile under the eye or the ray, while something is held (for the green or red ring). */
    var hoverTile: Int? = null; private set

    fun hover(id: Int?) {
        hoverTile = id
        val p = play
        val b = holding
        if (p == null || b == null || id == null || phase != Phase.PLAYING) { view?.hover(null, false); return }
        val ok = synchronized(lock) { p.tiles.getOrNull(id)?.let { p.canBuild(b, it) == null } } ?: false
        view?.hover(id, ok)
    }

    /** A pinch on the planet: with something in hand it goes there; else the tile is chosen (to look or to clear). */
    fun tile(id: Int) {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        synchronized(lock) { p.select(id) }
        view?.face(id)
        val b = holding
        if (b != null) {
            synchronized(lock) { p.build(b, id) }
            hover(id)
        } else {
            sound?.play(Sfx.SELECT)
        }
        version++
    }

    fun turn(dir: Int) {
        view?.turn(dir)
    }

    fun build(b: Build) {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        synchronized(lock) { p.build(b) }
        version++
    }

    fun clearTile() {
        synchronized(lock) { play?.clear() }
        view?.rebuildTiles()
        version++
    }

    fun tick(dt0: Double) {
        val dt = dt0 * timeScale
        val p = play ?: return
        if (phase == Phase.SHOW) view?.show(dt)
        if (phase != Phase.PLAYING) return
        val b = bot
        val evs = synchronized(lock) { if (b != null) p.step(dt) { b.update(Play.STEP) } else p.step(dt) }
        var changed = false
        for (e in evs) {
            changed = true
            when (e) {
                is Ev.Note -> say(e.text)
                is Ev.Refused -> { say(e.why); sound?.play(Sfx.REFUSE) }
                is Ev.Built -> { sound?.play(Sfx.BUILD); view?.rebuildTiles(); say("${e.what.title}：${e.what.line}") }
                is Ev.Grew -> { sound?.play(Sfx.GROW); view?.rebuildTiles(); say("村庄长成了城市！") }
                is Ev.Warning -> { sound?.play(Sfx.WARN); say(e.what.warn) }
                is Ev.Struck -> onStruck(e)
                is Ev.EraUp -> { sound?.play(Sfx.ERA); say("进入「${e.era.title}」时代！") }
                is Ev.Launch -> { sound?.play(Sfx.LAUNCH); view?.launch(e.tile) }
                Ev.Goal -> sound?.play(Sfx.GOAL)
                Ev.TooHot -> say("太热了！多种树，少建城。")
                Ev.TooCold -> say("太冷了！让城市多烧一点，或者少种几片树。")
                Ev.Won -> finish(true)
                Ev.Lost -> finish(false)
            }
        }
        val second = p.time.toInt()
        if (changed || second != lastSecond) version++
        lastSecond = second
    }

    private fun onStruck(e: Ev.Struck) {
        view?.struck(e.what, e.tile)
        sound?.play(when (e.what) { Happening.ERUPTION -> Sfx.ERUPT; Happening.COLD -> Sfx.COLD; Happening.COMET -> Sfx.COMET })
        say(e.what.title + "！")
        view?.rebuildTiles()
    }

    /** The narration clip now playing, or null. */
    var speaking by mutableStateOf<String?>(null); private set
    private var narrator: android.media.MediaPlayer? = null
    private var appContext: Context? = null

    fun speak(id: String) {
        stopNarration()
        val c = appContext ?: return
        runCatching {
            val fd = c.assets.openFd("narration/$id.ogg")
            narrator = android.media.MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { stopNarration() }
                prepare()
                start()
            }
            speaking = id
            music?.ducked = true
        }.onFailure { Log.i(TAG, "narration $id: ${it.message}") }
    }

    fun stopNarration() {
        runCatching { narrator?.stop() }
        runCatching { narrator?.release() }
        narrator = null
        speaking = null
        music?.ducked = false
    }

    private fun finish(won: Boolean) {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        trophiesBefore = trophies
        if (won) {
            val s = p.stars()
            if (s > (best[p.stage.id] ?: 0)) best[p.stage.id] = s
        }
        save()
        phase = Phase.RESULT
        sound?.play(if (won) Sfx.FANFARE else Sfx.LOSE)
        if (won) {
            // The reward: the years race by on the planet you looked after, and a little science to go with it.
            view?.timelapse()
            val l = com.vrgames.planet.core.Science.of(com.vrgames.planet.core.Stages.ALL.indexOf(p.stage))
            speak(l.id)
        }
        Log.i(TAG, "finish ${p.stage.id} won=$won stars=${p.stars()} time=${"%.1f".format(p.time)} people=${p.people} temp=${"%.1f".format(p.temp)}")
        version++
    }

    /** The gold trophy's show: rockets go up from all around the planet. */
    fun show() {
        if (!gold && !AutoPilot.debug) return
        fresh(Stages.ALL.last())
        phase = Phase.SHOW
        sound?.play(Sfx.FANFARE)
        say("群星之夜！")
        version++
    }

    fun endShow() {
        stage = Stages.ALL[0]
        fresh(stage)
        toCard()
    }

    private fun save() {
        prefs?.edit()?.putString("best", best.entries.joinToString(",") { "${it.key}:${it.value}" })?.apply()
    }

    fun restart() {
        best.clear()
        save()
        fresh(Stages.ALL[0])
        phase = Phase.CARD
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(n: Int) {
        for (s in Stages.ALL) best[s.id] = n
        save()
        version++
    }

    fun windowShown(id: String, copy: Any? = null) {
        window = id
        if (copy != null) windowCopy = copy
        version++
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && copy !== windowCopy) return
        if (window == id) window = null
        version++
    }
}
