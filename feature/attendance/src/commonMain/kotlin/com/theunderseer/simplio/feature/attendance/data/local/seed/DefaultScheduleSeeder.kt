package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.slotEntities
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toEntity
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule

/** Call only inside the caller's write transaction. Legacy cloud groups imply this unchanged template. */
internal suspend fun AttendanceDatabase.attachDefaultSchedule(groupId: Int) {
    val dao = classScheduleDao()
    if (dao.schedules().none { it.id == DefaultClassSchedule.ID }) {
        dao.save(DefaultClassSchedule.schedule.toEntity())
        dao.insertSlots(DefaultClassSchedule.schedule.slotEntities())
    }
    if (dao.groupSchedules().none { it.groupId == groupId }) {
        dao.attach(StudentGroupScheduleEntity(groupId, DefaultClassSchedule.ID))
    }
}
