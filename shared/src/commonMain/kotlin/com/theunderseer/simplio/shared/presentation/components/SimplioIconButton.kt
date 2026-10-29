package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.theme.SimplioMotion
import com.theunderseer.simplio.shared.presentation.theme.SimplioSpacing
import com.theunderseer.simplio.shared.presentation.theme.positive

enum class SimplioActionTone { Neutral, Positive, Destructive, Accent }

@Composable
fun SimplioIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: SimplioActionTone = SimplioActionTone.Neutral,
    selected: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) = ActionIcon(painter, contentDescription, onClick, modifier, enabled, tone, selected, false, interactionSource)

@Composable
fun SimplioToggleIconButton(
    painter: Painter,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = ActionIcon(
    painter, contentDescription, { onCheckedChange(!checked) }, modifier, enabled,
    if (checked) SimplioActionTone.Positive else SimplioActionTone.Neutral, checked, true, interactionSource,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionIcon(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tone: SimplioActionTone,
    selected: Boolean,
    toggle: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsKeyboardFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val ink = when (tone) {
        SimplioActionTone.Neutral -> scheme.onSurfaceVariant
        SimplioActionTone.Positive -> scheme.positive
        SimplioActionTone.Destructive -> scheme.error
        SimplioActionTone.Accent -> scheme.primary
    }
    val background by animateColorAsState(
        ink.copy(
            alpha = when {
                !enabled -> 0f
                pressed -> 0.12f
                hovered || focused -> if (selected) 0.10f else 0.06f
                selected -> 0.08f
                else -> 0f
            },
        ),
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    val foreground by animateColorAsState(
        if (enabled) ink else scheme.onSurface.copy(alpha = 0.38f),
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    val shape = MaterialTheme.shapes.small
    val target = modifier.size(SimplioSpacing.ActionTarget).border(
        width = 2.dp,
        color = if (enabled && focused) scheme.tertiary else Color.Transparent,
        shape = shape,
    )
    val icon: @Composable () -> Unit = {
        Icon(painter, description, Modifier.size(SimplioSpacing.Icon), tint = foreground)
    }
    // The animated container is the only state layer; Material ripple would draw a second one.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        if (toggle) {
            IconToggleButton(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = target,
                enabled = enabled,
                shape = shape,
                interactionSource = source,
                colors = IconButtonDefaults.iconToggleButtonColors(
                    containerColor = background,
                    checkedContainerColor = background,
                ),
                content = icon,
            )
        } else {
            IconButton(
                onClick,
                target,
                enabled,
                shape = shape,
                interactionSource = source,
                colors = IconButtonDefaults.iconButtonColors(containerColor = background),
                content = icon,
            )
        }
    }
}
