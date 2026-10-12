package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceScreen
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate

@Composable
internal fun AttendanceScreenForTest(
    state: AttendanceUiState,
    onRegisterStudent: (String, Int, PackageType, LocalDate, Int) -> Unit = { _, _, _, _, _ -> },
    onDeleteStudent: (Int) -> Unit = {},
    onCopyAttendance: (String) -> Unit = {},
    onMarkStudentPresent: (Int, LocalDate, Int) -> Unit = { _, _, _ -> },
    onRemoveStudentAttendance: (Int, LocalDate) -> Unit = { _, _ -> },
    onClearMarkStudentPresentState: () -> Unit = {},
    onClearRemoveStudentAttendanceState: () -> Unit = {},
) {
    AttendanceScreen(
        state = state,
        onRegisterStudent = onRegisterStudent,
        onDeleteStudent = onDeleteStudent,
        onCopyAttendance = onCopyAttendance,
        onMarkStudentPresent = onMarkStudentPresent,
        onRemoveStudentAttendance = onRemoveStudentAttendance,
        onClearMarkStudentPresentState = onClearMarkStudentPresentState,
        onClearRemoveStudentAttendanceState = onClearRemoveStudentAttendanceState,
    )
}
