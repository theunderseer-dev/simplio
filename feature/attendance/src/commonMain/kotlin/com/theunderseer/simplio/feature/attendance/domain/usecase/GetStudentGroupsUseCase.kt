package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupRepository

class GetStudentGroupsUseCase(private val repository: StudentGroupRepository) {
    suspend operator fun invoke(): List<StudentGroup> = repository.getAll()
}
