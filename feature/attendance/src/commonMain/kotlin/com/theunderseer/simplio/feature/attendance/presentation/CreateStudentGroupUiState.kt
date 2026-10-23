package com.theunderseer.simplio.feature.attendance.presentation

sealed interface CreateStudentGroupUiState {
    data object Idle : CreateStudentGroupUiState
    data object Saving : CreateStudentGroupUiState
    data class Success(val name: String) : CreateStudentGroupUiState
    data class Error(val message: String) : CreateStudentGroupUiState
}
