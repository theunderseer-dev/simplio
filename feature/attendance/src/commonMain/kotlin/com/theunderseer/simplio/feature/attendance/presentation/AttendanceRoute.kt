package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AttendanceRoute(onCopyAttendance: (String) -> Unit, viewModel: AttendanceViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()

    AttendanceScreen(
        state = state,
        onTrackClassAttendance = viewModel::openClassAttendance,
        onCloseClassAttendance = viewModel::closeClassAttendance,
        onSaveClassAttendance = viewModel::saveClassAttendance,
        onRegisterStudent = viewModel::registerStudent,
        onCreatePackageType = viewModel::createPackageType,
        onClearCreatePackageTypeState = viewModel::clearCreatePackageTypeState,
        onCreateStudentGroup = viewModel::createStudentGroup,
        onClearCreateStudentGroupState = viewModel::clearCreateStudentGroupState,
        onRenewStudentPackage = viewModel::renewStudentPackage,
        onClearRenewStudentPackageState = viewModel::clearRenewStudentPackageState,
        onDeleteStudent = viewModel::deleteStudent,
        onCopyAttendance = onCopyAttendance,
        onMarkStudentPresent = viewModel::markStudentPresent,
        onRemoveStudentAttendance = viewModel::removeStudentAttendance,
        onClearMarkStudentPresentState = viewModel::clearMarkStudentPresentState,
        onClearRemoveStudentAttendanceState = viewModel::clearRemoveStudentAttendanceState,
        onLoadNextClassHistoryPage = viewModel::loadNextClassHistoryPage,
        onRetryClassHistory = viewModel::retryClassHistory,
        onUpdateStudentNote = viewModel::updateStudentNote,
        onClearUpdateStudentNoteState = viewModel::clearUpdateStudentNoteState,
    )
}
