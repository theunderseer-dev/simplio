package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.studentNote
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateStudentNoteUseCaseTest {
    @Test
    fun `updates only selected student and trims note`() = runTest {
        val repository = FakeStudentRepository()
        val first = repository.seed(student)
        val other = repository.seed(testStudent(id = 2, fullName = "Bob Lee", note = "Keep this note"))

        val updated = UpdateStudentNoteUseCase(repository)(first.id, "  $studentNote  ")

        assertTrue(updated)
        assertEquals(listOf(first.copy(note = studentNote), other), repository.getAll())
    }

    @Test
    fun `blank note clears existing text`() = runTest {
        val repository = FakeStudentRepository()
        val saved = repository.seed(student.copy(note = studentNote))

        assertTrue(UpdateStudentNoteUseCase(repository)(saved.id, "   "))

        assertEquals(saved.copy(note = ""), repository.getAll().single())
    }

    @Test
    fun `missing student does not modify another student's note`() = runTest {
        val repository = FakeStudentRepository()
        val saved = repository.seed(student.copy(note = studentNote))

        assertFalse(UpdateStudentNoteUseCase(repository)(saved.id + 1, "Changed"))

        assertEquals(listOf(saved), repository.getAll())
    }
}
