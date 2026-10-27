package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.Composable
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceScreen
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate

@Composable
internal fun AttendanceScreenForTest(
    state: AttendanceUiState,
    onRegisterStudent: (String, Int, PackageType, LocalDate, Int, Boolean, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteStudent: (Int) -> Unit = {},
    onCopyAttendance: (String) -> Unit = {},
    onMarkStudentPresent: (Int, LocalDate, Int) -> Unit = { _, _, _ -> },
    onRemoveStudentAttendance: (Int, LocalDate) -> Unit = { _, _ -> },
    onClearMarkStudentPresentState: () -> Unit = {},
    onClearRemoveStudentAttendanceState: () -> Unit = {},
    onLoadNextClassHistoryPage: () -> Unit = {},
    onRetryClassHistory: () -> Unit = {},
    onUpdateStudentNote: (Int, String) -> Unit = { _, _ -> },
    onClearUpdateStudentNoteState: () -> Unit = {},
    onRenewStudentPackage: (
        Int,
        Int,
        PackageType,
        LocalDate,
        Int,
        Boolean,
        String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onClearRenewStudentPackageState: () -> Unit = {},
    onCreatePackageType: (CreatePackageTypeRequest) -> Unit = {},
    onClearCreatePackageTypeState: () -> Unit = {},
    onTrackClassAttendance: (String) -> Unit = {},
    onCloseClassAttendance: () -> Unit = {},
    onSaveClassAttendance: (Map<Int, Int>, Boolean) -> Unit = { _, _ -> },
    onCreateStudentGroup: (CreateStudentGroupRequest) -> Unit = {},
) {
    AttendanceScreen(
        state = state,
        onTrackClassAttendance = onTrackClassAttendance,
        onCloseClassAttendance = onCloseClassAttendance,
        onSaveClassAttendance = onSaveClassAttendance,
        onCreateStudentGroup = onCreateStudentGroup,
        onCreatePackageType = onCreatePackageType,
        onClearCreatePackageTypeState = onClearCreatePackageTypeState,
        onRegisterStudent = onRegisterStudent,
        onRenewStudentPackage = onRenewStudentPackage,
        onClearRenewStudentPackageState = onClearRenewStudentPackageState,
        onDeleteStudent = onDeleteStudent,
        onCopyAttendance = onCopyAttendance,
        onMarkStudentPresent = onMarkStudentPresent,
        onRemoveStudentAttendance = onRemoveStudentAttendance,
        onClearMarkStudentPresentState = onClearMarkStudentPresentState,
        onClearRemoveStudentAttendanceState = onClearRemoveStudentAttendanceState,
        onLoadNextClassHistoryPage = onLoadNextClassHistoryPage,
        onRetryClassHistory = onRetryClassHistory,
        onUpdateStudentNote = onUpdateStudentNote,
        onClearUpdateStudentNoteState = onClearUpdateStudentNoteState,
    )
}
