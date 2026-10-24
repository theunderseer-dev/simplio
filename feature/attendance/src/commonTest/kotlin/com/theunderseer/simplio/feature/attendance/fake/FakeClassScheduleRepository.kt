package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.repository.ClassScheduleRepository

internal class FakeClassScheduleRepository(initial: List<ClassSchedule>) : ClassScheduleRepository {
    private val schedules = initial.toMutableList()
    private val links = mutableMapOf<Int, Set<String>>()

    override suspend fun getAll(): List<ClassSchedule> = schedules.toList()
    override suspend fun getForGroup(groupId: Int): List<ClassSchedule> = schedules.filter {
        it.id in
            links[groupId].orEmpty()
    }
    override suspend fun save(schedule: ClassSchedule) {
        schedules.removeAll { it.id == schedule.id }
        schedules += schedule
    }
    override suspend fun assignToGroup(groupId: Int, scheduleIds: Set<String>) {
        links[groupId] = scheduleIds
    }
}
