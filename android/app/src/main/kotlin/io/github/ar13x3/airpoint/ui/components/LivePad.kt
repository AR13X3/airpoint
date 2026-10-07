package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.core.PadEvent
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Radii
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A miniature screen that mirrors the S Pen: the cursor leaves a tapering trail (an echo of
 * the Loop's stroke), presses ripple out, and a double-press springs it back to center.
 */
@Composable
fun LivePad(pad: Flow<PadEvent>, gain: Float, modifier: Modifier = Modifier) {
    val c = Air.colors
    val reduce = Air.reduceMotion
    val model = remember { PadModel() }
    val scope = rememberCoroutineScope()
    val recenter = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    LaunchedEffect(gain) {
        pad.collect { e ->
            when (e) {
                is PadEvent.Motion -> model.move(e.dx * gain, e.dy * gain)
                is PadEvent.Button -> model.press(e.pressed)
                PadEvent.Center -> scope.launch {
                    model.flash()
                    recenter.snapTo(model.pos)
                    recenter.animateTo(Offset.Zero, spring(dampingRatio = 0.55f, stiffness = 300f)) {
                        model.pos = value
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) withFrameNanos { model.tick(it) }
    }

    val live = rememberInfiniteTransition(label = "live")
    val blink by live.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "blink")
    val padDescription = stringResource(R.string.live_hint)

    Box(
        modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(Radii.lg)
            .background(c.surfaceSunken)
            .border(1.dp, c.outline, Radii.lg)
            .semantics { contentDescription = padDescription },
    ) {
        Canvas(Modifier.matchParentSize()) {
            model.frame
            val step = 22.dp.toPx()
            val dot = 1.1.dp.toPx()
            var gx = (size.width % step) / 2
            while (gx < size.width) {
                var gy = (size.height % step) / 2
                while (gy < size.height) {
                    drawCircle(c.outlineStrong.copy(alpha = 0.7f), dot, Offset(gx, gy))
                    gy += step
                }
                gx += step
            }
            val half = Offset(size.width / 2, size.height / 2)
            val inset = 18.dp.toPx()
            fun toPx(p: Offset) = Offset(half.x + p.x * (half.x - inset), half.y + p.y * (half.y - inset))

            // Crosshair flash on recenter.
            if (model.flashT > 0f) {
                val a = model.flashT
                drawCircle(c.accent.copy(alpha = 0.5f * a), 26.dp.toPx() * (2f - a), half, style = Stroke(1.5.dp.toPx()))
            }
            // Trail: older points thinner and fainter, like the Loop's taper in reverse.
            val trail = model.trail
            val n = trail.size
            for (i in 1 until n) {
                val f = i / n.toFloat()
                drawLine(
                    c.accent.copy(alpha = f * 0.85f * model.trailFade(i)),
                    toPx(trail[i - 1]), toPx(trail[i]),
                    strokeWidth = (1.5f + 5f * f).dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            for (r in model.ripples) {
                drawCircle(c.live.copy(alpha = (1f - r.t) * 0.9f), (8f + r.t * 34f).dp.toPx(), toPx(r.at), style = Stroke(2.dp.toPx()))
            }
            val p = toPx(model.pos)
            val coreR = (if (model.pressed) 5.5f else 7f).dp.toPx()
            val coreColor = if (model.pressed) c.live else c.accent
            drawCircle(coreColor.copy(alpha = 0.18f), coreR * 2.8f, p)
            drawCircle(coreColor, coreR, p)
            drawCircle(c.surfaceSunken, coreR * 0.38f, p)
        }
        Box(
            Modifier
                .padding(14.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(c.surface)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = if (reduce) 1f else blink }
                        .clip(CircleShape)
                        .background(c.live),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(7.dp))
                Text(stringResource(R.string.live), style = MaterialTheme.typography.labelMedium, color = c.text)
            }
        }
    }
}

private class PadModel {
    class Ripple(val at: Offset, var t: Float)

    var pos by mutableStateOf(Offset.Zero)
    var pressed by mutableStateOf(false)
    val trail = ArrayList<Offset>()
    private val trailTimes = ArrayList<Long>()
    val ripples = ArrayList<Ripple>()
    var flashT = 0f
    private var last = 0L
    private var now = 0L

    var frame by mutableLongStateOf(0L)
        private set

    fun move(dx: Float, dy: Float) {
        pos = Offset((pos.x + dx).coerceIn(-1f, 1f), (pos.y + dy).coerceIn(-1f, 1f))
        trail += pos
        trailTimes += now
        if (trail.size > 42) {
            trail.removeAt(0); trailTimes.removeAt(0)
        }
    }

    fun press(down: Boolean) {
        pressed = down
        if (down) ripples += Ripple(pos, 0f)
    }

    fun flash() {
        flashT = 1f
        trail.clear(); trailTimes.clear()
    }

    /** Points fade out over ~0.7 s once the pen is still. */
    fun trailFade(i: Int): Float {
        val age = (now - trailTimes[i]) / 1e9f
        return (1f - age / 0.7f).coerceIn(0f, 1f)
    }

    fun tick(t: Long) {
        val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.05f)
        last = t
        now = t
        val ri = ripples.iterator()
        while (ri.hasNext()) {
            val r = ri.next()
            r.t += dt * 2f
            if (r.t >= 1f) ri.remove()
        }
        if (flashT > 0f) flashT = (flashT - dt * 2.2f).coerceAtLeast(0f)
        // A gentle pull home: the pad shows motion, not absolute position, so the cursor
        // never parks against an edge.
        if (!pressed && dt > 0f) pos *= (1f - dt * 0.7f).coerceAtLeast(0f)
        while (trailTimes.isNotEmpty() && (t - trailTimes[0]) / 1e9f > 0.7f) {
            trail.removeAt(0); trailTimes.removeAt(0)
        }
        frame++
    }
}
