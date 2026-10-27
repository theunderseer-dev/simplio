package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_add_student
import com.theunderseer.simplio.feature.attendance.resources.class_attendance_for_student
import com.theunderseer.simplio.feature.attendance.resources.class_attendance_title
import com.theunderseer.simplio.feature.attendance.resources.class_cancel
import com.theunderseer.simplio.feature.attendance.resources.class_cancel_confirmation
import com.theunderseer.simplio.feature.attendance.resources.class_cancel_has_attendance
import com.theunderseer.simplio.feature.attendance.resources.class_cancel_title
import com.theunderseer.simplio.feature.attendance.resources.class_close
import com.theunderseer.simplio.feature.attendance.resources.class_continue
import com.theunderseer.simplio.feature.attendance.resources.class_discard
import com.theunderseer.simplio.feature.attendance.resources.class_discard_confirmation
import com.theunderseer.simplio.feature.attendance.resources.class_discard_title
import com.theunderseer.simplio.feature.attendance.resources.class_draft_stale
import com.theunderseer.simplio.feature.attendance.resources.class_independent_saved
import com.theunderseer.simplio.feature.attendance.resources.class_invalid_selection
import com.theunderseer.simplio.feature.attendance.resources.class_keep
import com.theunderseer.simplio.feature.attendance.resources.class_no_students
import com.theunderseer.simplio.feature.attendance.resources.class_package_progress
import com.theunderseer.simplio.feature.attendance.resources.class_package_required
import com.theunderseer.simplio.feature.attendance.resources.class_people_count
import com.theunderseer.simplio.feature.attendance.resources.class_renew_student
import com.theunderseer.simplio.feature.attendance.resources.class_retry
import com.theunderseer.simplio.feature.attendance.resources.class_save
import com.theunderseer.simplio.feature.attendance.resources.class_save_failed
import com.theunderseer.simplio.feature.attendance.resources.class_saving
import com.theunderseer.simplio.feature.attendance.resources.class_track_attendance
import com.theunderseer.simplio.feature.attendance.resources.class_unavailable
import com.theunderseer.simplio.feature.attendance.resources.ic_refresh
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClassAttendanceDialog(
    state: ClassAttendanceUiState,
    onClose: () -> Unit,
    onRetry: (String) -> Unit,
    onSave: (Map<Int, Int>, Boolean) -> Unit,
    onSelect: (Int, Int) -> Unit,
    onAddStudent: () -> Unit,
    onRenewStudent: (Int) -> Unit,
) {
    if (state == ClassAttendanceUiState.Closed) return
    if (state !is ClassAttendanceUiState.Content) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(Res.string.class_track_attendance)) },
            text = {
                if (state is ClassAttendanceUiState.LoadError) {
                    Text(stringResource(Res.string.class_unavailable))
                } else {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {
                if (state is ClassAttendanceUiState.LoadError) {
                    TextButton(onClick = { onRetry(state.sessionId) }) {
                        Text(stringResource(Res.string.class_retry))
                    }
                }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.class_close)) } },
        )
        return
    }
    val sheet = state.sheet
    val selected = state.visitsByStudent
    var confirmingCancel by remember(sheet) { mutableStateOf(false) }
    var confirmingDiscard by remember(sheet) { mutableStateOf(false) }
    val close = {
        if (!state.isSaving) {
            if (state.hasDraftChanges) confirmingDiscard = true else onClose()
        }
    }
    AlertDialog(
        modifier = Modifier.widthIn(max = 720.dp).testTag("class-attendance-dialog"),
        onDismissRequest = close,
        title = { Text(stringResource(Res.string.class_attendance_title, sheet.session.date.toString())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sheet.timeLabel, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAddStudent, enabled = !state.isSaving) {
                    Text(stringResource(Res.string.class_add_student))
                }
                if (state.hasIndependentChanges) {
                    Text(stringResource(Res.string.class_independent_saved), style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sheet.rows.isEmpty()) item { Text(stringResource(Res.string.class_no_students)) }
                    items(sheet.rows, key = { it.student.id }) { row ->
                        val id = row.student.id
                        val description = stringResource(Res.string.class_attendance_for_student, row.student.fullName)
                        val count = selected[id] ?: 0
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.student.fullName)
                                    val pack = row.studentPackage
                                    if (pack != null) {
                                        Text(
                                            stringResource(
                                                Res.string.class_package_progress,
                                                pack.usedClasses - row.recordedVisitCount + count,
                                                pack.packageType.totalClasses,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (!row.canRecord && !row.canRenew) {
                                        Text(
                                            stringResource(Res.string.class_package_required),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (row.canRenew) {
                                    IconButton(
                                        onClick = { onRenewStudent(id) },
                                        enabled = !state.isSaving,
                                        modifier = Modifier.testTag("class-renew-student-$id"),
                                    ) {
                                        Icon(
                                            painterResource(Res.drawable.ic_refresh),
                                            stringResource(Res.string.class_renew_student, row.student.fullName),
                                        )
                                    }
                                } else {
                                    Checkbox(
                                        checked = count > 0,
                                        enabled = row.canRecord && !state.isSaving,
                                        onCheckedChange = { checked ->
                                            onSelect(id, if (checked) 1 else 0)
                                        },
                                        modifier = Modifier.testTag("class-attendance-student-$id").semantics {
                                            contentDescription =
                                                description
                                        },
                                    )
                                }
                            }
                            if (row.canRecord && row.studentPackage?.packageType?.maxVisitsPerAttendance == 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (1..2).forEach { people ->
                                        FilterChip(
                                            selected = count == people,
                                            enabled = !state.isSaving && count > 0 && people <= row.maximumVisitCount,
                                            onClick = { onSelect(id, people) },
                                            label = {
                                                Text(
                                                    pluralStringResource(
                                                        Res.plurals.class_people_count,
                                                        people,
                                                        people,
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.testTag("class-attendance-people-$id-$people"),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                if (state.problem != null || state.saveFailed) {
                    Text(
                        stringResource(
                            when (state.problem) {
                                ClassAttendanceProblem.STALE -> Res.string.class_draft_stale
                                ClassAttendanceProblem.HAS_ATTENDANCE -> Res.string.class_cancel_has_attendance
                                ClassAttendanceProblem.INVALID_SELECTION -> Res.string.class_invalid_selection
                                ClassAttendanceProblem.UNAVAILABLE, ClassAttendanceProblem.CANCELLED ->
                                    Res.string.class_unavailable
                                null -> Res.string.class_save_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("class-attendance-error"),
                    )
                }
                TextButton(
                    onClick = { confirmingCancel = true },
                    enabled = sheet.canCancel && selected.isEmpty() && !state.isSaving,
                ) { Text(stringResource(Res.string.class_cancel)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected, false) }, enabled = !state.isSaving) {
                Text(stringResource(if (state.isSaving) Res.string.class_saving else Res.string.class_save))
            }
        },
        dismissButton = {
            TextButton(onClick = close, enabled = !state.isSaving) {
                Text(stringResource(Res.string.class_close))
            }
        },
    )
    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text(stringResource(Res.string.class_cancel_title)) },
            text = { Text(stringResource(Res.string.class_cancel_confirmation)) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("confirm-class-cancel"), onClick = {
                    confirmingCancel =
                        false
                    onSave(emptyMap(), true)
                }) {
                    Text(stringResource(Res.string.class_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCancel = false }) {
                    Text(stringResource(Res.string.class_keep))
                }
            },
        )
    }
    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(Res.string.class_discard_title)) },
            text = { Text(stringResource(Res.string.class_discard_confirmation)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.class_discard)) } },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) {
                    Text(stringResource(Res.string.class_continue))
                }
            },
        )
    }
}
