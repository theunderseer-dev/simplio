package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState

    data class Content(val attendance: Attendance, val packageTypes: List<PackageType>) : AttendanceUiState

    data class Error(val message: String) : AttendanceUiState
}
