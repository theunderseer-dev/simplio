package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetClassHistoryUseCaseTest {
    private val latestDate = LocalDate(2026, 10, 9)

    @Test
    fun `returns empty page when history has no entries`() = runTest {
        // Given
        val repository = FakeClassHistoryRepository()
        val getHistory = GetClassHistoryUseCase(repository)

        // When
        val page = getHistory()

        // Then
        assertEquals(emptyList(), page.entries)
        assertNull(page.nextBeforeDate)
        assertEquals(21, repository.lastLimit)
    }

    @Test
    fun `returns all entries when history fits in one page`() = runTest {
        // Given
        val entries = historyEntries(count = 2)
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(entries),
        )

        // When
        val page = getHistory()

        // Then
        assertEquals(entries, page.entries)
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `exactly twenty entries do not require another page`() = runTest {
        // Given
        val entries = historyEntries(count = 20)
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(entries),
        )

        // When
        val page = getHistory()

        // Then
        assertEquals(entries, page.entries)
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `loads older page without repeating previous entries`() = runTest {
        // Given
        val entries = historyEntries(count = 21)
        val repository = FakeClassHistoryRepository(entries)
        val getHistory = GetClassHistoryUseCase(repository)

        // When
        val firstPage = getHistory()

        // Then
        assertEquals(entries.take(20), firstPage.entries)
        assertEquals(entries[19].date, firstPage.nextBeforeDate)

        // When
        val secondPage = getHistory(
            beforeDate = firstPage.nextBeforeDate,
        )

        // Then
        assertEquals(entries.takeLast(1), secondPage.entries)
        assertNull(secondPage.nextBeforeDate)
        assertEquals(entries[19].date, repository.lastBeforeDate)
    }

    private fun historyEntries(count: Int): List<ClassHistoryEntry> = List(count) { index ->
        testClassHistoryEntry(
            date = LocalDate.fromEpochDays(
                latestDate.toEpochDays() - index,
            ),
        )
    }
}
