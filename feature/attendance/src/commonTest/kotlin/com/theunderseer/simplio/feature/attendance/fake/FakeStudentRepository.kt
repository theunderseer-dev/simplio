package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class FakeStudentRepository : StudentRepository {
    private var nextId = 1
    val students: List<Student>
        field = mutableListOf<Student>()

    override suspend fun create(fullName: String): Student {
        val student =
            Student(
                id = nextId++,
                fullName = fullName,
            )

        students += student

        return student
    }

    override suspend fun getAll(): List<Student> = students.toList()

    override suspend fun delete(studentId: Int) {
        students.removeAll { student ->
            student.id == studentId
        }
    }

    fun seed(student: Student): Student {
        students += student
        return student
    }
}
