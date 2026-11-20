package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule

interface ClassScheduleRepository {
    suspend fun getAll(): List<ClassSchedule>
    suspend fun getForGroup(groupId: Int): List<ClassSchedule>
    suspend fun save(schedule: ClassSchedule)
    suspend fun assignToGroup(groupId: Int, scheduleIds: Set<String>)
}

internal suspend fun ClassScheduleRepository?.attendanceSchedulesByGroup(
    groupIds: Iterable<Int>,
): Map<Int, List<ClassSchedule>> = groupIds.mapNotNull { groupId ->
    val schedules = this?.getForGroup(groupId)
    if (schedules != null && schedules.any { it.id != DefaultClassSchedule.ID }) groupId to schedules else null
}.toMap()
