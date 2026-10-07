package io.github.ar13x3.airpoint.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Airpoint mark, drawn from its centerline so it can be revealed like handwriting:
 * [progress] runs the stroke from 0 to 1, then [dot] (0..1+) scales the point in.
 * Consecutive round-capped segments of growing width reproduce the tapered outline.
 */
@Composable
fun LoopMark(
    modifier: Modifier = Modifier,
    color: Color = Air.colors.accent,
    dotColor: Color = color,
    progress: Float = 1f,
    dot: Float = 1f,
) {
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = "Airpoint" }) {
        drawLoop(color, dotColor, progress, dot)
    }
}

fun DrawScope.drawLoop(color: Color, dotColor: Color, progress: Float, dot: Float) {
    val k = size.minDimension / 48f
    val ox = (size.width - 48f * k) / 2f
    val oy = (size.height - 48f * k) / 2f
    val c = LoopGeometry.centerline
    val n = c.size / 3
    val reach = progress.coerceIn(0f, 1f) * (n - 1)
    var i = 0
    while (i < n - 1 && i < reach) {
        val f = (reach - i).coerceAtMost(1f)
        val x0 = c[i * 3]; val y0 = c[i * 3 + 1]
        val x1 = c[i * 3 + 3]; val y1 = c[i * 3 + 4]
        val w = c[i * 3 + 2] + (c[i * 3 + 5] - c[i * 3 + 2]) * f
        drawLine(
            color,
            Offset(ox + x0 * k, oy + y0 * k),
            Offset(ox + (x0 + (x1 - x0) * f) * k, oy + (y0 + (y1 - y0) * f) * k),
            strokeWidth = w * k,
            cap = StrokeCap.Round,
        )
        i++
    }
    if (dot > 0f) {
        drawCircle(dotColor, LoopGeometry.DOT_R * k * dot, Offset(ox + LoopGeometry.DOT_X * k, oy + LoopGeometry.DOT_Y * k))
    }
}

/** The mark writing itself: the stroke draws on, then the point lands with a small bounce. */
@Composable
fun AnimatedLoopMark(
    modifier: Modifier = Modifier,
    color: Color = Air.colors.accent,
    dotColor: Color = color,
    startDelayMillis: Int = 200,
    replayKey: Any? = Unit,
) {
    val reduce = Air.reduceMotion
    val stroke = remember(replayKey) { Animatable(if (reduce) 1f else 0f) }
    val dot = remember(replayKey) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(replayKey) {
        if (reduce) return@LaunchedEffect
        delay(startDelayMillis.toLong())
        launch {
            delay(1050)
            dot.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        }
        stroke.animateTo(1f, tween(1150, easing = Motion.Draw))
    }
    LoopMark(modifier, color, dotColor, stroke.value, dot.value)
}
