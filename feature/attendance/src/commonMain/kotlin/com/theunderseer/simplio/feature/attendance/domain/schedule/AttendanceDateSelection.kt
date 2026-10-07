package com.theunderseer.simplio.feature.attendance.domain.schedule

import kotlinx.datetime.LocalDate

internal fun isAttendanceDateSelectable(date: LocalDate, today: LocalDate): Boolean = date <= today &&
    latestScheduledClassDate(today = date) == date
