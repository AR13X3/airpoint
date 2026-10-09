package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import io.github.ar13x3.airpoint.core.DiscoveredPc
import io.github.ar13x3.airpoint.ui.brand.LoopMark
import io.github.ar13x3.airpoint.ui.theme.Air
import kotlin.math.cos
import kotlin.math.sin

/**
 * Discovery as a radar: echoes ripple out from the phone while a sweep turns, and each
 * computer that answers lands on the scope as a blip.
 */
@Composable
fun Radar(found: List<DiscoveredPc>, modifier: Modifier = Modifier) {
    val c = Air.colors
    val reduce = Air.reduceMotion
    val loop = rememberInfiniteTransition(label = "radar")
    val sweep by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sweep")
    val echo by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "echo")

    // Each PC pops in once, at a stable angle derived from its id.
    val blips = found.associate { pc ->
        pc.id to key(pc.id) {
            val pop = remember { Animatable(0f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
            pop
        }
    }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxR = size.minDimension / 2
            for (i in 1..3) {
                drawCircle(c.outline, maxR * i / 3f, center, style = Stroke(1.dp.toPx()))
            }
            if (!reduce) {
                rotate(sweep, center) {
                    drawCircle(
                        Brush.sweepGradient(
                            0f to c.accent.copy(alpha = 0f), 0.82f to c.accent.copy(alpha = 0f),
                            1f to c.accent.copy(alpha = 0.22f), center = center,
                        ),
                        maxR, center,
                    )
                }
                for (k in 0 until 3) {
                    val e = (echo + k / 3f) % 1f
                    drawCircle(c.accent.copy(alpha = (1f - e) * 0.35f), maxR * (0.18f + e * 0.82f), center, style = Stroke(1.5.dp.toPx()))
                }
            }
            found.forEach { pc ->
                val p = blips[pc.id]?.value ?: 0f
                val angle = Math.toRadians(((pc.id.hashCode() and 0x7fffffff) % 360).toDouble())
                val r = maxR * 0.66f
                val at = Offset(center.x + (cos(angle) * r).toFloat(), center.y + (sin(angle) * r).toFloat())
                drawCircle(c.live.copy(alpha = 0.22f * p), 16.dp.toPx() * p, at)
                drawCircle(c.live, 6.dp.toPx() * p, at)
            }
        }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(c.surfaceRaised).border(1.5.dp, c.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            LoopMark(Modifier.size(36.dp), color = c.accentText)
        }
    }
}
