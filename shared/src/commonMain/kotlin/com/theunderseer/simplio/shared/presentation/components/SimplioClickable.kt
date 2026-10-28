package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.theme.SimplioMotion

/** One rounded state layer for links and read-only field overlays, with no second ripple. */
@Composable
fun Modifier.simplioClickable(
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    interactionSource: MutableInteractionSource? = null,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    return controlFeedback(source, enabled, shape)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/** Checkbox rows share the same clipped state layer and keep a single toggle target. */
@Composable
fun Modifier.simplioToggleable(
    checked: Boolean,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    interactionSource: MutableInteractionSource? = null,
    onValueChange: (Boolean) -> Unit,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    return controlFeedback(source, enabled, shape)
        .toggleable(
            value = checked,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onValueChange,
        )
}

@Composable
private fun Modifier.controlFeedback(source: MutableInteractionSource, enabled: Boolean, shape: Shape): Modifier {
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        when {
            !enabled -> 0f
            pressed -> 0.08f
            hovered || focused -> 0.04f
            else -> 0f
        },
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    return clip(shape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        .border(2.dp, if (enabled && focused) MaterialTheme.colorScheme.tertiary else Color.Transparent, shape)
}
