package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import kotlinx.datetime.LocalDate

internal fun isAttendanceDateSelectable(
    date: LocalDate,
    today: LocalDate,
    schedules: List<ClassSchedule>? = null,
): Boolean =
    date <= today && (schedules?.isScheduledClassDate(date) ?: (latestScheduledClassDate(today = date) == date))
