package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.PackageTypeValidation

sealed interface CreatePackageTypeUiState {
    data object Idle : CreatePackageTypeUiState
    data object Saving : CreatePackageTypeUiState
    data class Success(val name: String) : CreatePackageTypeUiState
    data class Invalid(val reason: PackageTypeValidation) : CreatePackageTypeUiState
    data object DuplicateName : CreatePackageTypeUiState
    data object SaveFailed : CreatePackageTypeUiState
}
