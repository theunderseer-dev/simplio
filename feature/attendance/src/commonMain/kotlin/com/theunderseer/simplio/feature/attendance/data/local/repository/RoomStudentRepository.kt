package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.model.InvalidStudentName
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.isValidStudentName
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class RoomStudentRepository(private val database: AttendanceDatabase) : StudentRepository {
    private val studentDao = database.studentDao()

    override suspend fun create(fullName: String, groupId: Int, note: String): Student =
        RoomSyncJournal.write(database) { journal ->
            if (!isValidStudentName(fullName)) throw InvalidStudentName()
            val entity = StudentEntity(
                fullName = fullName.trim(),
                groupId = groupId,
                note = note,
            )
            val id = studentDao.insert(entity)

            journal.ensureStudent(id.toInt())
            entity.copy(id = id.toInt()).toDomain()
        }

    override suspend fun getAll(): List<Student> = studentDao
        .getAll()
        .map { it.toDomain() }

    override suspend fun updateNote(studentId: Int, note: String): Boolean =
        RoomSyncJournal.write(database) { journal ->
            val student = studentDao.getById(studentId) ?: return@write false
            if (student.note != note) {
                studentDao.updateNote(studentId = studentId, note = note)
                journal.upsertStudent(studentId)
            }
            true
        }

    override suspend fun delete(studentId: Int) {
        RoomSyncJournal.write(database) { journal ->
            if (studentDao.getById(studentId) != null) {
                journal.deleteStudent(studentId)
                studentDao.deleteById(studentId = studentId)
            }
        }
    }
}
