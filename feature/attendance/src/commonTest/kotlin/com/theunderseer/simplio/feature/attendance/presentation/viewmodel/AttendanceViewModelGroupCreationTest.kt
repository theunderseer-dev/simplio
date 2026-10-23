package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreateStudentGroupUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.CreateStudentGroupUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelGroupCreationTest {
    private val request = CreateStudentGroupRequest("New group", existingScheduleId = DefaultClassSchedule.ID)

    @Test
    fun `saving is guarded and success refreshes group list without hiding attendance`() = runViewModelTest {
        val groups = AttendanceTestData.studentGroups.toMutableList()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            studentGroups = groups,
            createStudentGroupUseCase = CreateStudentGroupUseCase(
                StudentGroupCreationRepository {
                    calls++
                    gate.await()
                    val group = StudentGroup(3, it.name)
                    groups += group
                    CreateStudentGroupResult.Success(group)
                },
            ),
        )
        observeUiState(viewModel)
        advanceUntilIdle()
        viewModel.createStudentGroup(request)
        viewModel.createStudentGroup(request)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(
            CreateStudentGroupUiState.Saving,
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).createStudentGroupState,
        )
        gate.complete(Unit)
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        assertEquals(3, state.studentGroups.size)
        assertEquals(CreateStudentGroupUiState.Success("New group"), state.createStudentGroupState)
    }

    @Test
    fun `save error stays inside group dialog state and can be retried`() = runViewModelTest {
        var calls = 0
        val viewModel = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            createStudentGroupUseCase = CreateStudentGroupUseCase(
                StudentGroupCreationRepository {
                    if (++calls == 1) error("Disk problem")
                    CreateStudentGroupResult.DuplicateName
                },
            ),
        )
        observeUiState(viewModel)
        advanceUntilIdle()
        viewModel.createStudentGroup(request)
        advanceUntilIdle()
        assertIs<CreateStudentGroupUiState.Error>(
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).createStudentGroupState,
        )
        viewModel.createStudentGroup(request)
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(
            CreateStudentGroupUiState.Error("A group with this name already exists"),
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).createStudentGroupState,
        )
    }

    @Test
    fun `venue date updates when venue crosses midnight before device does`() = runViewModelTest {
        val clock = MutableStateFlow<Instant?>(Instant.parse("2026-10-08T16:59:00Z"))
        val viewModel =
            createViewModel(FakeStudentRepository(), FakeStudentPackageRepository(), currentInstants = clock)
        observeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(
            LocalDate(2026, 10, 8),
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).groupCurrentDates[1],
        )
        clock.value = Instant.parse("2026-10-08T17:01:00Z")
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)
        assertEquals(AttendanceTestData.currentDate, state.currentDate)
        assertEquals(LocalDate(2026, 10, 9), state.groupCurrentDates[1])
    }
}
