package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class StudentPackage(
    val studentId: Int,
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

    val isCompleted: Boolean
        get() = usedClasses >= packageType.totalClasses

    fun isExpiredOn(attendanceDate: LocalDate): Boolean = attendanceDate > expiresOn

    fun isActiveOn(attendanceDate: LocalDate): Boolean = !isExpiredOn(attendanceDate) && !isCompleted
}
