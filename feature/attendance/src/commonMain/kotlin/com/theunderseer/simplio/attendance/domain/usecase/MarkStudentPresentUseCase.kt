package com.theunderseer.simplio.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

class MarkStudentPresentUseCase {
    operator fun invoke(
        studentPackage: StudentPackage,
        attendanceDate: LocalDate,
    ): StudentPackage {
        val updatedAttendanceDates = studentPackage.attendanceDates + attendanceDate
        return studentPackage.copy(attendanceDates = updatedAttendanceDates)
    }
}
