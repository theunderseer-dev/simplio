package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassSessionStateEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceRow
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceSheet
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSessionStatus
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.domain.schedule.isEligibleForCancellationOn
import com.theunderseer.simplio.feature.attendance.domain.schedule.nextValidityDate
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionById
import kotlin.time.Clock

class RoomClassAttendanceRepository(private val database: AttendanceDatabase, private val clock: Clock) :
    ClassAttendanceRepository {
    override suspend fun load(sessionId: String): ClassAttendanceSheet = RoomTransactionRunner(database).run {
        readSheet(sessionId)
    }

    override suspend fun save(expected: ClassAttendanceSheet, visitsByStudent: Map<Int, Int>, cancel: Boolean) {
        RoomSyncJournal.write(database) { journal ->
            val current = readSheet(expected.session.id)
            if (current != expected) fail(ClassAttendanceProblem.STALE)
            if (cancel && (!current.canCancel || visitsByStudent.isNotEmpty())) {
                fail(ClassAttendanceProblem.HAS_ATTENDANCE)
            }
            val byId = current.rows.associateBy { it.student.id }
            visitsByStudent.forEach { (id, visits) ->
                val row = byId[id] ?: fail(ClassAttendanceProblem.INVALID_SELECTION)
                if (!row.canRecord || visits !in 1..row.maximumVisitCount) {
                    fail(ClassAttendanceProblem.INVALID_SELECTION)
                }
            }
            val day = current.session.date.toEpochDays()
            if (cancel) {
                val schedule = RoomClassScheduleRepository(database).getAll()
                    .single { it.id == current.session.scheduleId }
                current.rows.forEach { row ->
                    val pack = row.studentPackage ?: return@forEach
                    if (!pack.isEligibleForCancellationOn(current.session.date)) return@forEach
                    if (current.session.id in pack.validityExtensions) return@forEach
                    val expiration = checkNotNull(pack.expiresOn)
                    val days = (schedule.nextValidityDate(expiration).toEpochDays() - expiration.toEpochDays()).toInt()
                    val entity = checkNotNull(database.studentPackageDao().getById(pack.id))
                    database.studentPackageDao().update(
                        entity.copy(validityExtensions = entity.validityExtensions + (current.session.id to days)),
                    )
                    journal.upsertPackage(pack.id)
                }
            } else {
                current.rows.forEach { row ->
                    val visits = visitsByStudent[row.student.id] ?: 0
                    if (visits == row.recordedVisitCount) return@forEach
                    val pack = row.studentPackage ?: fail(ClassAttendanceProblem.INVALID_SELECTION)
                    if (visits == 0) {
                        journal.deleteAttendance(pack.id, day)
                        database.attendanceDao().deleteForPackageOnDate(pack.id, day)
                    } else {
                        database.attendanceDao().save(AttendanceEntity(pack.id, day, visits, current.session.id))
                        journal.upsertAttendance(pack.id, day)
                    }
                    database.studentPackageDao().updateStartedOnFromAttendance(pack.id)
                    database.realignPackageValidity(pack.id, pack.startedOn?.toEpochDays(), journal)
                }
            }
            database.classSessionStateDao().save(
                ClassSessionStateEntity(
                    current.session.id,
                    current.session.scheduleId,
                    day,
                    if (cancel) ClassSessionStatus.CANCELLED.name else ClassSessionStatus.HELD.name,
                ),
            )
            journal.ensureClassSession(current.session.id)
        }
    }

    private suspend fun readSheet(sessionId: String): ClassAttendanceSheet {
        if (database.classSessionStateDao().get(sessionId) != null) fail(ClassAttendanceProblem.UNAVAILABLE)
        val now = clock.now()
        val schedule = RoomClassScheduleRepository(database).getAll()
            .firstOrNull { it.archivedAt == null && it.id == sessionId.substringBefore(':') }
            ?: fail(ClassAttendanceProblem.UNAVAILABLE)
        val session = schedule.sessionById(sessionId)?.takeIf { it.startsAt <= now }
            ?: fail(ClassAttendanceProblem.UNAVAILABLE)
        val groups = database.classScheduleDao().groupSchedules().filter { it.scheduleId == schedule.id }
            .map { it.groupId }.toSet()
        if (groups.isEmpty()) fail(ClassAttendanceProblem.UNAVAILABLE)
        val packagesByStudent = database.syncPullDao().packages().groupBy { it.studentId }
        val attendanceByPackage = database.attendanceDao().getAll().groupBy { it.packageId }
        val rows = database.studentDao().getAll().filter { it.groupId in groups }.map { student ->
            val packages = packagesByStudent[student.id].orEmpty()
            // Keep an already recorded visit editable even if a newer package has since been purchased.
            val recorded = packages.firstOrNull { pack ->
                attendanceByPackage[pack.id].orEmpty().any {
                    it.attendedOnEpochDay == session.date.toEpochDays() &&
                        (it.sessionId == null || it.sessionId == sessionId)
                }
            }
            val entity = recorded ?: packages.filter { it.purchasedOnEpochDay <= session.date.toEpochDays() }
                .maxWithOrNull(compareBy({ it.purchasedOnEpochDay }, { it.id }))
            val pack = entity?.let { it.toDomain(attendanceByPackage[it.id].orEmpty()) }
            val count = if (recorded == null) 0 else pack?.attendanceHistory?.visitsByDate?.get(session.date) ?: 0
            val otherVisit = packages.any { other ->
                attendanceByPackage[other.id].orEmpty().any {
                    it.attendedOnEpochDay == session.date.toEpochDays() &&
                        (other.id != entity?.id || it.sessionId != null && it.sessionId != sessionId)
                }
            }
            val canRecord = count > 0 ||
                pack != null &&
                session.date >= (pack.startedOn ?: pack.purchasedOn) &&
                pack.isActiveOn(session.date) &&
                !otherVisit
            val latest = packages.maxWithOrNull(compareBy({ it.purchasedOnEpochDay }, { it.id }))
            val canRenew = count == 0 &&
                pack != null &&
                entity.id == latest?.id &&
                !pack.isActiveOn(session.date) &&
                session.date >= pack.earliestRenewalDate &&
                !otherVisit
            ClassAttendanceRow(student.toDomain(), pack, count, canRecord, canRenew)
        }.sortedWith(compareBy<ClassAttendanceRow> { it.student.fullName.lowercase() }.thenBy { it.student.id })
        val slot = schedule.slots.single { it.id == session.slotId }
        return ClassAttendanceSheet(session, "${slot.startsAt}–${slot.endsAt} · ${schedule.timeZoneId}", rows, groups)
    }

    private fun fail(problem: ClassAttendanceProblem): Nothing = throw ClassAttendanceFailure(problem)
}
