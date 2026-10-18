package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourthClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelClassHistoryTest {
    @Test
    fun `loads every scheduled class since first recorded attendance`() = runViewModelTest {
        // Given
        val recordedClass = testClassHistoryEntry(
            date = firstClassDate,
            students = listOf(
                ClassHistoryStudent(
                    student = student,
                    visitCount = 1,
                ),
            ),
        )

        val viewModel = createViewModel(
            studentRepository = FakeStudentRepository(),
            studentPackageRepository = FakeStudentPackageRepository(),
            classHistoryRepository = FakeClassHistoryRepository(
                entries = listOf(recordedClass),
            ),
            currentDates = flowOf(fourthClassDate),
        )
        observeUiState(viewModel)

        // When
        advanceUntilIdle()

        // Then
        val state = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(date = fourthClassDate),
                    testClassHistoryEntry(date = thirdClassDate),
                    testClassHistoryEntry(date = secondClassDate),
                    recordedClass,
                ),
                nextBeforeDate = null,
            ),
            state.classHistoryState,
        )
    }

    @Test
    fun `retries class history without hiding attendance content`() = runViewModelTest {
        // Given
        var requestCount = 0

        val repository = object : ClassHistoryRepository by FakeClassHistoryRepository() {
            override suspend fun getFirstAttendanceDate(): LocalDate? {
                requestCount++

                if (requestCount == 1) {
                    error("History unavailable")
                }

                return null
            }
        }

        val viewModel = createViewModel(
            studentRepository = FakeStudentRepository(),
            studentPackageRepository = FakeStudentPackageRepository(),
            classHistoryRepository = repository,
            currentDates = flowOf(secondClassDate),
        )
        observeUiState(viewModel)
        advanceUntilIdle()

        // Then: only history reports an error
        val before = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(
            ClassHistoryUiState.Error(message = "History unavailable"),
            before.classHistoryState,
        )

        // When
        viewModel.retryClassHistory()
        advanceUntilIdle()

        // Then
        val after = assertIs<AttendanceUiState.Content>(viewModel.uiState.value)

        assertEquals(before.attendance, after.attendance)
        assertEquals(2, requestCount)
        assertEquals(
            ClassHistoryUiState.Content(
                entries = emptyList(),
                nextBeforeDate = null,
            ),
            after.classHistoryState,
        )
    }
}
