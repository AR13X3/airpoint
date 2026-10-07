package io.github.ar13x3.airpoint.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens. Most UI moves on springs so interrupted animations stay continuous. Easing
 * curves are reserved for choreographed reveals (the Loop writing itself, page enters).
 */
object Motion {
    /** Settles quickly with no overshoot: layout, colour and size changes. */
    fun <T> calm(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 380f)

    /** A little overshoot for things the user directly caused: presses, toggles, success. */
    fun <T> lively(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.62f, stiffness = 520f)

    /** Soft and slow for large surfaces entering. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f)

    val offsetCalm = spring(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = IntOffset(1, 1))

    /** Expressive in-out curve used for the write-on reveal and page choreography. */
    val Draw = CubicBezierEasing(0.65f, 0f, 0.25f, 1f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val SHORT = 160
    const val MEDIUM = 320
    const val LONG = 560
}

/** Text that changes in place: the new line rises in as the old one lifts away. */
fun <S> AnimatedContentTransitionScope<S>.riseTransition(): ContentTransform =
    (slideInVertically(Motion.offsetCalm) { it / 2 } + fadeIn(tween(Motion.MEDIUM, delayMillis = 60)) + scaleIn(initialScale = 0.98f))
        .togetherWith(slideOutVertically(Motion.offsetCalm) { -it / 3 } + fadeOut(tween(Motion.SHORT)))
