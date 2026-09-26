package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class StudentPackage(
    val student: Student,
    val packageType: PackageType,
    val startedOn: LocalDate,
    val attendanceDates: Set<LocalDate> = setOf(startedOn),
) {
    val usedClasses: Int
        get() = attendanceDates.size

    val expiresOn: LocalDate
        get() =
            startedOn.plus(
                DatePeriod(days = packageType.validityWeeks * 7),
            )
}
