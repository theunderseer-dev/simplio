package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet

sealed interface ClassAttendanceUiState {
    data object Closed : ClassAttendanceUiState
    data object Loading : ClassAttendanceUiState
    data class LoadError(val sessionId: String) : ClassAttendanceUiState
    data class Content(
        val sheet: ClassAttendanceSheet,
        val isSaving: Boolean = false,
        val problem: ClassAttendanceProblem? = null,
        val saveFailed: Boolean = false,
    ) : ClassAttendanceUiState
}
