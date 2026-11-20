package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.schedule.attendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestClassDate
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun attendanceContentUiState(
    attendance: Attendance,
    packageTypes: List<PackageType>,
    currentDate: LocalDate,
    groupSchedules: Map<Int, List<ClassSchedule>> = emptyMap(),
    groupCurrentDates: Map<Int, LocalDate> = emptyMap(),
): AttendanceUiState.Content {
    val latestClassDate =
        latestScheduledClassDate(
            today = currentDate,
        )

    return AttendanceUiState.Content(
        attendance = attendance,
        packageTypes = packageTypes,
        currentDate = currentDate,
        latestClassDate = latestClassDate,
        rows = attendanceRowsUiState(attendance, currentDate, groupSchedules, groupCurrentDates),
    )
}

internal fun attendanceDatesForGroups(
    groupIds: Iterable<Int>,
    groupSchedules: Map<Int, List<ClassSchedule>>,
    now: Instant,
): Map<Int, LocalDate> = groupIds.associateWith { groupId ->
    val zone = groupSchedules[groupId]?.firstOrNull()?.timeZoneId ?: DefaultClassSchedule.schedule.timeZoneId
    now.toLocalDateTime(TimeZone.of(zone)).date
}

internal fun AttendanceRoster.toRows(now: Instant): List<StudentAttendanceUiState> {
    val groupIds = attendance.entries.map { it.student.groupId }.toSet()
    val defaultDate = now.toLocalDateTime(TimeZone.of(DefaultClassSchedule.schedule.timeZoneId)).date
    return attendanceRowsUiState(
        attendance,
        defaultDate,
        groupSchedules,
        attendanceDatesForGroups(groupIds, groupSchedules, now),
    )
}

internal fun attendanceRowsUiState(
    attendance: Attendance,
    currentDate: LocalDate,
    groupSchedules: Map<Int, List<ClassSchedule>>,
    groupCurrentDates: Map<Int, LocalDate>,
): List<StudentAttendanceUiState> = attendance.entries.map { registeredStudent ->
    val schedules = groupSchedules[registeredStudent.student.groupId]
    val venueToday = groupCurrentDates[registeredStudent.student.groupId] ?: currentDate
    val effectiveDate =
        schedules?.latestClassDate(venueToday)
            ?: latestScheduledClassDate(venueToday).takeIf { schedules == null }
    val checkState = if (effectiveDate == null) {
        AttendanceCheckState.Pending
    } else {
        attendanceCheckState(
            attendanceDates = registeredStudent.studentPackage.attendanceDates,
            latestScheduledClassDate = effectiveDate,
        )
    }

    StudentAttendanceUiState(
        registeredStudent = registeredStudent,
        checkState = checkState,
        isCheckEnabled = true,
        requiresRenewal = !registeredStudent.studentPackage.isActiveOn(venueToday),
        effectiveClassDate = effectiveDate.takeIf { schedules != null || venueToday != currentDate },
    )
}
