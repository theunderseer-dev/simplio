package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryPage
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

interface ClassHistoryRepository {
    suspend fun getScheduledPage(
        currentDate: LocalDate,
        beforeDate: LocalDate?,
        limit: Int,
        currentInstant: Instant? = null,
    ): ClassHistoryPage? = null

    suspend fun getFirstAttendanceDate(): LocalDate?

    suspend fun getPage(beforeDate: LocalDate?, limit: Int): List<ClassHistoryEntry>
}
