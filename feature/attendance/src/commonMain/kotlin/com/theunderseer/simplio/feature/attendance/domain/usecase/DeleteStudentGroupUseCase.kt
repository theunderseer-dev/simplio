package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupDeletionRepository

class DeleteStudentGroupUseCase(private val repository: StudentGroupDeletionRepository) {
    suspend operator fun invoke(groupId: Int) = repository.delete(groupId)
}
