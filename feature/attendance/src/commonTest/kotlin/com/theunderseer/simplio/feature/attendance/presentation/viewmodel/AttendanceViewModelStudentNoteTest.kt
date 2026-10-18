package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.UpdateStudentNoteUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelStudentNoteTest {
    @Test
    fun `registration persists note even when no first attendance is recorded`() = runViewModelTest {
        val students = FakeStudentRepository()
        val packages = FakeStudentPackageRepository()
        val viewModel = createViewModel(studentRepository = students, studentPackageRepository = packages)
        observeUiState(viewModel)
        advanceUntilIdle()

        viewModel.registerStudent(
            fullName = AttendanceTestData.student.fullName,
            groupId = AttendanceTestData.student.groupId,
            packageType = AttendanceTestData.fourClassPackage,
            registeredOn = AttendanceTestData.registrationDate,
            recordFirstAttendance = false,
            note = "  ${AttendanceTestData.studentNote}  ",
        )
        advanceUntilIdle()

        val state = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        val entry = state.attendance.entries.single()
        assertEquals(AttendanceTestData.studentNote, entry.student.note)
        assertEquals(AttendanceTestData.studentNote, students.getAll().single().note)
        assertEquals(0, entry.studentPackage.usedClasses)
        assertEquals(null, entry.studentPackage.startedOn)
    }

    @Test
    fun `saving and clearing note refreshes row without changing package`() = runViewModelTest {
        val students = FakeStudentRepository()
        val student = students.seed(AttendanceTestData.student)
        val packages = FakeStudentPackageRepository()
        val studentPackage = packages.seed(
            studentId = student.id,
            packageType = AttendanceTestData.fourClassPackage,
            startedOn = AttendanceTestData.firstClassDate,
        )
        val viewModel = createViewModel(studentRepository = students, studentPackageRepository = packages)
        observeUiState(viewModel)
        advanceUntilIdle()

        viewModel.updateStudentNote(student.id, AttendanceTestData.studentNote)
        advanceUntilIdle()

        val updated = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        assertEquals(AttendanceTestData.studentNote, updated.rows.single().registeredStudent.student.note)
        assertEquals(studentPackage, updated.attendance.entries.single().studentPackage)
        assertEquals(UpdateStudentNoteUiState.Success(student.id), updated.updateStudentNoteState)

        viewModel.clearUpdateStudentNoteState()
        viewModel.updateStudentNote(student.id, "")
        advanceUntilIdle()

        val cleared = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        assertEquals("", cleared.rows.single().registeredStudent.student.note)
        assertEquals(studentPackage, cleared.attendance.entries.single().studentPackage)
    }

    @Test
    fun `missing student reports note error while attendance remains available`() = runViewModelTest {
        val viewModel = createViewModel(
            studentRepository = FakeStudentRepository(),
            studentPackageRepository = FakeStudentPackageRepository(),
        )
        observeUiState(viewModel)
        advanceUntilIdle()
        val before = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        viewModel.updateStudentNote(AttendanceTestData.student.id, AttendanceTestData.studentNote)
        advanceUntilIdle()

        val after = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        assertEquals(before.attendance, after.attendance)
        assertEquals(
            UpdateStudentNoteUiState.Error(AttendanceTestData.student.id, "Student not found"),
            after.updateStudentNoteState,
        )
    }
}
