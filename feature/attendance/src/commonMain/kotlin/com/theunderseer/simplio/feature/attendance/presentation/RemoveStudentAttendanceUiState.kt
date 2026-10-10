package com.theunderseer.simplio.feature.attendance.presentation

sealed interface RemoveStudentAttendanceUiState {
    data object Idle : RemoveStudentAttendanceUiState

    data object Saving : RemoveStudentAttendanceUiState

    data object Success : RemoveStudentAttendanceUiState

    data class Error(val message: String) : RemoveStudentAttendanceUiState
}
