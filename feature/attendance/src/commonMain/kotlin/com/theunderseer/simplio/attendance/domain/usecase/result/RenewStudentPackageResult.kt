package com.theunderseer.simplio.attendance.domain.usecase.result

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage

sealed interface RenewStudentPackageResult {
    data class Success(
        val studentPackage: StudentPackage,
    ) : RenewStudentPackageResult

    data class ActivePackageExists(
        val studentPackage: StudentPackage,
    ) : RenewStudentPackageResult
}
