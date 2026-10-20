package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import kotlinx.datetime.LocalDate

class RenewStudentPackageUseCase(
    private val repository: StudentPackageRepository,
    private val studentRepository: StudentRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        studentId: Int,
        previousPackageId: Int,
        packageType: PackageType,
        renewedOn: LocalDate,
        note: String,
        visitCount: Int = 1,
        recordFirstAttendance: Boolean = true,
    ): RenewStudentPackageResult = transactionRunner.run {
        val latestPackage = repository.getLatestPackageForStudent(studentId)
            ?: return@run RenewStudentPackageResult.PackageNotFound

        // The dialog may have been opened before another device renewed this student's package.
        if (latestPackage.id != previousPackageId) return@run RenewStudentPackageResult.PackageChanged
        if (latestPackage.isActiveOn(renewedOn)) {
            return@run RenewStudentPackageResult.ActivePackageExists(latestPackage)
        }
        if (renewedOn < latestPackage.earliestRenewalDate) {
            return@run RenewStudentPackageResult.RenewalDateTooEarly(latestPackage.earliestRenewalDate)
        }
        if (recordFirstAttendance &&
            (!packageType.allowsVisitCount(visitCount) || visitCount > packageType.totalClasses)
        ) {
            return@run RenewStudentPackageResult.InvalidVisitCount
        }
        if (!studentRepository.updateNote(studentId, note.trim())) {
            return@run RenewStudentPackageResult.StudentNotFound
        }

        val newPackage = repository.create(
            studentId = studentId,
            packageType = packageType,
            purchasedOn = renewedOn,
            initialAttendance = if (recordFirstAttendance) {
                InitialAttendance(date = renewedOn, visitCount = visitCount)
            } else {
                null
            },
        )
        RenewStudentPackageResult.Success(newPackage)
    }
}
