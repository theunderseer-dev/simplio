package com.theunderseer.simplio.shared.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.shared.presentation.theme.SimplioMotion

/** Visual feedback only: it does not turn the row into an interactive control. */
@Composable
fun Modifier.simplioHoverSurface(interactionSource: MutableInteractionSource? = null): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val alpha by animateFloatAsState(
        if (hovered) 0.012f else 0f,
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    val scheme = MaterialTheme.colorScheme
    val outline by animateColorAsState(
        if (hovered) scheme.outline.copy(alpha = 0.65f) else scheme.outlineVariant,
        animationSpec = tween(SimplioMotion.FEEDBACK_MILLIS),
    )
    // Animate opacity only. Interpolating from transparent black changes the tint as well,
    // creating a dark flash before the final (much lighter) hover color.
    return hoverable(source).background(
        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        MaterialTheme.shapes.medium,
    ).border(1.dp, outline, MaterialTheme.shapes.medium)
}
