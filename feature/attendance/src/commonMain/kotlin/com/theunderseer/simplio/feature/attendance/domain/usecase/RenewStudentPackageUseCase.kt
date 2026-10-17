package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import kotlinx.datetime.LocalDate

class RenewStudentPackageUseCase(
    private val repository: StudentPackageRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        studentId: Int,
        packageType: PackageType,
        renewedOn: LocalDate,
    ): RenewStudentPackageResult = transactionRunner.run {
        val latestPackage = repository.getLatestPackageForStudent(studentId)

        if (latestPackage != null &&
            latestPackage.isActiveOn(renewedOn)
        ) {
            return@run RenewStudentPackageResult.ActivePackageExists(
                studentPackage = latestPackage,
            )
        }

        val newStudentPackage =
            repository.create(
                studentId = studentId,
                packageType = packageType,
                purchasedOn = renewedOn,
                initialAttendance = InitialAttendance(
                    date = renewedOn,
                ),
            )

        RenewStudentPackageResult.Success(studentPackage = newStudentPackage)
    }
}
