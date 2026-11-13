package com.vrgames.planet.render

/** Sizes of the models on the planet. Models come normalized to 1 m along their longest side. */
object Looks {
    private val SCALE = mapOf(
        "city" to 0.075f, "village" to 0.06f, "forest" to 0.065f, "mountain" to 0.08f, "volcano" to 0.08f, "windfarm" to 0.065f, "rocket" to 0.08f, "satellite" to 0.05f, "whale" to 0.055f, "dome" to 0.065f,
        "farm" to 0.065f, "mangrove" to 0.065f, "reef" to 0.06f, "solar" to 0.062f, "hydro" to 0.07f, "port" to 0.07f,
    )
    /** The planet grew from 22 to 30 cm (2026-11-12) with 48 tiles for 36: things grow a fifth with it. */
    fun scale(name: String) = (SCALE[name] ?: 0.06f) * 1.2f
}
