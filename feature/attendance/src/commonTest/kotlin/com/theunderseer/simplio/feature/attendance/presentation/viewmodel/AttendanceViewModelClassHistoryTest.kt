package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelClassHistoryTest {
    @Test
    fun `loads class history and adds empty latest scheduled class`() = runViewModelTest {
        // Given
        val recordedClass =
            testClassHistoryEntry(
                date = firstClassDate,
                students = listOf(
                    ClassHistoryStudent(
                        student = student,
                        visitCount = 1,
                    ),
                ),
            )

        val viewModel =
            createViewModel(
                studentRepository = FakeStudentRepository(),
                studentPackageRepository = FakeStudentPackageRepository(),
                classHistoryRepository = FakeClassHistoryRepository(
                    entries = listOf(recordedClass),
                ),
            )

        observeUiState(viewModel)

        // When
        advanceUntilIdle()

        // Then
        val state =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(
                        date = secondClassDate,
                    ),
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

        val repository =
            object : ClassHistoryRepository {
                override suspend fun getPage(beforeDate: LocalDate?, limit: Int): List<ClassHistoryEntry> {
                    requestCount += 1

                    if (requestCount == 1) {
                        error("History unavailable")
                    }

                    return emptyList()
                }
            }

        val viewModel =
            createViewModel(
                studentRepository = FakeStudentRepository(),
                studentPackageRepository = FakeStudentPackageRepository(),
                classHistoryRepository = repository,
            )

        observeUiState(viewModel)
        advanceUntilIdle()

        // Then: only history reports an error
        val before =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(
            ClassHistoryUiState.Error(
                message = "History unavailable",
            ),
            before.classHistoryState,
        )

        // When
        viewModel.retryClassHistory()
        advanceUntilIdle()

        // Then
        val after =
            assertIs<AttendanceUiState.Content>(
                viewModel.uiState.value,
            )

        assertEquals(before.attendance, after.attendance)
        assertEquals(2, requestCount)

        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(
                        date = secondClassDate,
                    ),
                ),
                nextBeforeDate = null,
            ),
            after.classHistoryState,
        )
    }
}
