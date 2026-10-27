package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.fake.FakeClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassAttendanceUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelClassAttendanceTest {
    @Test
    fun `opening is read only and successful save closes the dialog`() = runViewModelTest {
        val repository = FakeClassAttendanceRepository()
        val viewModel = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            classAttendanceRepository = repository,
        )
        observeUiState(viewModel)
        advanceUntilIdle()
        viewModel.openClassAttendance(ClassAttendanceTestData.session.id)
        advanceUntilIdle()
        assertIs<ClassAttendanceUiState.Content>(
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).classAttendanceState,
        )
        assertEquals(0, repository.saves)
        viewModel.saveClassAttendance(mapOf(1 to 1), false)
        advanceUntilIdle()
        assertEquals(mapOf(1 to 1), repository.savedVisits)
        assertEquals(
            ClassAttendanceUiState.Closed,
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).classAttendanceState,
        )
    }

    @Test
    fun `stale save keeps the sheet open for an explicit reload`() = runViewModelTest {
        val repository = FakeClassAttendanceRepository().apply { problem = ClassAttendanceProblem.STALE }
        val viewModel = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            classAttendanceRepository = repository,
        )
        observeUiState(viewModel)
        advanceUntilIdle()
        viewModel.openClassAttendance(ClassAttendanceTestData.session.id)
        advanceUntilIdle()
        viewModel.saveClassAttendance(mapOf(1 to 1), false)
        advanceUntilIdle()
        val state =
            assertIs<ClassAttendanceUiState.Content>(
                assertIs<AttendanceUiState.Content>(viewModel.uiState.value).classAttendanceState,
            )
        assertEquals(ClassAttendanceProblem.STALE, state.problem)
        assertEquals(repository.sheet, state.sheet)
        assertEquals(0, repository.saves)
    }
}
