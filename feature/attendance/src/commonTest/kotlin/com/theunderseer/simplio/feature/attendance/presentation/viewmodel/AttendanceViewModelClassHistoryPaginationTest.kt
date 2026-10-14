package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceViewModel
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.collections.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelClassHistoryPaginationTest {
    @Test
    fun `appends older history and stops requesting pages at the end`() = runViewModelTest {
        // Given
        val entries = historyEntries(count = 11)
        val storedRepository = FakeClassHistoryRepository(
            entries = entries,
        )
        val requestedBeforeDates = mutableListOf<LocalDate?>()

        val repository =
            object : ClassHistoryRepository by storedRepository {
                override suspend fun getPage(
                    beforeDate: LocalDate?,
                    limit: Int,
                ): List<ClassHistoryEntry> {
                    requestedBeforeDates += beforeDate
                    return storedRepository.getPage(beforeDate, limit)
                }
            }

        val viewModel = createHistoryViewModel(repository)
        observeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(latestClassEntry()) + entries.take(10),
                nextBeforeDate = entries[9].date,
            ),
            historyState(viewModel),
        )

        // When
        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        // Then
        val expectedHistory = ClassHistoryUiState.Content(
            entries = listOf(latestClassEntry()) + entries,
            nextBeforeDate = null,
        )

        assertEquals(expectedHistory, historyState(viewModel))
        assertEquals(
            listOf(null, entries[9].date),
            requestedBeforeDates,
        )

        // When: the end has already been reached
        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        // Then
        assertEquals(expectedHistory, historyState(viewModel))
        assertEquals(
            listOf(null, entries[9].date),
            requestedBeforeDates,
        )
    }

    @Test
    fun `preserves history while loading and ignores repeated page requests`() = runViewModelTest {
        // Given
        val entries = historyEntries(count = 11)
        val storedRepository = FakeClassHistoryRepository(
            entries = entries,
        )
        val allowNextPage = CompletableDeferred<Unit>()
        var requestCount = 0

        val repository =
            object : ClassHistoryRepository by storedRepository {
                override suspend fun getPage(
                    beforeDate: LocalDate?,
                    limit: Int,
                ): List<ClassHistoryEntry> {
                    requestCount++

                    if (beforeDate != null) {
                        allowNextPage.await()
                    }

                    return storedRepository.getPage(beforeDate, limit)
                }
            }

        val viewModel = createHistoryViewModel(repository)
        observeUiState(viewModel)
        advanceUntilIdle()

        val before = historyState(viewModel)

        // When: both requests arrive before the coroutine runs
        viewModel.loadNextClassHistoryPage()
        viewModel.loadNextClassHistoryPage()
        runCurrent()

        // Then
        assertEquals(
            before.copy(isLoadingNextPage = true),
            historyState(viewModel),
        )
        assertEquals(2, requestCount)

        // When
        allowNextPage.complete(Unit)
        advanceUntilIdle()

        // Then
        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(latestClassEntry()) + entries,
                nextBeforeDate = null,
            ),
            historyState(viewModel),
        )
        assertEquals(2, requestCount)
    }

    @Test
    fun `retries failed next page without losing loaded history or attendance`() = runViewModelTest {
        // Given
        val entries = historyEntries(count = 11)
        val storedRepository = FakeClassHistoryRepository(
            entries = entries,
        )
        val requestedBeforeDates = mutableListOf<LocalDate?>()
        var nextPageFailed = false

        val repository =
            object : ClassHistoryRepository by storedRepository {
                override suspend fun getPage(
                    beforeDate: LocalDate?,
                    limit: Int,
                ): List<ClassHistoryEntry> {
                    requestedBeforeDates += beforeDate

                    if (beforeDate != null && !nextPageFailed) {
                        nextPageFailed = true
                        error("Older history unavailable")
                    }

                    return storedRepository.getPage(beforeDate, limit)
                }
            }

        val viewModel = createHistoryViewModel(repository)
        observeUiState(viewModel)
        advanceUntilIdle()

        val before = assertIs<AttendanceUiState.Content>(
            viewModel.uiState.value,
        )
        val beforeHistory = historyState(viewModel)

        // When
        viewModel.loadNextClassHistoryPage()
        advanceUntilIdle()

        // Then
        assertEquals(
            before.copy(
                classHistoryState = beforeHistory.copy(
                    nextPageErrorMessage = "Older history unavailable",
                ),
            ),
            viewModel.uiState.value,
        )

        // When
        viewModel.retryClassHistory()
        advanceUntilIdle()

        // Then
        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(latestClassEntry()) + entries,
                nextBeforeDate = null,
            ),
            historyState(viewModel),
        )

        val after = assertIs<AttendanceUiState.Content>(
            viewModel.uiState.value,
        )

        assertEquals(before.attendance, after.attendance)
        assertEquals(
            listOf(null, entries[9].date, entries[9].date),
            requestedBeforeDates,
        )
    }

    @Test
    fun `refresh after registration prevents cancelled older page from overwriting history`() = runViewModelTest {
        // Given
        val entries = historyEntries(count = 11)
        val storedRepository = FakeClassHistoryRepository(
            entries = entries,
        )
        val allowOlderPage = CompletableDeferred<Unit>()
        var firstPageRequests = 0

        val repository =
            object : ClassHistoryRepository by storedRepository {
                override suspend fun getPage(
                    beforeDate: LocalDate?,
                    limit: Int,
                ): List<ClassHistoryEntry> {
                    if (beforeDate == null) {
                        firstPageRequests++

                        return if (firstPageRequests == 1) {
                            storedRepository.getPage(beforeDate, limit)
                        } else {
                            emptyList()
                        }
                    }

                    // Simulate a source that finishes despite cancellation.
                    return withContext(NonCancellable) {
                        allowOlderPage.await()
                        storedRepository.getPage(beforeDate, limit)
                    }
                }
            }

        val viewModel = createHistoryViewModel(repository)
        observeUiState(viewModel)
        advanceUntilIdle()

        viewModel.loadNextClassHistoryPage()
        runCurrent()

        assertEquals(true, historyState(viewModel).isLoadingNextPage)

        // When: registration starts a fresh history request
        viewModel.registerStudent(
            fullName = AttendanceTestData.student.fullName,
            groupId = AttendanceTestData.student.groupId,
            packageType = AttendanceTestData.fourClassPackage,
            registeredOn = AttendanceTestData.registrationDate,
        )
        runCurrent()

        val refreshed = assertIs<AttendanceUiState.Content>(
            viewModel.uiState.value,
        )

        assertEquals(
            ClassHistoryUiState.Content(
                entries = listOf(latestClassEntry()),
                nextBeforeDate = null,
            ),
            refreshed.classHistoryState,
        )
        assertEquals(2, firstPageRequests)

        // When: the cancelled request still returns its result
        allowOlderPage.complete(Unit)
        advanceUntilIdle()

        // Then
        assertEquals(refreshed, viewModel.uiState.value)
    }

    private fun createHistoryViewModel(
        repository: ClassHistoryRepository,
    ): AttendanceViewModel = createViewModel(
        studentRepository = FakeStudentRepository(),
        studentPackageRepository = FakeStudentPackageRepository(),
        classHistoryRepository = repository,
        currentDates = flowOf(AttendanceTestData.firstClassDate),
    )

    private fun historyState(
        viewModel: AttendanceViewModel,
    ): ClassHistoryUiState.Content {
        val content = assertIs<AttendanceUiState.Content>(
            viewModel.uiState.value,
        )

        return assertIs<ClassHistoryUiState.Content>(
            content.classHistoryState,
        )
    }

    private fun latestClassEntry(): ClassHistoryEntry =
        testClassHistoryEntry(date = AttendanceTestData.firstClassDate)

    private fun historyEntries(count: Int): List<ClassHistoryEntry> =
        List(count) { index ->
            testClassHistoryEntry(
                date = LocalDate.fromEpochDays(
                    AttendanceTestData.firstClassDate.toEpochDays() - index - 1,
                ),
            )
        }
}
