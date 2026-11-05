package com.vrgames.planet.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.vrgames.planet.core.Build
import com.vrgames.planet.core.Happening
import com.vrgames.planet.core.Play
import com.vrgames.planet.core.Terrain
import com.vrgames.planet.core.Tile
import com.vrgames.planet.core.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The planet, floating before the player among the stars. [spin] turns it; everything on the surface
 * is in spin's frame, on the unit sphere times [R]. The surface is painted once per level from land
 * and ocean pictures by each tile's terrain; the air's color follows the temperature.
 */
class PlanetView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>, private val assets: AssetManager) {
    val root = Entity()
    val spin = Entity()
    private var surface = Entity().also { spin.addChild(it) }
    private var things = Entity().also { spin.addChild(it) }
    private val iblSource = Entity().also { root.addChild(it) }
    private val sphere = MeshResource.createSphere(1f).shared()
    private val tileOwner = HashMap<Entity, Int>()
    private val colliders = ArrayList<Entity>()
    // Added light so faint it cannot be seen until hover brightens it; a see-through layer would darken what is behind.
    private val hoverMat = glow(color(1f, 1f, 1f, 0.004f)).shared()
    private val ringMat = glow(color(1f, 0.85f, 0.4f, 0.8f)).shared()
    private val ring = model(MeshResource.createTorus(R * 0.3f, R * 0.012f), ringMat).apply { enabled = false }.also { spin.addChild(it) }
    private val air = model(sphere, rim(color(0.3f, 0.6f, 1f, 0.42f))).apply { setScale(R * 1.07f) }.also { root.addChild(it) }
    private var airState = -1
    private val airMats = listOf(
        rim(color(0.35f, 0.5f, 1f, 0.5f)).shared(), // cold
        rim(color(0.3f, 0.65f, 1f, 0.42f)).shared(), // kind
        rim(color(1f, 0.55f, 0.25f, 0.5f)).shared(), // hot
    )
    private val clouds: Entity
    private val fire = Sparks("planet_fire", 140, sphere, glow(color(1f, 0.55f, 0.2f, 1f)).shared()).also { spin.addChild(it.entity) }
    private val ash = Sparks("planet_ash", 90, sphere, glow(color(0.5f, 0.48f, 0.45f, 0.6f)).shared()).also { spin.addChild(it.entity) }
    private val stars = Sparks("planet_stars", 200, sphere, glow(color(0.9f, 0.95f, 1f, 1f)).shared()).also { root.addChild(it.entity) }
    private var played: Play? = null
    private var yaw = 0f
    private var pitch = 0f
    private var wantYaw = 0f
    private var wantPitch = 0f
    private var clock = 0.0
    private var idle = 99.0
    private var showRocket = 0.0
    private var dirty = false
    private val sats = ArrayList<Entity>()
    private val flights = ArrayList<Flight>()
    private val trophyViews = ArrayList<Entity>()
    private val trophyMats = listOf(
        pbr(color(0.72f, 0.45f, 0.22f), metallic = 1f, roughness = 0.3f), pbr(color(0.8f, 0.82f, 0.86f), metallic = 1f, roughness = 0.22f), pbr(color(1f, 0.78f, 0.3f), metallic = 1f, roughness = 0.2f),
    ).map { it.shared() }
    private val trophyOff = pbr(color(0.08f, 0.08f, 0.1f), roughness = 0.8f).shared()

    private class Flight(val e: Entity, val dir: Vec3, var t: Double = 0.0)

    init {
        root.addChild(spin)
        root.setPos(Vector3(0f, Y, 0f))
        // Clouds: white on black, added to what is under them.
        clouds = model(sphereMesh("clouds_shell"), (tex["clouds"]?.let { t ->
            UnlitMaterial.create(com.pico.spatial.core.ecs.resource.BlendingMode.ADD).apply {
                setBaseColorTexture(t)
                setBaseColor(color(0.85f, 0.85f, 0.85f, 0.85f))
            }.shared()
        } ?: glow(color(1f, 1f, 1f, 0.05f)))).apply { setScale(R * 1.025f) }
        root.addChild(clouds)
        shelf()
        repeat(Play.TILES) {
            val c = model(sphere, hoverMat)
            // Wide enough that the boxes overlap: there must be no gap on the planet where a pinch lands on nothing.
            c.setScale(PAD)
            c.components.set(CollisionComponent(listOf(ShapeResource.createSphere(1f)), PhysicsMaterialResource()))
            c.components.set(InteractableComponent())
            c.components.set(HoverEffectComponent())
            spin.addChild(c)
            colliders += c
            tileOwner[c] = it
        }
    }

    private fun setMaterials(e: Entity, m: Material) {
        e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    private fun clone(name: String): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))

    private fun shelf() {
        val dark = pbr(color(0.14f, 0.15f, 0.2f), metallic = 0.5f, roughness = 0.5f).shared()
        root.addChild(model(MeshResource.createBox(Vector3(0.34f, 0.02f, 0.14f), 0.004f), dark).apply { setPos(Vector3(0.62f, -0.5f, 0.2f)) })
        for (k in 0 until 3) {
            val t = clone("trophy") ?: model(MeshResource.createCone(0.1f, 0.03f), dark)
            t.setScale(0.09f)
            t.setPos(Vector3(0.51f + k * 0.11f, -0.49f, 0.2f))
            t.transform().eulerAngles = EulerAngles(0f, -90f, 0f)
            root.addChild(t)
            trophyViews += t
        }
        trophies(0)
    }

    fun trophies(earned: Int) = trophyViews.forEachIndexed { k, t -> setMaterials(t, if (k < earned) trophyMats[k] else trophyOff) }

    fun tileOf(e: Entity?): Int? {
        var cur = e
        while (cur != null) {
            tileOwner[cur]?.let { return it }
            cur = cur.getParent()
        }
        return null
    }

    private fun at(t: Tile, r: Float = R) = Vector3(t.x.toFloat() * r, t.y.toFloat() * r, t.z.toFloat() * r)

    fun build(p: Play) {
        played = p
        surface.destroy()
        surface = Entity().also { spin.addChild(it) }
        things.destroy()
        things = Entity().also { spin.addChild(it) }
        fire.clear()
        ash.clear()
        sats.clear()
        for (f in flights) f.e.destroy()
        flights.clear()
        ring.enabled = false
        p.tiles.forEachIndexed { k, t -> colliders.getOrNull(k)?.setPos(at(t)) }
        val painted = paint(p)
        val m = PhysicallyBasedMaterial.create().apply {
            setBaseColorTexture(painted)
            setEmissiveTexture(painted)
            setEmissiveColor(color(0.2f, 0.2f, 0.2f))
            setRoughness(0.75f)
        }
        surface.addChild(model(sphereMesh("ground_${p.stage.id}"), m).apply { setScale(R) })
        // What the land itself carries.
        for (t in p.tiles) when (t.terrain) {
            Terrain.MOUNTAIN -> place("mountain", t)?.let { surface.addChild(it) }
            Terrain.VOLCANO -> place("volcano", t)?.let { surface.addChild(it) }
            else -> {}
        }
        rebuildTiles()
        yaw = 0f
        pitch = 0f
        wantYaw = 0f
        wantPitch = 0f
    }

    /** A unit sphere with its seam at the back and a texture laid by longitude and latitude. */
    private fun sphereMesh(name: String): MeshResource {
        val b = MeshBuilder()
        val nu = 64
        val nv = 32
        fun v(i: Int, j: Int): Vector3 {
            val lon = 2 * PI * i / nu - PI
            val lat = PI / 2 - PI * j / nv
            return Vector3((cos(lat) * sin(lon)).toFloat(), sin(lat).toFloat(), (cos(lat) * cos(lon)).toFloat())
        }
        fun uv(i: Int, j: Int) = com.pico.spatial.core.math.Vector2(i.toFloat() / nu, 1f - j.toFloat() / nv)
        for (j in 0 until nv) for (i in 0 until nu) {
            val a = v(i, j + 1); val bb = v(i + 1, j + 1); val c = v(i + 1, j); val d = v(i, j)
            b.smooth(a, bb, c, d, a, bb, c, d, uv(i, j + 1), uv(i + 1, j + 1), uv(i + 1, j), uv(i, j))
        }
        return b.build(name).shared()
    }

    /** The surface for this level: land where land tiles are, sea elsewhere, a wavy coast between. */
    private fun paint(p: Play): TextureResource {
        val w = 1024
        val h = 512
        val land = picture("land")
        val sea = picture("ocean")
        val out = IntArray(w * h)
        // A coarse map of which tile is nearest, then the coast smoothed by a little noise.
        val cw = 192
        val ch = 96
        val isLand = FloatArray(cw * ch)
        for (j in 0 until ch) for (i in 0 until cw) {
            val lon = 2 * PI * i / cw - PI
            val lat = PI / 2 - PI * (j + 0.5) / ch
            val x = cos(lat) * sin(lon)
            val y = sin(lat)
            val z = cos(lat) * cos(lon)
            val wob = 0.14 * sin(lon * 5 + lat * 3) + 0.09 * sin(lon * 11 - lat * 7)
            var best = -2.0
            var kind = false
            for (t in p.tiles) {
                val d = x * t.x + y * t.y + z * t.z + (if (t.terrain != Terrain.OCEAN) wob * 0.3 else 0.0)
                if (d > best) { best = d; kind = t.terrain != Terrain.OCEAN }
            }
            isLand[j * cw + i] = if (kind) 1f else 0f
        }
        for (j in 0 until h) for (i in 0 until w) {
            val fx = i.toFloat() / w * cw
            val fy = j.toFloat() / h * ch - 0.5f
            val x0 = floor(fx).toInt()
            val y0 = floor(fy).toInt().coerceIn(0, ch - 1)
            val y1 = (y0 + 1).coerceAtMost(ch - 1)
            val u = fx - x0
            val v = (fy - y0).coerceIn(0f, 1f)
            fun m(a: Int, b: Int) = isLand[b * cw + ((a % cw) + cw) % cw]
            val mask = (m(x0, y0) * (1 - u) + m(x0 + 1, y0) * u) * (1 - v) + (m(x0, y1) * (1 - u) + m(x0 + 1, y1) * u) * v
            val s = ((mask - 0.35f) / 0.3f).coerceIn(0f, 1f)
            // The sea a little deeper and calmer than the picture.
            val a = mix(wrapped(sea, i, j, w, h), 0xFF0C2A52.toInt(), 0.45f)
            val b = wrapped(land, i, j, w, h)
            // Polar ice.
            val latAbs = kotlin.math.abs(j.toFloat() / h - 0.5f) * 2
            val ice = ((latAbs - 0.84f) / 0.08f).coerceIn(0f, 1f)
            out[j * w + i] = mix(mix(a, b, s), 0xFFE8F0F8.toInt(), ice)
        }
        val bmp = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        return TextureResource.create(bmp)
    }

    /** The pictures do not join up where their left and right edges meet: fade to a shifted copy there. */
    private fun wrapped(pic: Pic, i: Int, j: Int, w: Int, h: Int): Int {
        val here = pic.get(i * pic.width / w, j * pic.height / h)
        val k = (kotlin.math.min(i, w - i).toFloat() / w / 0.07f).coerceIn(0f, 1f)
        if (k >= 1f) return here
        val other = pic.get((i + w / 2) % w * pic.width / w, j * pic.height / h)
        return mix(other, here, k * k * (3 - 2 * k))
    }

    private class Pic(val px: IntArray, val width: Int, val height: Int) {
        fun get(x: Int, y: Int) = px[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
    }

    private val pics = HashMap<String, Pic>()

    private fun picture(name: String): Pic = pics.getOrPut(name) {
        val b = assets.open("textures/$name.jpg").use { BitmapFactory.decodeStream(it) }
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        Pic(px, b.width, b.height).also { b.recycle() }
    }

    private fun mix(a: Int, b: Int, s: Float): Int {
        if (s <= 0f) return a
        if (s >= 1f) return b
        val q = 1 - s
        val r = ((a shr 16 and 255) * q + (b shr 16 and 255) * s).toInt()
        val g = ((a shr 8 and 255) * q + (b shr 8 and 255) * s).toInt()
        val bl = ((a and 255) * q + (b and 255) * s).toInt()
        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or bl
    }

    /** A model standing on a tile, its foot on the ground and its head along the normal. */
    private fun place(name: String, t: Tile, r: Float = R * 0.995f): Entity? {
        val e = clone(name) ?: return null
        e.setScale(Looks.scale(name))
        e.setPos(at(t, r))
        e.transform().setQuaternion(quatFromY(Vec3(t.x, t.y, t.z)))
        return e
    }

    fun rebuildTiles() {
        dirty = true
    }

    private fun refresh(p: Play) {
        things.destroy()
        things = Entity().also { spin.addChild(it) }
        sats.clear()
        for (t in p.tiles) {
            val b = t.build ?: continue
            when (b) {
                Build.SATELLITE -> {
                    val s = clone("satellite") ?: continue
                    s.setScale(Looks.scale("satellite"))
                    things.addChild(s)
                    sats += s
                }
                Build.ROCKET -> if (!p.launched) place("rocket", t)?.let { things.addChild(it) }
                Build.VILLAGE -> place(if (t.city) "city" else "village", t)?.let { things.addChild(it) }
                Build.FOREST -> place("forest", t)?.let { things.addChild(it) }
                Build.WHALE -> place("whale", t, R * 1.005f)?.let { things.addChild(it) }
                Build.WIND -> place("windfarm", t)?.let { things.addChild(it) }
                Build.DOME -> place("dome", t)?.let { things.addChild(it) }
            }
        }
        receive(things)
    }

    /** Brings a tile to the front. */
    fun face(id: Int) {
        val p = played ?: return
        val t = p.tiles.getOrNull(id) ?: return
        wantYaw = Math.toDegrees(-atan2(t.x, t.z)).toFloat()
        wantPitch = Math.toDegrees(atan2(t.y, sqrt(t.x * t.x + t.z * t.z))).toFloat().coerceIn(-65f, 65f)
        idle = 0.0
        ring.enabled = true
        ring.setPos(at(t, R * 1.012f))
        ring.transform().setQuaternion(quatFromY(Vec3(t.x, t.y, t.z)))
    }

    fun turn(dir: Int) {
        wantYaw += dir * 40f
        idle = 0.0
    }

    fun launch(id: Int) {
        val t = played?.tiles?.getOrNull(id) ?: return
        val e = clone("rocket") ?: return
        e.setScale(Looks.scale("rocket"))
        e.setPos(at(t))
        e.transform().setQuaternion(quatFromY(Vec3(t.x, t.y, t.z)))
        spin.addChild(e)
        flights += Flight(e, Vec3(t.x, t.y, t.z))
        rebuildTiles()
    }

    fun struck(h: Happening, id: Int) {
        val t = played?.tiles?.getOrNull(id) ?: return
        val q = at(t, R * 1.03f)
        val v = Vec3(q.x.toDouble(), q.y.toDouble(), q.z.toDouble())
        when (h) {
            Happening.ERUPTION -> {
                fire.emit(v, 40, 0.1, 1.1, 0.005f, Vec3(t.x * 0.05, t.y * 0.05, t.z * 0.05))
                ash.emit(v, 50, 0.05, 3.5, 0.008f, Vec3(t.x * 0.04, t.y * 0.04, t.z * 0.04))
            }
            Happening.COMET -> fire.emit(v, 70, 0.2, 1.0, 0.006f)
            Happening.COLD -> ash.emit(v, 20, 0.08, 2.0, 0.006f)
        }
    }

    fun sync(p: Play, dt: Double) {
        if (p !== played) return
        clock += dt
        idle += dt
        if (dirty) {
            dirty = false
            refresh(p)
        }
        // Slowly round, when nothing has been pinched for a while: the far side comes by itself.
        if (idle > IDLE_S && p.selected == null) wantYaw += (dt * 6).toFloat()
        val k = (dt * 3).toFloat().coerceAtMost(1f)
        var d = wantYaw - yaw
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        yaw += d * k
        pitch += (wantPitch - pitch) * k
        spin.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
        clouds.transform().eulerAngles = EulerAngles(pitch * 0.9f, yaw + (clock * 1.5).toFloat(), 0f)
        val state = if (p.temp > Play.HOT_LIMIT - 3) 2 else if (p.temp < Play.COLD_LIMIT + 3) 0 else 1
        if (state != airState) {
            airState = state
            setMaterials(air, airMats[state])
        }
        sats.forEachIndexed { i, s ->
            val a = clock * 0.5 + i * 2.1
            s.setPos(Vector3((cos(a) * R * 1.42).toFloat(), (sin(a * 0.7) * R * 0.5).toFloat(), (sin(a) * R * 1.42).toFloat()))
        }
        val it = flights.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.t += dt
            val h = R + 0.6 * f.t * f.t * 0.2
            f.e.setPos(Vector3((f.dir.x * h).toFloat(), (f.dir.y * h).toFloat(), (f.dir.z * h).toFloat()))
            if ((clock * 12).toInt() != ((clock - dt) * 12).toInt()) fire.emit(Vec3(f.dir.x * h, f.dir.y * h, f.dir.z * h), 3, 0.03, 0.7, 0.004f)
            if (f.t > 5) {
                f.e.destroy()
                it.remove()
            }
        }
        fire.update(dt, 0.4)
        ash.update(dt, 0.2)
        stars.update(dt, 0.3)
    }

    /** The show: rockets from all around, sparks across the sky. */
    fun show(dt: Double) {
        showRocket += dt
        if (showRocket > 1.2) {
            showRocket = 0.0
            val p = played ?: return
            launch(p.tiles[(clock * 7).toInt() % p.tiles.size].id)
            stars.emit(Vec3(sin(clock) * 0.5, 0.3 + cos(clock * 0.7) * 0.2, -0.3), 40, 0.25, 1.4, 0.004f)
        }
    }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 7f))
        receive(root)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach { receive(it) }
    }

    fun receiveAll() = receive(root)

    companion object {
        const val R = 0.22f
        /** The pinch box of a tile: tiles stand about 0.6 R apart, so this overlaps its neighbors. */
        const val PAD = 0.5f * R
        const val Y = 1.2f
        const val IDLE_S = 4.0
    }
}
