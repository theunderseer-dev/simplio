package com.theunderseer.simplio.feature.attendance.domain.result

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage

sealed interface MarkStudentPresentResult {
    data class Success(val studentPackage: StudentPackage) : MarkStudentPresentResult

    data class PackageCompleted(val studentPackage: StudentPackage) : MarkStudentPresentResult

    data class PackageExpired(val studentPackage: StudentPackage) : MarkStudentPresentResult

    data object PackageNotFound : MarkStudentPresentResult
}
