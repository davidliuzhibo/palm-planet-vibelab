package com.vrgames.planet

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.planet.content.GuideWindow
import com.vrgames.planet.content.LevelsWindow
import com.vrgames.planet.content.PlanetStage

const val LEVELS_ID = "Levels"
const val GUIDE_ID = "Guide"

/**
 * Three linked spaces: the small planet in front of the seated player (a full-immersion Stage), the
 * 关卡 window (levels, stars, trophies) and the 星球档案 window. They share [com.vrgames.planet.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { PlanetStage() }
        }
        WindowContainer(LEVELS_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { LevelsWindow() }
        }
        WindowContainer(GUIDE_ID, Form.Planar, { defaultSize = WindowContainerSize(820.dp, 1000.dp) }) {
            PicoTheme { GuideWindow() }
        }
    }
