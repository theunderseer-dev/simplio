package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import kotlinx.datetime.LocalDate

internal suspend fun AttendanceDatabase.attendanceSessionId(studentId: Int, date: LocalDate): String? {
    val groupId = checkNotNull(studentDao().getById(studentId)).groupId
    val schedules = RoomClassScheduleRepository(this).getForGroup(groupId)
    if (schedules.isEmpty() || schedules.all { it.id == DefaultClassSchedule.ID }) return null
    val sessions = schedules.flatMap { it.sessionsBetween(date, date) }
    require(sessions.size == 1) { "Choose a scheduled class date for this group" }
    return sessions.single().id
}
