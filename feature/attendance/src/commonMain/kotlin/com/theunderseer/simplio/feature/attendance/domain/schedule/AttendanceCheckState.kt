package com.theunderseer.simplio.feature.attendance.domain.schedule

import kotlinx.datetime.LocalDate

enum class AttendanceCheckState {
    Pending,
    Checked,
}

fun attendanceCheckState(attendanceDates: Set<LocalDate>, latestScheduledClassDate: LocalDate): AttendanceCheckState =
    if (latestScheduledClassDate in attendanceDates) {
        AttendanceCheckState.Checked
    } else {
        AttendanceCheckState.Pending
    }
