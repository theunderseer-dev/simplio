package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.ic_close
import com.theunderseer.simplio.feature.attendance.resources.ios_delete_student_action
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private enum class StudentSwipeAnchor {
    Closed,
    Revealed,
}

private val studentSwipeAnimation = tween<Float>(durationMillis = 180)
private val reducedMotionSwipeAnimation = snap<Float>()

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun IosStudentSwipeRow(
    studentName: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Boolean,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    foregroundModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animation = if (reduceMotion) reducedMotionSwipeAnimation else studentSwipeAnimation
    val state = rememberStudentSwipeState(expanded, animation, onExpandedChange)
    val isRevealed = state.settledValue == StudentSwipeAnchor.Revealed
    val deleteDescription = stringResource(Res.string.ios_delete_student_action, studentName)
    Box(
        modifier.fillMaxWidth().clipToBounds().anchoredDraggable(
            state = state,
            reverseDirection = false,
            orientation = Orientation.Horizontal,
            enabled = expanded || !isRevealed,
            flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, animationSpec = animation),
        ),
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = AbsoluteAlignment.CenterRight) {
            StudentSwipeDeleteAction(deleteDescription, expanded && isRevealed, onRequestDelete)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val actionSize = minOf(size.width, size.height).toFloat()
                    if (actionSize > 0) {
                        state.updateAnchors(
                            DraggableAnchors {
                                StudentSwipeAnchor.Closed at 0f
                                StudentSwipeAnchor.Revealed at -actionSize
                            },
                        )
                    }
                }
                .absoluteOffset {
                    val offset = state.offset
                    val x = if (offset.isNaN()) 0 else offset.coerceIn(state.anchors.minPosition(), 0f).roundToInt()
                    IntOffset(x, 0)
                }
                .background(MaterialTheme.colorScheme.surface)
                .then(foregroundModifier)
                .semantics(mergeDescendants = true) {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteDescription) {
                            onRequestDelete()
                            true
                        },
                    )
                },
        ) { content() }
    }
}

@Composable
private fun rememberStudentSwipeState(
    expanded: Boolean,
    animation: AnimationSpec<Float>,
    onExpandedChange: (Boolean) -> Boolean,
): AnchoredDraggableState<StudentSwipeAnchor> {
    val target = if (expanded) StudentSwipeAnchor.Revealed else StudentSwipeAnchor.Closed
    val state = remember { AnchoredDraggableState(target) }
    val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)

    LaunchedEffect(state) {
        // Claim the active row as soon as the drag starts showing the action.
        snapshotFlow { state.offset < 0f }.drop(1).collect { shifted ->
            if (!latestOnExpandedChange(shifted)) {
                state.closeSwipe(reducedMotionSwipeAnimation)
            }
        }
    }
    LaunchedEffect(state, expanded, animation) {
        if (!expanded && state.offset < 0f) state.closeSwipe(animation)
    }
    DisposableEffect(state) {
        onDispose { latestOnExpandedChange(false) }
    }
    return state
}

private suspend fun AnchoredDraggableState<StudentSwipeAnchor>.closeSwipe(animation: AnimationSpec<Float>) {
    // A cancelled drag can still post a fling. Closing must take precedence.
    anchoredDrag(StudentSwipeAnchor.Closed, MutatePriority.PreventUserInput) { anchors, anchor ->
        animate(requireOffset(), anchors.positionOf(anchor), animationSpec = animation) { value, velocity ->
            dragTo(value, velocity)
        }
    }
}

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
private fun StudentSwipeDeleteAction(description: String, enabled: Boolean, onClick: () -> Unit) {
    // While dragging, show the action but only expose the tap target once its row is selected.
    val interaction = if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Box(
        Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true)
            .background(MaterialTheme.colorScheme.error)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_close),
            contentDescription = if (enabled) description else null,
            tint = MaterialTheme.colorScheme.onError,
        )
    }
}
