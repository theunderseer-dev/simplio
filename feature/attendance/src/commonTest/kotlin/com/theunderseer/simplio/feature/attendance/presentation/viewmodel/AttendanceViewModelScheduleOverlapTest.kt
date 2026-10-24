package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupCreationRepository
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreateStudentGroupUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeClassScheduleRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.CreateStudentGroupUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelScheduleOverlapTest {
    @Test
    fun `local invalidations expose imported overlaps and clear warning after correction`() = runViewModelTest {
        val first = testClassSchedule(id = "a", name = "First")
        val schedules = FakeClassScheduleRepository(listOf(first))
        val changes = MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
        val vm = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            localChanges = changes,
            classScheduleRepository = schedules,
        )
        observeUiState(vm)
        advanceUntilIdle()
        assertEquals(emptyList(), assertIs<AttendanceUiState.Content>(vm.uiState.value).scheduleOverlaps)
        schedules.save(first.copy(id = "b", name = "Second"))
        changes.emit(Unit)
        advanceUntilIdle()
        val overlap = assertIs<AttendanceUiState.Content>(vm.uiState.value).scheduleOverlaps.single()
        assertEquals(setOf("First", "Second"), setOf(overlap.first.name, overlap.second.name))
        schedules.save(
            first.copy(
                id = "b",
                name = "Second",
                slots = listOf(WeeklyClassSlot("friday", DayOfWeek.FRIDAY, LocalTime(12, 0), LocalTime(13, 0))),
            ),
        )
        changes.emit(Unit)
        advanceUntilIdle()
        assertEquals(emptyList(), assertIs<AttendanceUiState.Content>(vm.uiState.value).scheduleOverlaps)
    }

    @Test
    fun `save time overlap refreshes schedules and reports actionable error inside the dialog`() = runViewModelTest {
        val first = testClassSchedule(name = "Morning class")
        val vm = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            classScheduleRepository = FakeClassScheduleRepository(listOf(first)),
            createStudentGroupUseCase = CreateStudentGroupUseCase(
                StudentGroupCreationRepository {
                    CreateStudentGroupResult.ScheduleOverlap(first.name)
                },
            ),
        )
        observeUiState(vm)
        advanceUntilIdle()
        vm.createStudentGroup(CreateStudentGroupRequest("Private", newSchedule = first.copy(id = "new")))
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        val error = assertIs<CreateStudentGroupUiState.Error>(state.createStudentGroupState)
        assertTrue("Morning class" in error.message)
        assertEquals(listOf(first), state.classSchedules)
    }
}
