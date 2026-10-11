package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentGroupDao
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupRepository

class RoomStudentGroupRepository(private val studentGroupDao: StudentGroupDao) : StudentGroupRepository {
    override suspend fun getAll(): List<StudentGroup> = studentGroupDao.getAll().map { entity ->
        StudentGroup(
            id = entity.id,
            name = entity.name,
        )
    }
}
