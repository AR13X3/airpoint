package io.github.ar13x3.airpoint.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Airpoint's icon set: 24 dp, 1.8 dp round strokes, matching the Loop's rounded terminals.
 * Drawn in black and tinted by Icon().
 */
private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        strokes.forEach {
            addPath(
                addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        fills.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

object AirIcons {
    val Back by lazy { icon("back", "M19 12H5", "M11 18l-6-6 6-6") }
    val ChevronRight by lazy { icon("chevron_right", "M9.5 6l6 6-6 6") }
    val ChevronDown by lazy { icon("chevron_down", "M6 9.5l6 6 6-6") }
    val Tune by lazy {
        icon("tune", "M4 8h9", "M17.5 8h2.5", "M4 16h2.5", "M11 16h9",
            "M15.25 5.75a2.25 2.25 0 1 1 0 4.5a2.25 2.25 0 1 1 0-4.5z",
            "M8.75 13.75a2.25 2.25 0 1 1 0 4.5a2.25 2.25 0 1 1 0-4.5z")
    }
    val Stylus by lazy { icon("stylus", "M4 20L5.98 15.62L17.65 3.95A1.7 1.7 0 0 1 20.05 6.35L8.38 18.02Z") }
    val Phone by lazy {
        icon("phone", "M8 2.75h8a2 2 0 0 1 2 2v14.5a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4.75a2 2 0 0 1 2-2z", "M10.75 18.25h2.5")
    }
    val Monitor by lazy {
        icon("monitor",
            "M4.25 4.5h15.5a1.75 1.75 0 0 1 1.75 1.75v9a1.75 1.75 0 0 1-1.75 1.75H4.25A1.75 1.75 0 0 1 2.5 15.25v-9A1.75 1.75 0 0 1 4.25 4.5z",
            "M9 20.5h6", "M12 17v3.5")
    }
    val Check by lazy { icon("check", "M5 12.5l4.25 4.25L19 7") }
    val Close by lazy { icon("close", "M6.5 6.5l11 11", "M17.5 6.5l-11 11") }
    val Target by lazy {
        icon("target", "M12 5a7 7 0 1 1 0 14a7 7 0 1 1 0-14z", "M12 2v3", "M12 19v3", "M2 12h3", "M19 12h3",
            fills = listOf("M12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z"))
    }
    val Wifi by lazy {
        icon("wifi", "M2.75 9.25a13.5 13.5 0 0 1 18.5 0", "M5.75 12.75a9 9 0 0 1 12.5 0", "M8.75 16.25a4.6 4.6 0 0 1 6.5 0",
            fills = listOf("M12 18.3a1.25 1.25 0 1 1 0 2.5a1.25 1.25 0 1 1 0-2.5z"))
    }
    val Shield by lazy { icon("shield", "M12 3l7 2.75v5.5c0 4.4-3 8.2-7 9.75c-4-1.55-7-5.35-7-9.75v-5.5z", "M9 12l2.2 2.2L15.5 10") }
    val Bell by lazy { icon("bell", "M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z", "M10 20.75h4") }
    val Bluetooth by lazy { icon("bluetooth", "M7 7.5l10 9-5 4.25V3.25l5 4.25-10 9") }
    val Refresh by lazy { icon("refresh", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.75 4.25v4.5h-4.5") }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14") }
    val Trash by lazy {
        icon("trash", "M4.5 7h15", "M9.5 7V4.75h5V7", "M6.5 7l.9 12.2a1.5 1.5 0 0 0 1.5 1.3h6.2a1.5 1.5 0 0 0 1.5-1.3L17.5 7")
    }
    val Code by lazy { icon("code", "M8.5 7.5L4 12l4.5 4.5", "M15.5 7.5L20 12l-4.5 4.5", "M13.25 5.5l-2.5 13") }
    val Info by lazy {
        icon("info", "M12 3.75a8.25 8.25 0 1 1 0 16.5a8.25 8.25 0 1 1 0-16.5z", "M12 11v5",
            fills = listOf("M12 7a1.15 1.15 0 1 1 0 2.3a1.15 1.15 0 1 1 0-2.3z"))
    }
    val Sun by lazy {
        icon("sun", "M12 8a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M12 2.5v1.75", "M12 19.75v1.75", "M2.5 12h1.75", "M19.75 12h1.75",
            "M5.3 5.3l1.25 1.25", "M17.45 17.45l1.25 1.25", "M5.3 18.7l1.25-1.25", "M17.45 6.55l1.25-1.25")
    }
    val Moon by lazy { icon("moon", "M19.5 14.5A7.75 7.75 0 0 1 9.5 4.5a7.75 7.75 0 1 0 10 10z") }
    val Auto by lazy {
        icon("auto", "M12 4a8 8 0 1 1 0 16a8 8 0 1 1 0-16z", fills = listOf("M12 4a8 8 0 0 0 0 16z"))
    }
    val Share by lazy {
        icon("share", "M12 14.5V3.75", "M7.75 8L12 3.75 16.25 8", "M5 12.5v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6")
    }
    val Keyboard by lazy {
        icon("keyboard",
            "M4.5 6.5h15a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 16V8a1.5 1.5 0 0 1 1.5-1.5z",
            "M7 10h.01", "M10.33 10h.01", "M13.67 10h.01", "M17 10h.01", "M8 14h8")
    }
    val Stop by lazy {
        icon("stop", fills = listOf("M8 6.5h8a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 16V8A1.5 1.5 0 0 1 8 6.5z"))
    }
    val ArrowRight by lazy { icon("arrow_right", "M5 12h14", "M13 6l6 6-6 6") }
}
