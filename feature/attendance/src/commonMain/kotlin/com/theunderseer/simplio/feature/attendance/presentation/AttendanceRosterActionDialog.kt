package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.coroutines.launch
import kotlin.time.Instant

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun AttendanceRosterActionDialog(
    row: StudentAttendanceUiState,
    roster: AttendanceRoster,
    now: Instant,
    onEdit: suspend (AttendanceRosterEdit) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember(row) { mutableStateOf(false) }
    var error by remember(row) { mutableStateOf<String?>(null) }
    val reviewed = row.registeredStudent
    val groupId = reviewed.student.groupId
    val today = attendanceDatesForGroups(listOf(groupId), roster.groupSchedules, now).getValue(groupId)
    val initialDate = row.effectiveClassDate ?: latestScheduledClassDate(today)

    fun save(edit: AttendanceRosterEdit) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                val failure = onEdit(edit)
                if (failure == null) onDismiss() else error = failure
            } finally {
                saving = false
            }
        }
    }

    when {
        row.requiresRenewal -> StudentPackageRenewalDialog(
            registeredStudent = reviewed,
            studentGroups = roster.studentGroups,
            packageTypes = roster.packageTypes,
            currentDate = today,
            isSaving = saving,
            errorMessage = error,
            groupSchedules = roster.groupSchedules,
            onRenew = { template, date, visits, record, note ->
                save(AttendanceRosterEdit.Renew(reviewed, date, template, visits, record, note))
            },
            onDismiss = onDismiss,
        )
        row.checkState == AttendanceCheckState.Checked -> AttendanceRemovalDialog(
            studentName = reviewed.student.fullName,
            attendanceDate = initialDate,
            isSaving = saving,
            errorMessage = error,
            onConfirm = { save(AttendanceRosterEdit.Remove(reviewed, initialDate)) },
            onDismiss = onDismiss,
        )
        else -> StudentAttendanceDateDialog(
            registeredStudent = reviewed,
            initialDate = initialDate,
            currentDate = today,
            schedules = roster.groupSchedules[groupId],
            isSaving = saving,
            errorMessage = error,
            onConfirm = { date, visits -> save(AttendanceRosterEdit.Mark(reviewed, date, visits)) },
            onDismiss = onDismiss,
        )
    }
}
