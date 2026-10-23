package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult

fun interface StudentGroupCreationRepository {
    suspend fun create(request: CreateStudentGroupRequest): CreateStudentGroupResult
}
