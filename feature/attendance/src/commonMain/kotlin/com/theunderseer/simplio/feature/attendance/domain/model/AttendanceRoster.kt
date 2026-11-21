package com.theunderseer.simplio.feature.attendance.domain.model

internal data class AttendanceRoster(val attendance: Attendance, val groupSchedules: Map<Int, List<ClassSchedule>>)
