package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryPage
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassHistoryRepository
import kotlinx.datetime.LocalDate

class GetClassHistoryUseCase(private val repository: ClassHistoryRepository) {
    suspend operator fun invoke(beforeDate: LocalDate? = null): ClassHistoryPage {
        val entries = repository.getPage(
            beforeDate = beforeDate,
            limit = PAGE_SIZE + 1,
        )

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

    private companion object {
        const val PAGE_SIZE = 10
    }
}
