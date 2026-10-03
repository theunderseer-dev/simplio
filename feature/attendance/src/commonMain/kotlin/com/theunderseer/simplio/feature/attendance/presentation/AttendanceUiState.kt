package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState

    data class Content(val attendance: Attendance) : AttendanceUiState

    data class Error(val message: String) : AttendanceUiState
}
