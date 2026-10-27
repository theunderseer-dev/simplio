package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceFailure
import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import kotlinx.datetime.LocalDate

internal suspend fun AttendanceDatabase.attendanceSessionId(studentId: Int, date: LocalDate): String? {
    val groupId = checkNotNull(studentDao().getById(studentId)).groupId
    val schedules = RoomClassScheduleRepository(this).getForGroup(groupId)
    if (schedules.isEmpty()) return null
    val sessions = schedules.flatMap { it.sessionsBetween(date, date) }
    if (sessions.isEmpty() && schedules.all { it.id == DefaultClassSchedule.ID }) return null
    require(sessions.size == 1) { "Choose a scheduled class date for this group" }
    val sessionId = sessions.single().id
    if (classSessionStateDao().get(sessionId)?.status == "CANCELLED") {
        throw ClassAttendanceFailure(
            ClassAttendanceProblem.CANCELLED,
        )
    }
    return sessionId.takeUnless { schedules.all { it.id == DefaultClassSchedule.ID } }
}
