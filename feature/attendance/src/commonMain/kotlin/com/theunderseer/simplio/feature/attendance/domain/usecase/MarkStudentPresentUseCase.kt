package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import kotlinx.datetime.LocalDate

class MarkStudentPresentUseCase(
    private val repository: StudentPackageRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(studentId: Int, attendanceDate: LocalDate): MarkStudentPresentResult =
        transactionRunner.run {
            val studentPackage =
                repository.getLatestPackageForStudent(studentId)
                    ?: return@run MarkStudentPresentResult.PackageNotFound

            if (studentPackage.isCompleted) {
                return@run MarkStudentPresentResult.PackageCompleted(
                    studentPackage = studentPackage,
                )
            }

            if (studentPackage.isExpiredOn(attendanceDate)) {
                return@run MarkStudentPresentResult.PackageExpired(
                    studentPackage = studentPackage,
                )
            }

            if (attendanceDate in studentPackage.attendanceDates) {
                return@run MarkStudentPresentResult.Success(
                    studentPackage = studentPackage,
                )
            }

            val updatedPackage =
                studentPackage.copy(
                    attendanceDates = studentPackage.attendanceDates + attendanceDate,
                )

            repository.recordAttendance(updatedPackage.id, attendanceDate)

            MarkStudentPresentResult.Success(
                studentPackage = updatedPackage,
            )
        }
}
