package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.fake.FakeClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetClassHistoryUseCaseTest {
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
    fun `returns empty history before any attendance has been recorded`() = runTest {
        val repository = FakeClassHistoryRepository()

        val page = GetClassHistoryUseCase(repository)(currentDate = dates.first())

        assertEquals(emptyList(), page.entries)
        assertNull(page.nextBeforeDate)
        assertEquals(0, repository.lastLimit)
    }

    @Test
    fun `fills scheduled dates between first attendance and latest class in pages of five`() = runTest {
        val firstEntry = recordedEntry(date = dates.last(), visitCount = 2)
        val middleEntry = recordedEntry(date = AttendanceTestData.fifthClassDate)
        val repository = FakeClassHistoryRepository(listOf(firstEntry, middleEntry))
        val getHistory = GetClassHistoryUseCase(repository)

        val firstPage = getHistory(currentDate = dates.first())

        assertEquals(dates.take(5), firstPage.entries.map { it.date })
        assertEquals(dates[4], firstPage.nextBeforeDate)
        assertEquals(middleEntry, firstPage.entries.single { it.date == middleEntry.date })
        assertTrue(firstPage.entries.filter { it.date != middleEntry.date }.all { it.students.isEmpty() })
        assertEquals(6, repository.lastLimit)

        val secondPage = getHistory(currentDate = dates.first(), beforeDate = firstPage.nextBeforeDate)

        assertEquals(dates.drop(5), secondPage.entries.map { it.date })
        assertEquals(firstEntry, secondPage.entries.last())
        assertNull(secondPage.nextBeforeDate)
        assertEquals(dates[4], repository.lastBeforeDate)
    }

    @Test
    fun `does not include classes before the first recorded attendance`() = runTest {
        val firstEntry = recordedEntry(date = AttendanceTestData.secondClassDate)
        val getHistory = GetClassHistoryUseCase(FakeClassHistoryRepository(listOf(firstEntry)))

        val page = getHistory(currentDate = AttendanceTestData.thirdClassDate)

        assertEquals(
            listOf(AttendanceTestData.thirdClassDate, AttendanceTestData.secondClassDate),
            page.entries.map { it.date },
        )
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `does not add future Friday when today is Thursday`() = runTest {
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(listOf(recordedEntry(date = dates.last()))),
        )

        val page = getHistory(currentDate = AttendanceTestData.currentDate)

        assertEquals(
            listOf(AttendanceTestData.secondClassDate, AttendanceTestData.firstClassDate),
            page.entries.map { it.date },
        )
    }

    @Test
    fun `weekend history ends with Friday and excludes future recorded attendance`() = runTest {
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(
                listOf(
                    recordedEntry(date = AttendanceTestData.firstClassDate),
                    recordedEntry(date = AttendanceTestData.secondClassDate),
                ),
            ),
        )

        val page = getHistory(currentDate = AttendanceTestData.firstClassDate.plus(DatePeriod(days = 2)))

        assertEquals(listOf(AttendanceTestData.firstClassDate), page.entries.map { it.date })
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `exactly five scheduled classes do not require another page`() = runTest {
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(listOf(recordedEntry(date = dates[4]))),
        )

        val page = getHistory(currentDate = dates.first())

        assertEquals(dates.take(5), page.entries.map { it.date })
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `six scheduled classes offer a final page containing one class`() = runTest {
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(listOf(recordedEntry(date = dates[5]))),
        )

        val firstPage = getHistory(currentDate = dates.first())
        val lastPage = getHistory(currentDate = dates.first(), beforeDate = firstPage.nextBeforeDate)

        assertEquals(dates.take(5), firstPage.entries.map { it.date })
        assertEquals(dates[4], firstPage.nextBeforeDate)
        assertEquals(listOf(dates[5]), lastPage.entries.map { it.date })
        assertNull(lastPage.nextBeforeDate)
    }

    @Test
    fun `preserves legacy attendance outside Monday and Friday without dropping scheduled dates`() = runTest {
        val legacyDate = AttendanceTestData.firstClassDate.minus(DatePeriod(days = 1))
        val legacyEntry = recordedEntry(date = legacyDate, visitCount = 2)
        val getHistory = GetClassHistoryUseCase(FakeClassHistoryRepository(listOf(legacyEntry)))

        val page = getHistory(currentDate = AttendanceTestData.secondClassDate)

        assertEquals(
            listOf(AttendanceTestData.secondClassDate, AttendanceTestData.firstClassDate, legacyDate),
            page.entries.map { it.date },
        )
        assertEquals(legacyEntry, page.entries.last())
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `future attendance alone does not create history before its date`() = runTest {
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(listOf(recordedEntry(date = AttendanceTestData.thirdClassDate))),
        )

        val page = getHistory(currentDate = AttendanceTestData.currentDate)

        assertEquals(emptyList(), page.entries)
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `cursor at or before the first recorded date returns no entries`() = runTest {
        val firstDate = AttendanceTestData.firstClassDate
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(listOf(recordedEntry(date = firstDate))),
        )

        val page = getHistory(currentDate = dates.first(), beforeDate = firstDate)

        assertEquals(emptyList(), page.entries)
        assertNull(page.nextBeforeDate)
    }

    @Test
    fun `loads a year of empty scheduled classes without gaps or duplicate dates`() = runTest {
        val today = AttendanceTestData.firstClassDate
        val firstDate = today.minus(DatePeriod(days = 364))
        val getHistory = GetClassHistoryUseCase(
            FakeClassHistoryRepository(listOf(recordedEntry(date = firstDate, visitCount = 2))),
        )
        val loadedDates = mutableListOf<LocalDate>()
        var beforeDate: LocalDate? = null
        var pageCount = 0

        do {
            val page = getHistory(currentDate = today, beforeDate = beforeDate)
            assertEquals(5, page.entries.size)
            loadedDates += page.entries.map { it.date }
            beforeDate = page.nextBeforeDate
            pageCount++
            assertTrue(pageCount <= 21)
        } while (beforeDate != null)

        assertEquals(21, pageCount)
        assertEquals(105, loadedDates.size)
        assertEquals(105, loadedDates.distinct().size)
        assertEquals(today, loadedDates.first())
        assertEquals(firstDate, loadedDates.last())
        assertTrue(loadedDates.all { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY) })
        assertTrue(
            loadedDates.zipWithNext().all { (newer, older) ->
                newer.toEpochDays() - older.toEpochDays() in 3L..4L
            },
        )
    }

    private fun recordedEntry(date: LocalDate, visitCount: Int = 1): ClassHistoryEntry = testClassHistoryEntry(
        date = date,
        students = listOf(
            ClassHistoryStudent(
                student = testStudent(),
                visitCount = visitCount,
            ),
        ),
    )
}
