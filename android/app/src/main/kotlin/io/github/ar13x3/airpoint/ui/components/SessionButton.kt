package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.ui.brand.LoopMark
import io.github.ar13x3.airpoint.ui.icons.AirIcons
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.riseTransition

enum class SessionButtonState { Start, Connecting, Stop }

@Composable
fun SessionButton(state: SessionButtonState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Air.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val running = state == SessionButtonState.Stop
    val container by animateColorAsState(if (running) c.surfaceRaised else c.accent, Motion.calm(), label = "container")
    val content by animateColorAsState(if (running) c.text else c.onAccent, Motion.calm(), label = "content")
    val border by animateColorAsState(if (running) c.outlineStrong else c.accent, Motion.calm(), label = "border")

    val reduce = Air.reduceMotion
    val sweep = rememberInfiniteTransition(label = "sweep")
    val sweepX by sweep.animateFloat(
        initialValue = -0.4f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweepX",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScale(interaction, 0.975f)
            .clip(CircleShape)
            .background(container)
            .border(1.dp, border, CircleShape)
            .drawWithContent {
                drawContent()
                if (state == SessionButtonState.Connecting && !reduce) {
                    // A soft band of light travels across while we connect.
                    val x = size.width * sweepX
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                            startX = x - size.width * 0.25f, endX = x + size.width * 0.25f,
                        ),
                    )
                }
            }
            .clickable(interaction, ripple(color = content), role = Role.Button) {
                haptics.performHapticFeedback(if (running) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(state, transitionSpec = { riseTransition() }, label = "label") { s ->
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                when (s) {
                    SessionButtonState.Start -> {
                        LoopMark(Modifier.size(26.dp), color = content)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.action_start), style = MaterialTheme.typography.labelLarge, color = content)
                    }
                    SessionButtonState.Connecting -> {
                        Text(stringResource(R.string.action_connecting), style = MaterialTheme.typography.labelLarge, color = content)
                        Spacer(Modifier.width(6.dp))
                        BouncingDots(content)
                    }
                    SessionButtonState.Stop -> {
                        Icon(AirIcons.Stop, null, Modifier.size(20.dp), tint = c.danger)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.labelLarge, color = content)
                    }
                }
            }
        }
    }
}

@Composable
fun BouncingDots(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dots")
    val reduce = Air.reduceMotion
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val y by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(900, delayMillis = 0, easing = LinearEasing), RepeatMode.Restart),
                label = "dot$i",
            )
            // Each dot hops in turn; phase-shifted triangle wave.
            val phase = ((y + i * 0.18f) % 1f)
            val hop = if (reduce) 0f else (if (phase < 0.5f) phase * 2 else (1 - phase) * 2)
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { translationY = -hop * 5.dp.toPx(); alpha = 0.5f + hop * 0.5f }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
