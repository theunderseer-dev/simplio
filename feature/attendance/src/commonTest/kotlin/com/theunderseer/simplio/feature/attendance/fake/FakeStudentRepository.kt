package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.Student

class FakeStudentRepository : StudentRepository {
    private var nextId = 1
    val students: List<Student>
        field = mutableListOf<Student>()

    override fun newStudent(fullName: String): Student {
        val student =
            Student(
                id = nextId++,
                fullName = fullName,
            )

        students += student

        return student
    }
}
