package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import kotlinx.datetime.LocalDate

sealed interface ClassHistoryUiState {
    data object Loading : ClassHistoryUiState

    data class Content(val entries: List<ClassHistoryEntry>, val nextBeforeDate: LocalDate?) : ClassHistoryUiState

    data class Error(val message: String) : ClassHistoryUiState
}

internal fun ClassHistoryUiState.withLatestClassDate(latestClassDate: LocalDate): ClassHistoryUiState = when (this) {
    is ClassHistoryUiState.Content -> {
        if (entries.any { it.date == latestClassDate }) {
            this
        } else {
            copy(
                entries = (
                    entries +
                        ClassHistoryEntry(
                            date = latestClassDate,
                            students = emptyList(),
                        )
                    ).sortedByDescending { it.date },
            )
        }
    }

    ClassHistoryUiState.Loading,
    is ClassHistoryUiState.Error,
    -> this
}
