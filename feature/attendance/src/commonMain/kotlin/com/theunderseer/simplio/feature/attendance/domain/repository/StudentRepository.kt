package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.Student

interface StudentRepository {
    suspend fun create(fullName: String, groupId: Int): Student
    suspend fun getAll(): List<Student>

    suspend fun delete(studentId: Int)
}
