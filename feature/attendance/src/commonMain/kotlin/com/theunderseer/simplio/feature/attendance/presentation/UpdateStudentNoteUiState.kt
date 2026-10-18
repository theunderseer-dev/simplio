package com.theunderseer.simplio.feature.attendance.presentation

sealed interface UpdateStudentNoteUiState {
    data object Idle : UpdateStudentNoteUiState

    data class Saving(val studentId: Int) : UpdateStudentNoteUiState

    data class Success(val studentId: Int) : UpdateStudentNoteUiState

    data class Error(val studentId: Int, val message: String) : UpdateStudentNoteUiState
}
