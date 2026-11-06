package com.theunderseer.simplio.feature.attendance.data.local.repository

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceReminder
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Reads a local snapshot for both upcoming notifications and catch-up after a missed delivery. */
class RoomClassAttendanceReminderSource(private val database: AttendanceDatabase) {
    suspend fun getCandidates(endsAfter: Instant, endsThrough: Instant): List<ClassAttendanceReminder> {
        require(endsThrough >= endsAfter && endsThrough - endsAfter <= 31.days) {
            "Request a reminder window of at most 31 days"
        }
        return database.useReaderConnection { reader ->
            reader.deferredTransaction { readCandidates(endsAfter, endsThrough) }
        }
    }

    private suspend fun readCandidates(endsAfter: Instant, endsThrough: Instant): List<ClassAttendanceReminder> {
        val scheduleDao = database.classScheduleDao()
        val groupIdsBySchedule = scheduleDao.groupSchedules().groupBy({ it.scheduleId }, { it.groupId })
            .mapValues { (_, groupIds) -> groupIds.toSet() }
        val firstPurchaseByGroup = scheduleDao.firstPurchasesByGroup().associate {
            it.groupId to it.firstPurchaseEpochDay
        }
        val slots = scheduleDao.slots()
        val projected = scheduleDao.schedules().asSequence()
            .filter { it.archivedAtEpochMillis == null }
            .flatMap { entity ->
                val firstPurchaseDay = groupIdsBySchedule[entity.id].orEmpty()
                    .mapNotNull(firstPurchaseByGroup::get).minOrNull()
                    ?: return@flatMap emptySequence()
                val schedule = entity.toDomain(slots)
                val zone = TimeZone.of(schedule.timeZoneId)
                schedule.sessionsBetween(
                    endsAfter.toLocalDateTime(zone).date,
                    endsThrough.toLocalDateTime(zone).date,
                ).asSequence()
                    .filter { session ->
                        session.endsAt > endsAfter &&
                            session.endsAt <= endsThrough &&
                            session.date.toEpochDays() >= firstPurchaseDay
                    }
                    .map { ClassAttendanceReminder(it, schedule.name) }
            }.toList()
        if (projected.isEmpty()) return emptyList()

        val firstDay = projected.minOf { it.session.date.toEpochDays() }
        val lastDay = projected.maxOf { it.session.date.toEpochDays() }
        val decisions = database.classSessionStateDao().getSessionIdsBetween(firstDay, lastDay).toSet()
        val attendanceByDay = database.attendanceDao().getClassAttendancePresence(firstDay, lastDay)
            .groupBy { it.attendedOnEpochDay }
        return projected.filter { (session) ->
            val groupIds = groupIdsBySchedule.getValue(session.scheduleId)
            session.id !in decisions &&
                attendanceByDay[session.date.toEpochDays()].orEmpty().none {
                    matchesClassAttendance(session.id, groupIds, it.sessionId, it.groupId)
                }
        }.sortedWith(compareBy<ClassAttendanceReminder> { it.session.endsAt }.thenBy { it.session.id })
    }
}
