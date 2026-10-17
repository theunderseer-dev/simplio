package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.usecase.GetClassHistoryUseCase
import kotlinx.datetime.LocalDate

sealed interface ClassHistoryUiState {
    data object Loading : ClassHistoryUiState

    data class Content(
        val entries: List<ClassHistoryEntry>,
        val nextBeforeDate: LocalDate?,
        val isLoadingNextPage: Boolean = false,
        val nextPageErrorMessage: String? = null,
    ) : ClassHistoryUiState

    data class Error(val message: String) : ClassHistoryUiState
}

internal fun ClassHistoryUiState.withLatestClassDate(latestClassDate: LocalDate): ClassHistoryUiState = when (this) {
    is ClassHistoryUiState.Content -> this.withLatestClassDate(latestClassDate)

    ClassHistoryUiState.Loading,
    is ClassHistoryUiState.Error,
    -> this
}

internal fun ClassHistoryUiState.Content.withLatestClassDate(latestClassDate: LocalDate): ClassHistoryUiState.Content {
    val combinedEntries =
        if (entries.any { it.date == latestClassDate }) {
            entries
        } else {
            (
                entries +
                    ClassHistoryEntry(
                        date = latestClassDate,
                        students = emptyList(),
                    )
                ).sortedByDescending { it.date }
        }

    val pageSize = GetClassHistoryUseCase.PAGE_SIZE
    // Keep the current page budget: 5, 10, 15, ... cards, including the empty latest class.
    val visibleEntryLimit = maxOf(
        pageSize,
        ((entries.size + pageSize - 1) / pageSize) * pageSize,
    )
    val visibleEntries = combinedEntries.take(visibleEntryLimit)
    val nextDate =
        if (combinedEntries.size > visibleEntries.size) {
            visibleEntries.last().date
        } else {
            nextBeforeDate
        }

    return if (visibleEntries == entries && nextDate == nextBeforeDate) {
        this
    } else {
        copy(
            entries = visibleEntries,
            nextBeforeDate = nextDate,
        )
    }
}
