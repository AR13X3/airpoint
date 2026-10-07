package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion
import io.github.ar13x3.airpoint.ui.theme.Radii
import androidx.compose.material3.MaterialTheme

/** Shrinks slightly while pressed, then springs back, so taps feel physical. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.lively(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades and rises into place on first composition, staggered by [index]. */
@Composable
fun Modifier.enterStagger(index: Int, distance: Dp = 18.dp): Modifier {
    if (Air.reduceMotion) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(Motion.LONG, delayMillis = 70 * index, easing = Motion.Emphasized))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance.toPx()
    }
}

@Composable
fun AirCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radii.lg,
    color: Color = Air.colors.surface,
    padding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Air.colors
    val interaction = remember { MutableInteractionSource() }
    val base = modifier
        .then(if (onClick != null) Modifier.pressScale(interaction, 0.985f) else Modifier)
        .clip(shape)
        .background(color)
        .border(BorderStroke(1.dp, c.outline), shape)
    Column(
        (if (onClick != null) base.clickable(interaction, ripple(color = c.text), role = Role.Button, onClick = onClick) else base)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 4.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Air.colors.textTertiary,
    )
}

/** A soft rounded tile holding an icon. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Air.colors.text, background: Color = Air.colors.tile, size: Dp = 44.dp) {
    Box(modifier.size(size).clip(Radii.sm).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

enum class ButtonStyle { Primary, Tonal, Neutral, Ghost }

@Composable
fun AirButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    val c = Air.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val (container, content) = when (style) {
        ButtonStyle.Primary -> c.accent to c.onAccent
        ButtonStyle.Tonal -> c.accentSoft to c.accentText
        ButtonStyle.Neutral -> c.surfaceRaised to c.text
        ButtonStyle.Ghost -> Color.Transparent to c.accentText
    }
    Row(
        modifier
            .pressScale(interaction)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(container)
            .then(if (style == ButtonStyle.Neutral) Modifier.border(1.dp, c.outline, CircleShape) else Modifier)
            .clickable(interaction, ripple(color = content), enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .heightIn(min = if (compact) 40.dp else 52.dp)
            .padding(horizontal = if (compact) 16.dp else 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(if (compact) 18.dp else 20.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, null, Modifier.size(if (compact) 18.dp else 20.dp), tint = content)
        }
    }
}

/** A tappable row: leading tile, title and subtitle, trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Air.colors.text,
    iconBackground: Color = Air.colors.tile,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Air.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radii.md)
            .then(
                if (onClick != null) Modifier.clickable(interaction, ripple(color = c.text), role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, tint = iconTint, background = iconBackground)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        trailing()
    }
}

/** A small rounded status chip. */
@Composable
fun Chip(text: String, color: Color, background: Color, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
