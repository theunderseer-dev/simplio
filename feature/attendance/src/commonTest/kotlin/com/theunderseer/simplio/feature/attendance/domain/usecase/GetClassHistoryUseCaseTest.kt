package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetClassHistoryUseCaseTest {
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
        assertEquals(6, repository.lastLimit)
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
    fun `exactly five entries do not require another page`() = runTest {
        // Given
        val entries = historyEntries(count = 5)
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
    fun `loads older pages without repeating or skipping entries`() = runTest {
        // Given
        val entries = historyEntries(count = 11)
        val repository = FakeClassHistoryRepository(entries)
        val getHistory = GetClassHistoryUseCase(repository)

        // When
        val firstPage = getHistory()

        // Then
        assertEquals(entries.take(5), firstPage.entries)
        assertEquals(entries[4].date, firstPage.nextBeforeDate)

        // When
        val secondPage = getHistory(
            beforeDate = firstPage.nextBeforeDate,
        )

        // Then
        assertEquals(entries.subList(5, 10), secondPage.entries)
        assertEquals(entries[9].date, secondPage.nextBeforeDate)
        assertEquals(entries[4].date, repository.lastBeforeDate)

        // When
        val thirdPage = getHistory(
            beforeDate = secondPage.nextBeforeDate,
        )

        // Then
        assertEquals(entries.takeLast(1), thirdPage.entries)
        assertNull(thirdPage.nextBeforeDate)
        assertEquals(entries[9].date, repository.lastBeforeDate)
        assertEquals(
            entries,
            firstPage.entries + secondPage.entries + thirdPage.entries,
        )
    }

    private fun historyEntries(count: Int): List<ClassHistoryEntry> = List(count) { index ->
        testClassHistoryEntry(
            date = LocalDate.fromEpochDays(
                thirdClassDate.toEpochDays() - index,
            ),
        )
    }
}
