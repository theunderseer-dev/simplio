package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import kotlinx.datetime.LocalDate

sealed interface AttendanceUiState {
    data object Loading : AttendanceUiState

    data class Content(
        val attendance: Attendance,
        val packageTypes: List<PackageType>,
        val currentDate: LocalDate,
        val latestClassDate: LocalDate,
        val rows: List<StudentAttendanceUiState>,
    ) : AttendanceUiState

    data class Error(val message: String) : AttendanceUiState
}

data class StudentAttendanceUiState(val registeredStudent: RegisteredStudent, val checkState: AttendanceCheckState)
