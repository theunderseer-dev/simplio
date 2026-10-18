package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryPage
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class GetClassHistoryUseCase(private val repository: ClassHistoryRepository) {
    suspend operator fun invoke(currentDate: LocalDate, beforeDate: LocalDate? = null): ClassHistoryPage {
        val firstAttendanceDate = repository.getFirstAttendanceDate()
            ?: return ClassHistoryPage(entries = emptyList(), nextBeforeDate = null)

        val tomorrow = currentDate.plus(DatePeriod(days = 1))
        val upperBound = minOf(tomorrow, beforeDate ?: tomorrow)

        if (firstAttendanceDate >= upperBound) {
            return ClassHistoryPage(entries = emptyList(), nextBeforeDate = null)
        }

        val recordedEntries = repository.getPage(
            beforeDate = upperBound,
            limit = PAGE_SIZE + 1,
        )

        val scheduledEntries = mutableListOf<ClassHistoryEntry>()
        var scheduledDate = latestScheduledClassDate(
            today = upperBound.minus(DatePeriod(days = 1)),
        )

        while (scheduledDate >= firstAttendanceDate && scheduledEntries.size <= PAGE_SIZE) {
            scheduledEntries += ClassHistoryEntry(
                date = scheduledDate,
                students = emptyList(),
            )
            scheduledDate = latestScheduledClassDate(
                today = scheduledDate.minus(DatePeriod(days = 1)),
            )
        }

        // Recorded dates take precedence, including legacy attendance outside the schedule.
        val entries = (scheduledEntries + recordedEntries)
            .associateBy { it.date }
            .values
            .sortedByDescending { it.date }
        val pageEntries = entries.take(PAGE_SIZE)

        return ClassHistoryPage(
            entries = pageEntries,
            nextBeforeDate =
            if (entries.size > PAGE_SIZE) {
                pageEntries.last().date
            } else {
                null
            },
        )
    }

    companion object {
        const val PAGE_SIZE = 5
    }
}
