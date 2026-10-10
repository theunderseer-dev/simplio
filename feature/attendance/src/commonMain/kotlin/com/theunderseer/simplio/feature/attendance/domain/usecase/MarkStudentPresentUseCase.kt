package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.MarkStudentPresentResult
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import kotlinx.datetime.LocalDate

class MarkStudentPresentUseCase(
    private val repository: StudentPackageRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        studentId: Int,
        attendanceDate: LocalDate,
        visitCount: Int = 1,
    ): MarkStudentPresentResult = transactionRunner.run {
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

        if (!studentPackage.packageType.allowsVisitCount(visitCount)) {
            return@run MarkStudentPresentResult.InvalidVisitCount
        }

        if (!studentPackage.allowsVisitCount(visitCount)) {
            return@run MarkStudentPresentResult.InsufficientRemainingVisits
        }

        val updatedPackage =
            studentPackage.copy(
                attendanceHistory =
                PackageAttendanceHistory(
                    visitsByDate =
                    studentPackage.attendanceHistory.visitsByDate +
                        (attendanceDate to visitCount),
                ),
            )

        repository.recordAttendance(
            packageId = updatedPackage.id,
            attendedOn = attendanceDate,
            visitCount = visitCount,
        )

        MarkStudentPresentResult.Success(
            studentPackage = updatedPackage,
        )
    }
}
