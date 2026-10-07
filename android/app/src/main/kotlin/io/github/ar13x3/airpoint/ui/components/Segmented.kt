package io.github.ar13x3.airpoint.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ar13x3.airpoint.ui.theme.Air
import io.github.ar13x3.airpoint.ui.theme.Motion

/** A segmented control whose selection indicator glides between options. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    modifier: Modifier = Modifier,
) {
    val c = Air.colors
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(c.surfaceSunken).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(segment * index, Motion.lively(), label = "indicator")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(c.surfaceRaised),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { option ->
                val isSelected = option == selected
                val tint by animateColorAsState(if (isSelected) c.text else c.textSecondary, Motion.calm(), label = "tint")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .semantics { this.selected = isSelected }
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                            if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(option)
                        },
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon(option), null, Modifier.size(18.dp), tint = tint)
                    Spacer(Modifier.width(6.dp))
                    Text(label(option), style = MaterialTheme.typography.titleSmall, color = tint)
                }
            }
        }
    }
}
