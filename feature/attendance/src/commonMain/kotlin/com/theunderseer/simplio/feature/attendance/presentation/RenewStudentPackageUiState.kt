package com.theunderseer.simplio.feature.attendance.presentation

sealed interface RenewStudentPackageUiState {
    data object Idle : RenewStudentPackageUiState

    data class Saving(val studentId: Int) : RenewStudentPackageUiState

    data class Success(val studentId: Int) : RenewStudentPackageUiState

    data class Error(val studentId: Int, val message: String) : RenewStudentPackageUiState
}
