package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelClassHistoryPageSizeTest {
    private val dates = listOf(
        AttendanceTestData.eighthClassDate,
        AttendanceTestData.seventhClassDate,
        AttendanceTestData.sixthClassDate,
        AttendanceTestData.fifthClassDate,
        AttendanceTestData.fourthClassDate,
        AttendanceTestData.thirdClassDate,
        AttendanceTestData.secondClassDate,
        AttendanceTestData.firstClassDate,
    )

    @Test
    fun `first page contains five classes including an empty latest class without skipping older dates`() =
        runViewModelTest {
            // Given
            val viewModel = historyViewModel(
                recordedDates = dates.drop(1),
                currentDate = dates.first(),
            )
            observeUiState(viewModel)
            advanceUntilIdle()

            // Then
            val firstPage = historyState(viewModel)
            assertEquals(dates.take(5), firstPage.entries.map { it.date })
            assertEquals(dates[4], firstPage.nextBeforeDate)

            // When
            viewModel.loadNextClassHistoryPage()
            advanceUntilIdle()

            // Then
            val allHistory = historyState(viewModel)
            assertEquals(dates, allHistory.entries.map { it.date })
            assertNull(allHistory.nextBeforeDate)
        }

    @Test
    fun `five recorded classes including the latest class do not offer another page`() = runViewModelTest {
        // Given
        val recordedDates = dates.take(5)
        val viewModel = historyViewModel(
            recordedDates = recordedDates,
            currentDate = recordedDates.first(),
        )
        observeUiState(viewModel)
        advanceUntilIdle()

        // Then
        val history = historyState(viewModel)
        assertEquals(recordedDates, history.entries.map { it.date })
        assertNull(history.nextBeforeDate)
    }

    @Test
    fun `empty latest class keeps load more available when five older classes exist`() = runViewModelTest {
        // Given
        val recordedDates = dates.drop(1).take(5)
        val expectedDates = listOf(dates.first()) + recordedDates
        val viewModel = historyViewModel(
            recordedDates = recordedDates,
            currentDate = dates.first(),
        )
        observeUiState(viewModel)
        advanceUntilIdle()

        // Then
        assertEquals(expectedDates.take(5), historyState(viewModel).entries.map { it.date })
        assertEquals(expectedDates[4], historyState(viewModel).nextBeforeDate)

        // When
        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        // Then
        assertEquals(expectedDates, historyState(viewModel).entries.map { it.date })
        assertNull(historyState(viewModel).nextBeforeDate)
    }

    @Test
    fun `load more expands history to ten classes and then the remaining classes`() = runViewModelTest {
        // Given
        val latestDate = AttendanceTestData.firstClassDate
        val recordedDates = (1..12).map { weeksAgo ->
            latestDate.minus(DatePeriod(days = weeksAgo * 7))
        }
        val expectedDates = listOf(latestDate) + recordedDates
        val viewModel = historyViewModel(
            recordedDates = recordedDates,
            currentDate = latestDate,
        )
        observeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(expectedDates.take(5), historyState(viewModel).entries.map { it.date })

        // When
        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        // Then
        assertEquals(expectedDates.take(10), historyState(viewModel).entries.map { it.date })
        assertEquals(expectedDates[9], historyState(viewModel).nextBeforeDate)

        // When
        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        // Then
        assertEquals(expectedDates, historyState(viewModel).entries.map { it.date })
        assertNull(historyState(viewModel).nextBeforeDate)
    }

    private fun historyViewModel(recordedDates: List<LocalDate>, currentDate: LocalDate): AttendanceViewModel {
        val entries = recordedDates
            .sortedDescending()
            .map { testClassHistoryEntry(date = it) }
        val repository = object : ClassHistoryRepository {
            override suspend fun getPage(beforeDate: LocalDate?, limit: Int): List<ClassHistoryEntry> = entries
                .filter { beforeDate == null || it.date < beforeDate }
                .take(limit)
        }

        return createViewModel(
            studentRepository = FakeStudentRepository(),
            studentPackageRepository = FakeStudentPackageRepository(),
            classHistoryRepository = repository,
            currentDates = flowOf(currentDate),
        )
    }

    private fun historyState(viewModel: AttendanceViewModel): ClassHistoryUiState.Content =
        assertIs<ClassHistoryUiState.Content>(
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).classHistoryState,
        )
}
