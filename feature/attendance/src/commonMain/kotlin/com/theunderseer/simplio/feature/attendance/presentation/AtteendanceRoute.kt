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
        onRegisterStudent = viewModel::registerStudent,
        onDeleteStudent = viewModel::deleteStudent,
        onCopyAttendance = onCopyAttendance,
        onMarkStudentPresent = viewModel::markStudentPresent,
        onRemoveStudentAttendance = viewModel::removeStudentAttendance,
        onClearMarkStudentPresentState = viewModel::clearMarkStudentPresentState,
    )
}
