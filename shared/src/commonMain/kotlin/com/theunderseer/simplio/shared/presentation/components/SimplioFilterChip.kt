package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.theme.SimplioMotion

/** Flat selection feedback belongs to the whole chip, never to its text label. */
@Composable
fun SimplioFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val base = if (selected) scheme.secondaryContainer else scheme.surface
    val alpha = when {
        !enabled -> 0f
        pressed -> 0.08f
        hovered || focused -> 0.04f
        else -> 0f
    }
    val container by animateColorAsState(
        scheme.onSurface.copy(alpha = alpha).compositeOver(base),
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    val ink = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    val outline = when {
        !enabled -> scheme.outlineVariant
        focused -> scheme.tertiary
        selected -> scheme.primary.copy(alpha = 0.45f)
        else -> scheme.outlineVariant
    }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(container)
            .border(BorderStroke(if (enabled && focused) 2.dp else 1.dp, outline), shape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink.copy(alpha = if (enabled) 1f else 0.38f)) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content = label)
        }
    }
}
