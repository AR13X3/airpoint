package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.Numeric
import kotlin.math.roundToInt

/**
 * A labelled 1..100 slider: the numeric readout rolls as it changes, the thumb swells while
 * dragged, and the phone ticks every ten steps.
 */
@Composable
fun TuningSlider(
    label: String,
    hint: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 1f..100f,
    onValueChangeFinished: () -> Unit = {},
) {
    val c = Air.colors
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(if (dragging) 30.dp else 24.dp, Motion.lively(), label = "thumb")
    val shown = value.roundToInt()

    fun emit(raw: Float) {
        val v = raw.coerceIn(range.start, range.endInclusive).roundToInt().toFloat()
        if (v != current) {
            if ((v / 10).toInt() != (current / 10).toInt()) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onChange(v)
        }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            AnimatedContent(
                shown,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically { if (up) it / 2 else -it / 2 } + fadeIn())
                        .togetherWith(slideOutVertically { if (up) -it / 2 else it / 2 } + fadeOut())
                },
                label = "value",
            ) { v ->
                Text("$v", style = Numeric.copy(fontSize = 22.sp), color = c.accentText)
            }
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = "$shown"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { target -> emit(target); onFinished(); true }
                }
                .pointerInput(range) {
                    detectTapGestures { pos ->
                        emit(range.start + (pos.x / size.width) * (range.endInclusive - range.start))
                        onFinished()
                    }
                }
                .pointerInput(range) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos ->
                            dragging = true
                            emit(range.start + (pos.x / size.width) * (range.endInclusive - range.start))
                        },
                        onDragEnd = { dragging = false; onFinished() },
                        onDragCancel = { dragging = false; onFinished() },
                    ) { change, _ ->
                        change.consume()
                        emit(range.start + (change.position.x / size.width) * (range.endInclusive - range.start))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val fraction = (value - range.start) / (range.endInclusive - range.start)
            val density = LocalDensity.current
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(c.outline, size = size, cornerRadius = r)
                drawRoundRect(c.accent, size = Size(size.width * fraction, size.height), cornerRadius = r)
                // Ten notches make the scale legible without numbers.
                for (i in 1 until 10) {
                    val x = size.width * i / 10f
                    if (x > size.width * fraction) {
                        drawCircle(c.outlineStrong, 1.5.dp.toPx(), Offset(x, size.height / 2))
                    }
                }
            }
            val widthPx = with(density) { maxWidth.toPx() }
            val thumbPx = with(density) { thumb.toPx() }
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                    .size(thumb)
                    .shadow(6.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                    .clip(CircleShape)
                    .background(if (c.isDark) c.text else c.surface)
                    .border(3.dp, c.accent, CircleShape),
            )
        }
    }
}
