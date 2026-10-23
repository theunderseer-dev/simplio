package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.isValidGroupName
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupCreationRepository

class CreateStudentGroupUseCase(private val repository: StudentGroupCreationRepository) {
    suspend operator fun invoke(request: CreateStudentGroupRequest): CreateStudentGroupResult {
        if (!isValidGroupName(request.name)) return CreateStudentGroupResult.InvalidName
        if ((request.existingScheduleId == null) == (request.newSchedule == null)) {
            return CreateStudentGroupResult.InvalidSchedule
        }
        return repository.create(request)
    }
}
