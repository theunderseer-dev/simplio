package com.theunderseer.simplio.attendance.domain.usecase.result

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage

sealed interface MarkStudentPresentResult {
    data class Success(
        val studentPackage: StudentPackage,
    ) : MarkStudentPresentResult

    data class PackageCompleted(
        val studentPackage: StudentPackage,
    ) : MarkStudentPresentResult
}
