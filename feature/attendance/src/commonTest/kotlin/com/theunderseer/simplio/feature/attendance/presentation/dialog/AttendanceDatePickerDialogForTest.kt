package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceDatePickerDialog
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@Composable
internal fun AttendanceDatePickerDialogForTest(
    initialDate: LocalDate = AttendanceTestData.secondClassDate,
    currentDate: LocalDate = AttendanceTestData.currentDate,
    maxVisitsPerAttendance: Int = 1,
    remainingClasses: Int = 3,
    startedOn: LocalDate = AttendanceTestData.firstClassDate,
    expiresOn: LocalDate = startedOn + DatePeriod(
        days = AttendanceTestData.fourClassPackage.validityWeeks * 7,
    ),
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
        startedOn = startedOn,
        expiresOn = expiresOn,
        recordedAttendanceDates = recordedAttendanceDates,
        isSaving = isSaving,
        errorMessage = errorMessage,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
