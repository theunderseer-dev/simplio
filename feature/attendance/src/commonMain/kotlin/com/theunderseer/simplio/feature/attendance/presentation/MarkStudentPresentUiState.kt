package com.theunderseer.simplio.feature.attendance.presentation

sealed interface MarkStudentPresentUiState {
    data object Idle : MarkStudentPresentUiState

    data object Saving : MarkStudentPresentUiState

    data object Success : MarkStudentPresentUiState

    data class Error(val message: String) : MarkStudentPresentUiState
}
