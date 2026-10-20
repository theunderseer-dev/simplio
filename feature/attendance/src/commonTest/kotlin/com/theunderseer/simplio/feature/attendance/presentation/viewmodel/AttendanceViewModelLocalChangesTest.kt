package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelLocalChangesTest {
    @Test
    fun `database change refreshes existing screen without recreating view model`() = runViewModelTest {
        val students = FakeStudentRepository()
        val packages = FakeStudentPackageRepository()
        val changes = MutableSharedFlow<Unit>(replay = 1)
        changes.emit(Unit)
        val viewModel = createViewModel(students, packages, localChanges = changes)
        observeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(emptyList(), assertIs<AttendanceUiState.Content>(viewModel.uiState.value).rows)
        val student = students.seed(AttendanceTestData.student.copy(note = "Imported note"))
        packages.seed(student.id, AttendanceTestData.fourClassPackage, AttendanceTestData.firstClassDate)
        changes.emit(Unit)
        advanceUntilIdle()
        val content = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        assertEquals("Imported note", content.rows.single().registeredStudent.student.note)
        assertEquals(1, content.rows.single().registeredStudent.studentPackage.usedClasses)
    }

    @Test
    fun `remote deletion disappears after local database invalidation`() = runViewModelTest {
        val students = FakeStudentRepository()
        val packages = FakeStudentPackageRepository()
        val student = students.seed(AttendanceTestData.student)
        packages.seed(student.id, AttendanceTestData.fourClassPackage, AttendanceTestData.firstClassDate)
        val changes = MutableSharedFlow<Unit>(replay = 1)
        changes.emit(Unit)
        val viewModel = createViewModel(students, packages, localChanges = changes)
        observeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(1, assertIs<AttendanceUiState.Content>(viewModel.uiState.value).rows.size)
        students.delete(student.id)
        changes.emit(Unit)
        advanceUntilIdle()
        assertEquals(emptyList(), assertIs<AttendanceUiState.Content>(viewModel.uiState.value).rows)
    }
}
