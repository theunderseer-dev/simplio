package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.schedule.attendanceCheckState
import com.theunderseer.simplio.feature.attendance.domain.schedule.latestScheduledClassDate
import kotlinx.datetime.LocalDate

internal fun attendanceContentUiState(
    attendance: Attendance,
    packageTypes: List<PackageType>,
    currentDate: LocalDate,
): AttendanceUiState.Content {
    val latestClassDate =
        latestScheduledClassDate(
            today = currentDate,
        )

    val rows =
        attendance.entries.map { registeredStudent ->
            val checkState =
                attendanceCheckState(
                    attendanceDates = registeredStudent.studentPackage.attendanceDates,
                    latestScheduledClassDate = latestClassDate,
                )

            StudentAttendanceUiState(
                registeredStudent = registeredStudent,
                checkState = checkState,
                isCheckEnabled = true,
                requiresRenewal = !registeredStudent.studentPackage.isActiveOn(currentDate),
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
