package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
internal fun AttendanceDatePickerDialogForTest(
    initialDate: LocalDate = LocalDate(2026, 10, 5),
    currentDate: LocalDate = LocalDate(2026, 10, 8),
    maxVisitsPerAttendance: Int = 1,
    remainingClasses: Int = 3,
    recordedAttendanceDates: Set<LocalDate> = emptySet(),
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onConfirm: (LocalDate, Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
) {
    AttendanceDatePickerDialog(
        initialDate = initialDate,
        currentDate = currentDate,
        maxVisitsPerAttendance = maxVisitsPerAttendance,
        remainingClasses = remainingClasses,
        recordedAttendanceDates = recordedAttendanceDates,
        isSaving = isSaving,
        errorMessage = errorMessage,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
