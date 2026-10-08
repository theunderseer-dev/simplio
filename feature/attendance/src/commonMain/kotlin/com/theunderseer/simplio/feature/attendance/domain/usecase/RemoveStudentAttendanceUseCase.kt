package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RemoveStudentAttendanceResult
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import kotlinx.datetime.LocalDate

class RemoveStudentAttendanceUseCase(
    private val repository: StudentPackageRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(studentId: Int, attendanceDate: LocalDate): RemoveStudentAttendanceResult =
        transactionRunner.run {
            val studentPackage =
                repository.getLatestPackageForStudent(studentId)
                    ?: return@run RemoveStudentAttendanceResult.PackageNotFound

            if (attendanceDate == studentPackage.startedOn) {
                return@run RemoveStudentAttendanceResult.FirstAttendanceCannotBeRemoved
            }

            if (attendanceDate !in studentPackage.attendanceDates) {
                return@run RemoveStudentAttendanceResult.Success(
                    studentPackage = studentPackage,
                )
            }

            repository.removeAttendance(
                packageId = studentPackage.id,
                attendedOn = attendanceDate,
            )

            RemoveStudentAttendanceResult.Success(
                studentPackage =
                studentPackage.copy(
                    attendanceDates = studentPackage.attendanceDates - attendanceDate,
                ),
            )
        }
}
