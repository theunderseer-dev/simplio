package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import kotlinx.datetime.LocalDate

class MarkStudentPresentUseCase(
    private val repository: StudentPackageRepository,
) {
    suspend operator fun invoke(
        studentId: Int,
        attendanceDate: LocalDate,
    ): MarkStudentPresentResult {
        val studentPackage =
            repository.getLatestPackageForStudent(studentId)
                ?: return MarkStudentPresentResult.PackageNotFound

        if (studentPackage.isCompleted) {
            return MarkStudentPresentResult.PackageCompleted(
                studentPackage = studentPackage,
            )
        }

        if (studentPackage.isExpiredOn(attendanceDate)) {
            return MarkStudentPresentResult.PackageExpired(
                studentPackage = studentPackage,
            )
        }

        if (attendanceDate in studentPackage.attendanceDates) {
            return MarkStudentPresentResult.Success(
                studentPackage = studentPackage,
            )
        }

        val updatedPackage =
            studentPackage.copy(
                attendanceDates = studentPackage.attendanceDates + attendanceDate,
            )

        repository.update(updatedPackage)

        return MarkStudentPresentResult.Success(
            studentPackage = updatedPackage,
        )
    }
}
