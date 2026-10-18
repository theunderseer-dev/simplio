package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class FakeStudentRepository : StudentRepository {
    private var nextId = 1
    val students: List<Student>
        field = mutableListOf<Student>()

    override suspend fun create(fullName: String, groupId: Int, note: String): Student {
        val student = Student(
            id = nextId++,
            fullName = fullName,
            groupId = groupId,
            note = note,
        )
        students += student
        return student
    }

    override suspend fun getAll(): List<Student> = students.toList()

    override suspend fun updateNote(studentId: Int, note: String): Boolean {
        val index = students.indexOfFirst { it.id == studentId }
        if (index < 0) return false

        students[index] = students[index].copy(note = note)
        return true
    }

    override suspend fun delete(studentId: Int) {
        students.removeAll { it.id == studentId }
    }

    fun seed(student: Student): Student {
        students += student
        nextId = maxOf(nextId, student.id + 1)
        return student
    }
}
