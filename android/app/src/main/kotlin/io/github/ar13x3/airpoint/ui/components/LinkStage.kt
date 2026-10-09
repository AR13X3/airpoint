package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ar13x3.airpoint.core.LinkPhase
import io.github.ar13x3.airpoint.core.PadEvent
import io.github.ar13x3.airpoint.core.SessionState
import io.github.ar13x3.airpoint.core.SpenPhase
import io.github.ar13x3.airpoint.ui.brand.LoopMark
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.AirpointColors
import io.github.ar13x3.airpoint.ui.theme.Motion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlin.math.hypot
import kotlin.random.Random

enum class NodeTone { Idle, Busy, Good, Bad }
enum class WireTone { Off, Busy, Live, Broken }

data class StageModel(
    val pen: NodeTone = NodeTone.Idle,
    val phone: NodeTone = NodeTone.Idle,
    val pc: NodeTone = NodeTone.Idle,
    val penWire: WireTone = WireTone.Off,
    val pcWire: WireTone = WireTone.Off,
)

fun SessionState.toStage(): StageModel {
    if (!active) return StageModel()
    val pen = when (spen.phase) {
        SpenPhase.Off -> NodeTone.Idle
        SpenPhase.Connecting -> NodeTone.Busy
        SpenPhase.Ready -> NodeTone.Good
        SpenPhase.Failed -> NodeTone.Bad
    }
    val pc = when (link.phase) {
        LinkPhase.Idle -> NodeTone.Idle
        LinkPhase.Connecting, LinkPhase.Reconnecting -> NodeTone.Busy
        LinkPhase.Connected -> NodeTone.Good
        LinkPhase.Failed -> NodeTone.Bad
    }
    fun wire(t: NodeTone) = when (t) {
        NodeTone.Idle -> WireTone.Off
        NodeTone.Busy -> WireTone.Busy
        NodeTone.Good -> WireTone.Live
        NodeTone.Bad -> WireTone.Broken
    }
    return StageModel(pen, NodeTone.Good, pc, wire(pen), wire(pc))
}

private val NodeSize = 64.dp
private val NodeColumn = 96.dp

/**
 * S Pen → phone → PC, as three nodes joined by wires. Wires are dotted when off, march while
 * connecting, glow when live and break on failure. While live, real air-motion from [pad]
 * sends particles along the wire, so the stage moves with the user's hand.
 */
@Composable
fun LinkStage(
    model: StageModel,
    pad: Flow<PadEvent>,
    penLabel: String,
    phoneLabel: String,
    pcLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Air.colors
    val reduce = Air.reduceMotion
    val field = remember { ParticleField() }

    val penLive = model.penWire == WireTone.Live
    val pcLive = model.pcWire == WireTone.Live
    LaunchedEffect(penLive, pcLive, reduce) {
        if (!penLive || reduce) {
            field.clear()
            return@LaunchedEffect
        }
        pad.collect { field.onEvent(it, pcLive) }
    }
    LaunchedEffect(penLive, pcLive, reduce) {
        if (!penLive || reduce) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                field.step(dt, pcLive)
            }
        }
    }

    val marching = rememberInfiniteTransition(label = "march")
    val phase by marching.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "phase")

    Box(modifier.fillMaxWidth().height(NodeSize + 34.dp)) {
        Canvas(Modifier.matchParentSize()) {
            field.frame // subscribe to particle frames
            val y = NodeSize.toPx() / 2
            val r = NodeSize.toPx() / 2
            val gap = 6.dp.toPx()
            val xs = floatArrayOf(NodeColumn.toPx() / 2, size.width / 2, size.width - NodeColumn.toPx() / 2)
            drawWire(xs[0] + r + gap, xs[1] - r - gap, y, model.penWire, if (reduce) 0f else phase, c)
            drawWire(xs[1] + r + gap, xs[2] - r - gap, y, model.pcWire, if (reduce) 0f else phase, c)
            field.draw(this, xs[0], xs[2], y, r, c)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StageNode(penLabel, model.pen, goodColor = c.accent) { tint -> Icon(AirIcons.Stylus, null, Modifier.size(26.dp), tint = tint) }
            StageNode(phoneLabel, model.phone, goodColor = c.accent) { tint -> LoopMark(Modifier.size(34.dp), color = tint) }
            StageNode(pcLabel, model.pc, goodColor = c.live) { tint -> Icon(AirIcons.Monitor, null, Modifier.size(26.dp), tint = tint) }
        }
    }
}

@Composable
private fun StageNode(label: String, tone: NodeTone, goodColor: Color, glyph: @Composable (Color) -> Unit) {
    val c = Air.colors
    val reduce = Air.reduceMotion
    val border by animateColorAsState(
        when (tone) {
            NodeTone.Idle -> c.outlineStrong
            NodeTone.Busy -> c.accent
            NodeTone.Good -> goodColor
            NodeTone.Bad -> c.warn
        }, Motion.calm(), label = "border",
    )
    val tint by animateColorAsState(
        when (tone) {
            NodeTone.Idle -> c.textTertiary
            NodeTone.Busy -> c.accentText
            NodeTone.Good -> if (goodColor == c.live) c.live else c.accentText
            NodeTone.Bad -> c.warn
        }, Motion.calm(), label = "tint",
    )
    val scale by animateFloatAsState(if (tone == NodeTone.Good) 1f else 0.92f, Motion.lively(), label = "scale")

    Column(Modifier.width(NodeColumn), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(NodeSize), contentAlignment = Alignment.Center) {
            if (tone == NodeTone.Busy && !reduce) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing)), label = "t")
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { scaleX = 1f + t * 0.5f; scaleY = 1f + t * 0.5f; alpha = (1f - t) * 0.6f }
                        .border(2.dp, c.accent, CircleShape),
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(c.surfaceRaised)
                    .border(1.5.dp, border, CircleShape),
                contentAlignment = Alignment.Center,
            ) { glyph(tint) }
            StatusBadge(tone, goodColor, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (tone == NodeTone.Idle) c.textTertiary else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Check or "!" in the node's corner. Top-level so AnimatedVisibility isn't the scoped overload. */
@Composable
private fun StatusBadge(tone: NodeTone, goodColor: Color, modifier: Modifier) {
    val c = Air.colors
    AnimatedVisibility(
        tone == NodeTone.Good || tone == NodeTone.Bad,
        modifier = modifier,
        enter = scaleIn(Motion.lively()) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        val badge = if (tone == NodeTone.Bad) c.warn else goodColor
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(badge).border(2.5.dp, c.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (tone == NodeTone.Bad) {
                Text("!", color = c.background, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            } else {
                Icon(AirIcons.Check, null, Modifier.size(12.dp), tint = if (c.isDark) c.background else Color.White)
            }
        }
    }
}

private fun DrawScope.drawWire(x0: Float, x1: Float, y: Float, tone: WireTone, phase: Float, c: AirpointColors) {
    if (x1 <= x0) return
    val w = 2.dp.toPx()
    val a = Offset(x0, y)
    val b = Offset(x1, y)
    when (tone) {
        WireTone.Off -> drawLine(
            c.outlineStrong, a, b, w, StrokeCap.Round,
            PathEffect.dashPathEffect(floatArrayOf(0.01f, 7.dp.toPx())),
        )
        WireTone.Busy -> {
            val on = 9.dp.toPx(); val off = 7.dp.toPx()
            drawLine(
                c.accent.copy(alpha = 0.85f), a, b, w, StrokeCap.Round,
                PathEffect.dashPathEffect(floatArrayOf(on, off), -phase * (on + off)),
            )
        }
        WireTone.Live -> drawLine(c.accent.copy(alpha = 0.55f), a, b, w, StrokeCap.Round)
        WireTone.Broken -> {
            val mid = (x0 + x1) / 2
            val gap = 9.dp.toPx()
            drawLine(c.warn.copy(alpha = 0.6f), a, Offset(mid - gap, y), w, StrokeCap.Round)
            drawLine(c.warn.copy(alpha = 0.6f), Offset(mid + gap, y), b, w, StrokeCap.Round)
        }
    }
}

/** A tiny particle system, advanced from the frame clock and drawn by the stage's Canvas. */
private class ParticleField {
    class Particle(var pos: Float, val speed: Float, val click: Boolean, var life: Float = 1f)
    class Ring(val node: Int, var t: Float, val live: Boolean)

    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private var energy = 0f
    private var sinceSpawn = 0f

    var frame by mutableLongStateOf(0L)
        private set

    fun clear() {
        particles.clear(); rings.clear(); energy = 0f
        frame++
    }

    fun onEvent(e: PadEvent, pcLive: Boolean) {
        when (e) {
            is PadEvent.Motion -> {
                energy += hypot(e.dx, e.dy) * 6f
                while (energy >= 1f) {
                    energy -= 1f
                    spawn(click = false)
                }
            }
            is PadEvent.Button -> if (e.pressed) {
                spawn(click = true)
                rings += Ring(0, 0f, live = true)
            }
            PadEvent.Center -> if (pcLive) rings += Ring(2, 0f, live = false)
        }
    }

    private fun spawn(click: Boolean) {
        if (particles.size >= 28) return
        particles += Particle(0f, if (click) 0.75f else 0.5f + Random.nextFloat() * 0.25f, click)
        sinceSpawn = 0f
    }

    fun step(dt: Float, pcLive: Boolean) {
        sinceSpawn += dt
        // A slow heartbeat keeps a live link looking alive when the pen is still.
        if (sinceSpawn > 1.8f) spawn(click = false)
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.pos += p.speed * dt
            if (!pcLive && p.pos > 0.5f) p.life -= dt * 6f // absorbed by the phone
            if (p.pos >= 1f) {
                if (p.click) rings += Ring(2, 0f, live = true)
                it.remove()
            } else if (p.life <= 0f) it.remove()
        }
        val ri = rings.iterator()
        while (ri.hasNext()) {
            val r = ri.next()
            r.t += dt * 1.6f
            if (r.t >= 1f) ri.remove()
        }
        frame++
    }

    fun draw(scope: DrawScope, x0: Float, x1: Float, y: Float, nodeR: Float, c: AirpointColors) = with(scope) {
        for (p in particles) {
            val x = x0 + (x1 - x0) * p.pos
            // Hidden while passing behind a node, so it reads as travelling through the phone.
            val color = if (p.click) c.live else c.accent
            val core = (if (p.click) 4.5f else 3f).dp.toPx()
            drawCircle(color.copy(alpha = 0.18f * p.life), core * 3.2f, Offset(x, y))
            drawCircle(color.copy(alpha = p.life), core, Offset(x, y))
        }
        val xs = floatArrayOf(x0, (x0 + x1) / 2, x1)
        for (r in rings) {
            val color = if (r.live) c.live else c.accent
            drawCircle(
                color.copy(alpha = (1f - r.t) * 0.8f),
                nodeR + r.t * 18.dp.toPx(),
                Offset(xs[r.node], y),
                style = Stroke(2.dp.toPx() * (1f - r.t * 0.5f)),
            )
        }
    }
}
