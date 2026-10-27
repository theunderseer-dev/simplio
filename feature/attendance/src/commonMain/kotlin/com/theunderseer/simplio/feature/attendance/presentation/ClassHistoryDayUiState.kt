package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal data class ClassHistoryDayUiState(val date: LocalDate, val blocks: List<ClassHistoryTimeBlockUiState>) {
    val visitCount: Int get() = blocks.sumOf { block -> block.students.sumOf { it.visitCount } }
}

internal data class ClassHistoryTimeBlockUiState(
    val key: String,
    val timeLabel: String?,
    val startsAt: Instant?,
    val endsAt: Instant?,
    val students: List<ClassHistoryStudent>,
    val sessions: List<ClassHistoryEntry>,
)

internal fun List<ClassHistoryEntry>.toClassHistoryDays(): List<ClassHistoryDayUiState> = groupBy { it.date }
    .toList()
    .sortedByDescending { it.first }
    .map { (date, entries) ->
        val blocks = entries.groupBy { entry ->
            if (entry.startsAt != null && entry.endsAt != null) {
                "time:${entry.startsAt}/${entry.endsAt}"
            } else {
                // An unknown interval must not be merged into a known class.
                "entry:${entry.key}"
            }
        }.map { (key, sameTimeEntries) ->
            val first = sameTimeEntries.minBy { it.key }
            ClassHistoryTimeBlockUiState(
                key = key,
                timeLabel = first.timeLabel,
                sessions = sameTimeEntries,
                startsAt = first.startsAt,
                endsAt = first.endsAt,
                students = sameTimeEntries.flatMap { it.students }.groupBy { it.student.id }.values.map { visits ->
                    visits.first().copy(visitCount = visits.sumOf { it.visitCount })
                }.sortedWith(
                    compareBy<ClassHistoryStudent> {
                        it.student.fullName.lowercase()
                    }.thenBy { it.student.id },
                ),
            )
        }.sortedWith(
            compareByDescending<ClassHistoryTimeBlockUiState> { it.startsAt }
                .thenByDescending { it.endsAt }.thenBy { it.key },
        )
        ClassHistoryDayUiState(date, blocks)
    }
