package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `fills gaps in pages of five and does not request history after the first attendance`() = runViewModelTest {
        val viewModel = historyViewModel(recordedDates = listOf(dates.last()))
        observeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(dates.take(5), historyState(viewModel).entries.map { it.date })
        assertEquals(dates[4], historyState(viewModel).nextBeforeDate)

        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        assertEquals(dates, historyState(viewModel).entries.map { it.date })
        assertNull(historyState(viewModel).nextBeforeDate)

        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        assertEquals(dates, historyState(viewModel).entries.map { it.date })
    }

    @Test
    fun `exactly five scheduled classes do not offer another page`() = runViewModelTest {
        val viewModel = historyViewModel(recordedDates = listOf(dates[4]))
        observeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(dates.take(5), historyState(viewModel).entries.map { it.date })
        assertNull(historyState(viewModel).nextBeforeDate)
    }

    @Test
    fun `six scheduled classes keep load more available for the oldest date`() = runViewModelTest {
        val viewModel = historyViewModel(recordedDates = listOf(dates[5]))
        observeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(dates.take(5), historyState(viewModel).entries.map { it.date })
        assertEquals(dates[4], historyState(viewModel).nextBeforeDate)

        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        assertEquals(dates.take(6), historyState(viewModel).entries.map { it.date })
        assertNull(historyState(viewModel).nextBeforeDate)
    }

    @Test
    fun `does not add a placeholder before any attendance has been recorded`() = runViewModelTest {
        val viewModel = historyViewModel(recordedDates = emptyList())
        observeUiState(viewModel)
        advanceUntilIdle()

        assertTrue(historyState(viewModel).entries.isEmpty())
        assertNull(historyState(viewModel).nextBeforeDate)
    }

    @Test
    fun `new scheduled class appears when current date changes`() = runViewModelTest {
        val currentDates = MutableStateFlow(AttendanceTestData.currentDate)
        val viewModel = createViewModel(
            studentRepository = FakeStudentRepository(),
            studentPackageRepository = FakeStudentPackageRepository(),
            classHistoryRepository = FakeClassHistoryRepository(
                listOf(testClassHistoryEntry(date = AttendanceTestData.firstClassDate)),
            ),
            currentDates = currentDates,
        )
        observeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(
            listOf(AttendanceTestData.secondClassDate, AttendanceTestData.firstClassDate),
            historyState(viewModel).entries.map { it.date },
        )

        currentDates.value = AttendanceTestData.thirdClassDate
        advanceUntilIdle()

        assertEquals(
            listOf(
                AttendanceTestData.thirdClassDate,
                AttendanceTestData.secondClassDate,
                AttendanceTestData.firstClassDate,
            ),
            historyState(viewModel).entries.map { it.date },
        )
    }

    private fun historyViewModel(recordedDates: List<LocalDate>): AttendanceViewModel = createViewModel(
        studentRepository = FakeStudentRepository(),
        studentPackageRepository = FakeStudentPackageRepository(),
        classHistoryRepository = FakeClassHistoryRepository(
            recordedDates.map { testClassHistoryEntry(date = it) },
        ),
        currentDates = flowOf(dates.first()),
    )

    private fun historyState(viewModel: AttendanceViewModel): ClassHistoryUiState.Content =
        assertIs<ClassHistoryUiState.Content>(
            assertIs<AttendanceUiState.Content>(viewModel.uiState.value).classHistoryState,
        )
}
