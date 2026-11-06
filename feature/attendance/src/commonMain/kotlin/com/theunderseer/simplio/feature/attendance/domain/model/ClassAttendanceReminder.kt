package com.theunderseer.simplio.feature.attendance.domain.model

/** One reminder per class occurrence, including when multiple groups share the schedule. */
data class ClassAttendanceReminder(val session: ClassSession, val scheduleName: String)
