package com.theunderseer.simplio.feature.attendance.domain.result

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

sealed interface MarkStudentPresentResult {
    data class Success(val studentPackage: StudentPackage) : MarkStudentPresentResult

    data class PackageCompleted(val studentPackage: StudentPackage) : MarkStudentPresentResult

    data class PackageExpired(val studentPackage: StudentPackage) : MarkStudentPresentResult

    data object PackageNotFound : MarkStudentPresentResult

    data object InvalidVisitCount : MarkStudentPresentResult

    data object InsufficientRemainingVisits : MarkStudentPresentResult

    data object AttendanceAlreadyRecorded : MarkStudentPresentResult

    data class AttendanceDateTooEarly(val earliestAllowedDate: LocalDate) : MarkStudentPresentResult
}
