package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule

interface ClassScheduleRepository {
    suspend fun getAll(): List<ClassSchedule>
    suspend fun getForGroup(groupId: Int): List<ClassSchedule>
    suspend fun save(schedule: ClassSchedule)
    suspend fun assignToGroup(groupId: Int, scheduleIds: Set<String>)
}
