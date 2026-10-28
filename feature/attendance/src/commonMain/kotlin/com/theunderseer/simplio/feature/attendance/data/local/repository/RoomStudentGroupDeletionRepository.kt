package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupDeletionRepository
import kotlin.time.Clock

class RoomStudentGroupDeletionRepository(private val database: AttendanceDatabase, private val clock: Clock) :
    StudentGroupDeletionRepository {
    override suspend fun delete(groupId: Int): DeleteStudentGroupResult = RoomSyncJournal.write(database) { journal ->
        if (database.studentGroupDao().getAll().none {
                it.id == groupId
            }
        ) {
            return@write DeleteStudentGroupResult.NOT_FOUND
        }
        if (database.studentDao().getAll().any {
                it.groupId == groupId
            }
        ) {
            return@write DeleteStudentGroupResult.NOT_EMPTY
        }
        journal.ensureGroup(groupId)
        val dao = database.classScheduleDao()
        val links = dao.groupSchedules()
        links.filter { it.groupId == groupId }.forEach { journal.ensureSchedule(it.scheduleId) }
        val retiredIds = links.filter { it.groupId == groupId }.map { it.scheduleId }.filter { id ->
            links.none { it.scheduleId == id && it.groupId != groupId }
        }.toSet()
        journal.deleteGroup(groupId)
        dao.detachGroup(groupId)
        database.studentGroupDao().delete(groupId)
        val now = clock.now().toEpochMilliseconds()
        dao.schedules().filter { it.id in retiredIds && it.archivedAtEpochMillis == null }.forEach { row ->
            dao.save(row.copy(archivedAtEpochMillis = now))
            journal.upsertSchedule(row.id)
        }
        DeleteStudentGroupResult.DELETED
    }
}
