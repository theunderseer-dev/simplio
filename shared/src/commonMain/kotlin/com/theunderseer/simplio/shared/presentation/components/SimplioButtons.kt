package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.theme.SimplioMotion
import com.theunderseer.simplio.shared.presentation.theme.SimplioSpacing

@Composable
fun SimplioButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = StyledButton(onClick, modifier, enabled, ButtonKind.Primary, interactionSource, content)

@Composable
fun SimplioOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = StyledButton(onClick, modifier, enabled, ButtonKind.Secondary, interactionSource, content)

@Composable
fun SimplioTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = StyledButton(onClick, modifier, enabled, ButtonKind.Text, interactionSource, content)

private enum class ButtonKind { Primary, Secondary, Text }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    kind: ButtonKind,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val emphasized = enabled && (hovered || focused || pressed)
    val stateAlpha = when {
        !emphasized -> 0f
        pressed -> 0.08f
        else -> 0.04f
    }
    val container by animateColorAsState(
        when {
            kind == ButtonKind.Primary && emphasized -> scheme.secondary
            kind == ButtonKind.Primary -> scheme.primary
            kind == ButtonKind.Secondary -> scheme.onSurface.copy(
                alpha = stateAlpha,
            ).compositeOver(scheme.surfaceContainerLowest)
            else -> scheme.onSurface.copy(
                alpha = stateAlpha,
            )
        },
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    val target = modifier.defaultMinSize(minHeight = SimplioSpacing.ActionTarget)
    val shape = MaterialTheme.shapes.small
    val colors = ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = if (kind == ButtonKind.Primary) scheme.onPrimary else scheme.primary,
        disabledContainerColor = if (kind == ButtonKind.Primary) {
            scheme.onSurface.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
    )
    // The animated container is the only state layer; Material ripple would draw a second one.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        when (kind) {
            ButtonKind.Primary -> Button(
                onClick,
                target,
                enabled,
                shape = shape,
                colors = colors,
                interactionSource = source,
                border = if (enabled && focused) BorderStroke(2.dp, scheme.onPrimary) else null,
                content = content,
            )
            ButtonKind.Secondary -> OutlinedButton(
                onClick,
                target,
                enabled,
                shape = shape,
                colors = colors,
                interactionSource = source,
                border = BorderStroke(
                    if (enabled && focused) 2.dp else 1.dp,
                    if (enabled && focused) scheme.tertiary else scheme.outlineVariant,
                ),
                content = content,
            )
            ButtonKind.Text -> TextButton(
                onClick,
                target,
                enabled,
                shape = shape,
                colors = colors,
                interactionSource = source,
                border = if (enabled && focused) BorderStroke(2.dp, scheme.tertiary) else null,
                content = content,
            )
        }
    }
}
