package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class RoomStudentRepository(private val studentDao: StudentDao) : StudentRepository {
    override suspend fun create(fullName: String, groupId: Int, note: String): Student {
        val entity = StudentEntity(
            fullName = fullName,
            groupId = groupId,
            note = note,
        )
        val id = studentDao.insert(entity)

        return entity.copy(id = id.toInt()).toDomain()
    }

    override suspend fun getAll(): List<Student> = studentDao
        .getAll()
        .map { it.toDomain() }

    override suspend fun updateNote(studentId: Int, note: String): Boolean =
        studentDao.updateNote(studentId = studentId, note = note) > 0

    override suspend fun delete(studentId: Int) {
        studentDao.deleteById(studentId = studentId)
    }
}
