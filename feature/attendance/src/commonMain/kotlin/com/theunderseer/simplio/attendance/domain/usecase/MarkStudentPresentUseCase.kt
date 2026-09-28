package com.theunderseer.simplio.attendance.domain.usecase

import com.theunderseer.simplio.attendance.domain.usecase.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

class MarkStudentPresentUseCase {
    operator fun invoke(
        studentPackage: StudentPackage,
        attendanceDate: LocalDate,
    ): MarkStudentPresentResult {
        if (studentPackage.isCompleted) {
            return MarkStudentPresentResult.PackageCompleted(studentPackage = studentPackage)
        }
        val updatedAttendanceDates = studentPackage.attendanceDates + attendanceDate
        return MarkStudentPresentResult.Success(
            studentPackage = studentPackage.copy(attendanceDates = updatedAttendanceDates),
        )
    }
}
