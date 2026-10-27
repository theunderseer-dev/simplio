package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A witnessed collision between distinct series, not between groups sharing one series. */
data class ScheduleOverlap(
    val first: ClassSchedule,
    val second: ClassSchedule,
    val firstSession: ClassSession,
    val secondSession: ClassSession,
)

suspend fun findScheduleOverlap(candidate: ClassSchedule, existing: List<ClassSchedule>): ScheduleOverlap? {
    if (candidate.archivedAt != null) return null
    for (other in existing.filter { it.archivedAt == null }) {
        if (candidate.id == other.id) continue
        currentCoroutineContext().ensureActive()
        // Within one zone, disjoint wall-time intervals cannot collide, including across DST changes.
        if (candidate.timeZoneId == other.timeZoneId &&
            candidate.slots.none { a ->
                other.slots.any { b ->
                    a.dayOfWeek == b.dayOfWeek && a.startsAt < b.endsAt && b.startsAt < a.endsAt
                }
            }
        ) {
            continue
        }
        for (date in scheduleOverlapProbeDates(candidate, other)) {
            currentCoroutineContext().ensureActive()
            val first = candidate.sessionsBetween(date, date)
            if (first.isEmpty()) continue
            // A simultaneous class can fall on a different local date across the date line.
            val second = other.sessionsBetween(date.minus(DatePeriod(days = 2)), date.plus(DatePeriod(days = 2)))
            for (a in first) {
                val b = second.firstOrNull { a.startsAt < it.endsAt && it.startsAt < a.endsAt }
                if (b != null) return ScheduleOverlap(candidate, other, a, b)
            }
        }
    }
    return null
}

suspend fun findScheduleOverlaps(schedules: List<ClassSchedule>): List<ScheduleOverlap> {
    val result = mutableListOf<ScheduleOverlap>()
    val distinct = schedules.distinctBy { it.id }.sortedBy { it.id }
    distinct.forEachIndexed { index, schedule ->
        for (other in distinct.drop(index + 1)) {
            findScheduleOverlap(schedule, listOf(other))?.let { result += it }
        }
    }
    return result
}

/** Probe two weekly patterns at their boundaries and whenever either zone's UTC offset changes. */
internal expect fun scheduleOverlapProbeDates(first: ClassSchedule, second: ClassSchedule): Sequence<LocalDate>
