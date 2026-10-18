package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class UpdateStudentNoteUseCase(private val studentRepository: StudentRepository) {
    suspend operator fun invoke(studentId: Int, note: String): Boolean = studentRepository.updateNote(
        studentId = studentId,
        note = note.trim(),
    )
}
