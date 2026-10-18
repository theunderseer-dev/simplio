package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import kotlinx.datetime.LocalDate

interface ClassHistoryRepository {
    suspend fun getFirstAttendanceDate(): LocalDate?

    suspend fun getPage(beforeDate: LocalDate?, limit: Int): List<ClassHistoryEntry>
}
