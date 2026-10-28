package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.slotEntities
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toEntity
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassScheduleRepository

class RoomClassScheduleRepository(private val database: AttendanceDatabase) : ClassScheduleRepository {
    private val dao = database.classScheduleDao()
    private val transactions = RoomTransactionRunner(database)

    override suspend fun getAll(): List<ClassSchedule> = transactions.run {
        val slots = dao.slots()
        dao.schedules().map { it.toDomain(slots) }
    }

    override suspend fun getForGroup(groupId: Int): List<ClassSchedule> = transactions.run {
        val ids = dao.groupSchedules().filter { it.groupId == groupId }.map { it.scheduleId }.toSet()
        getAll().filter { it.id in ids }
    }

    override suspend fun save(schedule: ClassSchedule) = RoomSyncJournal.write(database) { journal ->
        require(schedule.archivedAt == null || dao.groupSchedules().none { it.scheduleId == schedule.id })
        val archived = dao.schedules().firstOrNull { it.id == schedule.id }?.archivedAtEpochMillis
        require(archived == null || schedule.archivedAt?.toEpochMilliseconds() == archived)
        dao.save(schedule.toEntity())
        dao.deleteSlots(schedule.id)
        dao.insertSlots(schedule.slotEntities())
        journal.upsertSchedule(schedule.id)
    }

    override suspend fun assignToGroup(groupId: Int, scheduleIds: Set<String>) {
        require(scheduleIds.isNotEmpty() && scheduleIds.size <= 100)
        RoomSyncJournal.write(database) { journal ->
            require(database.studentGroupDao().getAll().any { it.id == groupId })
            require(dao.schedules().filter { it.archivedAtEpochMillis == null }.map { it.id }.containsAll(scheduleIds))
            scheduleIds.forEach { journal.ensureSchedule(it) }
            dao.detachGroup(groupId)
            scheduleIds.sorted().forEach { dao.attach(StudentGroupScheduleEntity(groupId, it)) }
            journal.upsertGroup(groupId)
        }
    }
}
