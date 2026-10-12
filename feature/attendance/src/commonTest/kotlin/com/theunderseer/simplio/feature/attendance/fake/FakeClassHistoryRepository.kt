package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import kotlinx.datetime.LocalDate

class FakeClassHistoryRepository(private val entries: List<ClassHistoryEntry> = emptyList()) : ClassHistoryRepository {
    var lastBeforeDate: LocalDate? = null
        private set

    var lastLimit: Int = 0
        private set

    override suspend fun getPage(beforeDate: LocalDate?, limit: Int): List<ClassHistoryEntry> {
        lastBeforeDate = beforeDate
        lastLimit = limit

        return entries
            .filter { beforeDate == null || it.date < beforeDate }
            .sortedByDescending { it.date }
            .take(limit)
    }
}
