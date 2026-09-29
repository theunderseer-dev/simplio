package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.Student

interface StudentRepository {
    fun newStudent(fullName: String): Student
}
