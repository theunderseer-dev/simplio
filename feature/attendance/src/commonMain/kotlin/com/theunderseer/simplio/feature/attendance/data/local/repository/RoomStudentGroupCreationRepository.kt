package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.slotEntities
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toEntity
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomSyncJournal
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.model.displayGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.isValidGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupCreationRepository

class RoomStudentGroupCreationRepository(private val database: AttendanceDatabase) : StudentGroupCreationRepository {
    override suspend fun create(request: CreateStudentGroupRequest): CreateStudentGroupResult =
        RoomSyncJournal.write(database) { journal ->
            if (!isValidGroupName(request.name)) return@write CreateStudentGroupResult.InvalidName
            val name = displayGroupName(request.name)
            val groups = database.studentGroupDao().getAll()
            if (groups.any { normalizedGroupName(it.name) == normalizedGroupName(name) }) {
                return@write CreateStudentGroupResult.DuplicateName
            }
            if ((request.existingScheduleId == null) == (request.newSchedule == null)) {
                return@write CreateStudentGroupResult.InvalidSchedule
            }
            val dao = database.classScheduleDao()
            val scheduleId = request.existingScheduleId ?: checkNotNull(request.newSchedule).id
            val existing = dao.schedules().any { it.id == scheduleId }
            if (request.newSchedule != null) {
                if (existing || request.newSchedule.slots.groupBy { it.dayOfWeek }.any { it.value.size > 1 }) {
                    return@write CreateStudentGroupResult.InvalidSchedule
                }
                dao.save(request.newSchedule.toEntity())
                dao.insertSlots(request.newSchedule.slotEntities())
                journal.ensureSchedule(scheduleId)
            } else if (!existing ||
                dao.slots().filter { it.scheduleId == scheduleId }
                    .groupBy { it.dayOfWeek }.any { it.value.size > 1 }
            ) {
                return@write CreateStudentGroupResult.InvalidSchedule
            }
            val id = database.studentGroupDao().insert(StudentGroupEntity(name = name)).toInt()
            dao.attach(StudentGroupScheduleEntity(id, scheduleId))
            journal.ensureGroup(id)
            CreateStudentGroupResult.Success(StudentGroup(id, name))
        }
}
