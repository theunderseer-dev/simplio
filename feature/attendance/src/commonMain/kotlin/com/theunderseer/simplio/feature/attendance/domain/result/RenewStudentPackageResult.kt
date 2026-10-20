package com.theunderseer.simplio.feature.attendance.domain.result

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

sealed interface RenewStudentPackageResult {
    data class Success(val studentPackage: StudentPackage) : RenewStudentPackageResult

    data class ActivePackageExists(val studentPackage: StudentPackage) : RenewStudentPackageResult
    data object PackageNotFound : RenewStudentPackageResult

    data object StudentNotFound : RenewStudentPackageResult

    data object PackageChanged : RenewStudentPackageResult

    data object InvalidVisitCount : RenewStudentPackageResult

    data class RenewalDateTooEarly(val earliestAllowedDate: LocalDate) : RenewStudentPackageResult
}
