package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.attendanceContentUiState
import kotlinx.datetime.LocalDate

internal fun testAttendanceContentUiState(
    attendance: Attendance,
    packageTypes: List<PackageType>,
    currentDate: LocalDate,
    studentGroups: List<StudentGroup> = testStudentGroups(),
): AttendanceUiState.Content = attendanceContentUiState(
    attendance = attendance,
    packageTypes = packageTypes,
    currentDate = currentDate,
).copy(
    studentGroups = studentGroups,
)
