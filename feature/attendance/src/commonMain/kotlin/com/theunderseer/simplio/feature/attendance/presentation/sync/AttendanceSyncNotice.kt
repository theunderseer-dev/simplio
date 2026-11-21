package com.theunderseer.simplio.feature.attendance.presentation.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncStatus
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictReviewState
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import kotlinx.coroutines.launch

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
fun AttendanceSyncNotice(sync: AttendanceSyncCoordinator) {
    val status by sync.status.collectAsState()
    val review by sync.review.collectAsState()
    val scope = rememberCoroutineScope()
    SyncConflictReviewDialog(
        state = review,
        onDismiss = sync::dismissReview,
        onRetry = { scope.launch { sync.openReview() } },
        onApply = { id, choices -> scope.launch { sync.applyReview(id, choices) } },
        onApplyRenamed = { id, choices, names -> scope.launch { sync.applyReview(id, choices, names) } },
    )
    val problem = (status as? AttendanceSyncStatus.Paused)?.problem
    val message = when {
        status is AttendanceSyncStatus.WaitingToRetry ->
            "Connection or server issue. Changes saved on this device. Retrying automatically."

        problem == UploadProblem.CONFLICT -> "Cloud and local changes need review. Changes saved on this device."

        problem == UploadProblem.AUTHENTICATION -> "Sign in again to sync changes. Local changes are saved."

        problem == UploadProblem.PERMISSION -> "Cloud access denied. Local changes are saved."

        problem == UploadProblem.TOO_LARGE -> "Some data is too large to sync. Local changes are saved."

        problem == UploadProblem.TARGET_MISMATCH ->
            "This database belongs to another cloud account or project. Sync paused."

        problem != null -> "Sync paused. Local changes are saved."

        else -> null
    }
    if (message != null) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (problem == UploadProblem.CONFLICT) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SimplioTextButton(
                        onClick = { scope.launch { sync.openReview() } },
                        enabled = review == ConflictReviewState.Closed,
                    ) { Text("Review changes") }
                    SimplioTextButton(onClick = sync::retry, enabled = review == ConflictReviewState.Closed) {
                        Text("Retry sync")
                    }
                }
            }
            Text(
                message,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (problem in setOf(
                    UploadProblem.AUTHENTICATION,
                    UploadProblem.PERMISSION,
                    UploadProblem.SERVICE,
                    UploadProblem.INVALID_DATA,
                    UploadProblem.TOO_LARGE,
                )
            ) {
                SimplioTextButton(onClick = sync::retry) { Text("Retry sync") }
            }
        }
    }
}
