package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryPage
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSessionStatus
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestClassDate
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Preserve series identities while returning all classes for each paginated date. */
internal class ScheduledClassHistory(private val database: AttendanceDatabase) {
    suspend fun getPage(
        today: LocalDate,
        beforeDate: LocalDate?,
        limit: Int,
        currentInstant: Instant?,
    ): ClassHistoryPage? {
        val schedules = RoomClassScheduleRepository(database).getAll()
        val venueDates = schedules.associate {
            it.id to
                (currentInstant?.toLocalDateTime(TimeZone.of(it.timeZoneId))?.date ?: today)
        }
        val decisions = database.classSessionStateDao().getAll().associateBy { it.sessionId }
        val first = listOfNotNull(
            database.attendanceDao().getFirstAttendanceEpochDay(),
            database.syncPullDao().packages().minOfOrNull { it.purchasedOnEpochDay },
            decisions.values.minOfOrNull { it.dateEpochDay },
        ).minOrNull()?.let(LocalDate::fromEpochDays) ?: return ClassHistoryPage(emptyList(), null)
        val latestVenueDate = venueDates.values.maxOrNull() ?: today
        val upper = minOf(
            latestVenueDate,
            beforeDate?.minus(DatePeriod(days = 1)) ?: latestVenueDate,
        )
        if (upper < first) return ClassHistoryPage(emptyList(), null)
        val links = database.classScheduleDao().groupSchedules()
        val recorded = database.attendanceDao().getClassHistoryPage(
            upper.plus(DatePeriod(days = 1)).toEpochDays(),
            limit + 1,
        )
            .groupBy { it.attendedOnEpochDay }
        val collectedDays = mutableListOf<List<ClassHistoryEntry>>()
        val activeSchedules = schedules.filter { schedule ->
            schedule.archivedAt != null ||
                links.any { it.scheduleId == schedule.id }
        }
        fun previousDate(bound: LocalDate): LocalDate? = (
            activeSchedules.mapNotNull { listOf(it).latestClassDate(minOf(bound, venueDates.getValue(it.id))) } +
                recorded.keys.map(LocalDate::fromEpochDays).filter { it <= bound }
            ).maxOrNull()
        var date = previousDate(upper) ?: return ClassHistoryPage(emptyList(), null)
        while (date >= first && collectedDays.size <= limit) {
            val rows = recorded[date.toEpochDays()].orEmpty()
            val matched = mutableSetOf<Int>()
            val dayEntries = activeSchedules.filter { date <= venueDates.getValue(it.id) }.flatMap { schedule ->
                val groupIds = links.filter { it.scheduleId == schedule.id }.map { it.groupId }.toSet()
                schedule.sessionsBetween(date, date).filter {
                    currentInstant == null || it.startsAt <= currentInstant
                }.map { session ->
                    val students = rows.filterIndexed { index, row ->
                        val belongs = row.sessionId?.let { it == session.id } ?: (row.groupId in groupIds)
                        if (belongs) matched += index
                        belongs
                    }.map { ClassHistoryStudent(Student(it.studentId, it.fullName, it.groupId), it.visitCount) }
                    val slot = schedule.slots.single { it.id == session.slotId }
                    ClassHistoryEntry(
                        date,
                        students,
                        session.id,
                        "${slot.startsAt}–${slot.endsAt} · ${schedule.timeZoneId}",
                        session.startsAt,
                        session.endsAt,
                        decisions[session.id]?.let { ClassSessionStatus.valueOf(it.status) }
                            ?: ClassSessionStatus.UNRECORDED,
                        currentInstant != null &&
                            schedule.archivedAt == null &&
                            session.id !in decisions &&
                            students.isEmpty(),
                        schedule.name,
                    )
                }
            }.toMutableList()
            val unmatched = rows.filterIndexed { index, _ -> index !in matched }
            if (unmatched.isNotEmpty()) {
                dayEntries += ClassHistoryEntry(
                    date,
                    unmatched.map {
                        ClassHistoryStudent(Student(it.studentId, it.fullName, it.groupId), it.visitCount)
                    },
                )
            }
            if (dayEntries.isNotEmpty()) {
                collectedDays += dayEntries.sortedWith(
                    compareByDescending<ClassHistoryEntry> { it.startsAt }
                        .thenByDescending { it.endsAt }.thenBy { it.key },
                )
            }
            date = previousDate(date.minus(DatePeriod(days = 1))) ?: break
        }
        val page = collectedDays.take(limit).flatten()
        val more = collectedDays.size > limit
        return ClassHistoryPage(page, if (more) page.last().date else null)
    }
}
