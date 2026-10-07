package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.Numeric
import io.github.ar13x3.airpoint.ui.theme.Radii

enum class PinState { Idle, Checking, Error, Success }

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    state: PinState,
    modifier: Modifier = Modifier,
    length: Int = 6,
    enabled: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val c = Air.colors
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            PinState.Error -> {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                shake.animateTo(0f, keyframes {
                    durationMillis = 440
                    -16f at 50; 14f at 120; -10f at 190; 7f at 260; -3f at 330; 0f at 440
                })
            }
            PinState.Success -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val wave = rememberInfiniteTransition(label = "checking")
    val t by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "t")
    val caret by wave.animateFloat(0f, 1f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "caret")

    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(length)
            if (digits != value) {
                if (digits.length > value.length) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                onValueChange(digits)
            }
        },
        enabled = enabled,
        // Read-only rather than disabled while checking: disabling drops focus and the keyboard.
        readOnly = state == PinState.Checking || state == PinState.Success,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { translationX = shake.value.dp.toPx() },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions.Default,
        cursorBrush = SolidColor(c.accent),
        singleLine = true,
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(length) { i ->
                    val ch = value.getOrNull(i)
                    val isCaret = focused && i == value.length && state == PinState.Idle
                    val border by animateColorAsState(
                        when {
                            state == PinState.Error -> c.danger
                            state == PinState.Success -> c.live
                            isCaret -> c.accent
                            ch != null -> c.outlineStrong
                            else -> c.outline
                        }, Motion.calm(), label = "border$i",
                    )
                    val fill by animateColorAsState(
                        when (state) {
                            PinState.Error -> c.dangerSoft
                            PinState.Success -> c.liveSoft
                            else -> c.surface
                        }, Motion.calm(), label = "fill$i",
                    )
                    // While checking, a wave runs through the cells.
                    val lift = if (state == PinState.Checking && !Air.reduceMotion) {
                        val phase = ((t - i * 0.09f) % 1f + 1f) % 1f
                        if (phase < 0.3f) kotlin.math.sin(phase / 0.3f * Math.PI).toFloat() else 0f
                    } else 0f
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(58.dp)
                            .graphicsLayer { translationY = -lift * 6.dp.toPx() }
                            .clip(Radii.md)
                            .background(fill)
                            .border(if (isCaret) 2.dp else 1.5.dp, border, Radii.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            ch,
                            transitionSpec = { (scaleIn(Motion.lively(), initialScale = 0.6f) + fadeIn()).togetherWith(fadeOut(tween(90))) },
                            label = "digit$i",
                        ) { d ->
                            if (d != null) {
                                Text(d.toString(), style = Numeric.copy(fontSize = 26.sp), color = c.text)
                            } else if (isCaret) {
                                Box(Modifier.width(2.dp).height(24.dp).graphicsLayer { alpha = caret }.background(c.accent))
                            } else {
                                Box(Modifier.size(6.dp).clip(Radii.sm).background(c.outlineStrong))
                            }
                        }
                    }
                }
            }
        },
    )
}
