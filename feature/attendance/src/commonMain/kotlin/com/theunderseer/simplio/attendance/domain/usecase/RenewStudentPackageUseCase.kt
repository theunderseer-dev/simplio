package com.theunderseer.simplio.attendance.domain.usecase

import com.theunderseer.simplio.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.attendance.domain.usecase.result.RenewStudentPackageResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

class RenewStudentPackageUseCase(
    private val repository: StudentPackageRepository,
) {
    operator fun invoke(
        studentId: Int,
        packageType: PackageType,
        renewedOn: LocalDate,
    ): RenewStudentPackageResult {
        val latestPackage = repository.getLatestPackageForStudent(studentId)
        if (latestPackage != null && latestPackage.isActiveOn(renewedOn)) {
            return RenewStudentPackageResult.ActivePackageExists(latestPackage)
        }

        val newStudentPackage =
            StudentPackage(
                studentId = studentId,
                packageType = packageType,
                startedOn = renewedOn,
            )

        repository.save(newStudentPackage)
        return RenewStudentPackageResult.Success(studentPackage = newStudentPackage)
    }
}
