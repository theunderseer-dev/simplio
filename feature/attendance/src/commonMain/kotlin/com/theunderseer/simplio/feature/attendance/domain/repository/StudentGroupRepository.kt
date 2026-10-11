package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup

interface StudentGroupRepository {
    suspend fun getAll(): List<StudentGroup>
}
