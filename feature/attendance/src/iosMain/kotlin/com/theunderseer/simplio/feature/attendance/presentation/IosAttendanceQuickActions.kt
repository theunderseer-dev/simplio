package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_add_package
import com.theunderseer.simplio.feature.attendance.resources.class_add_student
import com.theunderseer.simplio.feature.attendance.resources.ios_add_actions
import com.theunderseer.simplio.feature.attendance.resources.ios_add_schedule
import com.theunderseer.simplio.feature.attendance.resources.ios_close_add_actions
import com.theunderseer.simplio.feature.attendance.resources.ios_dismiss_add_actions
import org.jetbrains.compose.resources.stringResource

private val quickActionTriggerSize = 56.dp
private val quickActionMargin = 16.dp
internal val quickActionListClearance = quickActionTriggerSize + quickActionMargin * 2

// The column is laid out top to bottom, with the first action closest to the plus button.
private val quickActionLabels = listOf(
    Res.string.ios_add_schedule,
    Res.string.class_add_package,
    Res.string.class_add_student,
)

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun IosAttendanceQuickActions(
    expanded: Boolean,
    reduceMotion: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (expanded) {
            val description = stringResource(Res.string.ios_dismiss_add_actions)
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f))
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        onExpandedChange(false)
                    }
                    .semantics { contentDescription = description },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(quickActionMargin)
                .widthIn(min = 156.dp, max = 240.dp)
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickActionLabels.forEachIndexed { index, label ->
                val delay = if (reduceMotion) 0 else (quickActionLabels.lastIndex - index) * 55
                val slide = if (reduceMotion) {
                    EnterTransition.None
                } else {
                    slideInVertically(tween(durationMillis = 180, delayMillis = delay)) { it / 4 }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(durationMillis = if (reduceMotion) 100 else 180, delayMillis = delay)) + slide,
                    exit = fadeOut(tween(durationMillis = 110)),
                ) {
                    Button(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = expanded,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                    ) { Text(stringResource(label)) }
                }
            }
            QuickActionTrigger(expanded, reduceMotion, onClick = { onExpandedChange(!expanded) })
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun QuickActionTrigger(expanded: Boolean, reduceMotion: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (expanded) Res.string.ios_close_add_actions else Res.string.ios_add_actions)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(160),
    )
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(quickActionTriggerSize).semantics { contentDescription = description },
        shape = CircleShape,
    ) {
        val ink = LocalContentColor.current
        Canvas(Modifier.size(18.dp).rotate(rotation)) {
            val stroke = 2.dp.toPx()
            drawLine(ink, Offset(0f, center.y), Offset(size.width, center.y), stroke, StrokeCap.Round)
            drawLine(ink, Offset(center.x, 0f), Offset(center.x, size.height), stroke, StrokeCap.Round)
        }
    }
}
