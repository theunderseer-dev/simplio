package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate

data class RegisteredStudent(
    val student: Student,
    val studentPackage: StudentPackage,
    val previousAttendanceDates: Set<LocalDate> = emptySet(),
) {
    val recordedAttendanceDates: Set<LocalDate> get() = previousAttendanceDates + studentPackage.attendanceDates
}
