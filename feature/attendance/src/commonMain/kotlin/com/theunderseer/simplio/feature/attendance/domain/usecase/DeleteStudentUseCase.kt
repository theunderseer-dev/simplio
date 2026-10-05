package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class DeleteStudentUseCase(private val studentRepository: StudentRepository) {
    suspend operator fun invoke(studentId: Int) {
        studentRepository.delete(
            studentId = studentId,
        )
    }
}
