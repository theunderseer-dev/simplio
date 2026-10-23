package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.attendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestClassDate
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.datetime.LocalDate

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

    val rows =
        attendance.entries.map { registeredStudent ->
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

    return AttendanceUiState.Content(
        attendance = attendance,
        packageTypes = packageTypes,
        currentDate = currentDate,
        latestClassDate = latestClassDate,
        rows = rows,
    )
}
