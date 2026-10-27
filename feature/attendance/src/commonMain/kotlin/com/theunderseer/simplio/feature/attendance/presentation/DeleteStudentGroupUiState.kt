package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult

sealed interface DeleteStudentGroupUiState {
    data object Idle : DeleteStudentGroupUiState
    data class Saving(val groupId: Int) : DeleteStudentGroupUiState
    data class Deleted(val groupId: Int) : DeleteStudentGroupUiState
    data class Error(val groupId: Int, val reason: DeleteStudentGroupResult?) : DeleteStudentGroupUiState
}
