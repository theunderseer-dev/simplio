package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class ClassHistoryEntry(
    val date: LocalDate,
    val students: List<ClassHistoryStudent>,
    val sessionId: String? = null,
    val timeLabel: String? = null,
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val status: ClassSessionStatus = ClassSessionStatus.UNRECORDED,
    val canTrackAttendance: Boolean = false,
    val scheduleName: String? = null,
) {
    val key: String get() = sessionId ?: date.toEpochDays().toString()
}

data class ClassHistoryStudent(val student: Student, val visitCount: Int)
