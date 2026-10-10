package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class StudentPackage(
    val id: Int,
    val studentId: Int,
    val packageType: PackageType,
    val startedOn: LocalDate,
    val attendanceHistory: AttendanceHistory =
        AttendanceHistory(
            visitsByDate = mapOf(startedOn to 1),
        ),
) {
    val attendanceDates: Set<LocalDate>
        get() = attendanceHistory.visitsByDate.keys

    val usedClasses: Int
        get() = attendanceHistory.usedClasses

    val remainingClasses: Int
        get() = (packageType.totalClasses - usedClasses).coerceAtLeast(0)

    val expiresOn: LocalDate
        get() =
            startedOn.plus(
                DatePeriod(days = packageType.validityWeeks * 7),
            )

    val isCompleted: Boolean
        get() = usedClasses >= packageType.totalClasses

    fun isExpiredOn(attendanceDate: LocalDate): Boolean = attendanceDate > expiresOn

    fun isActiveOn(attendanceDate: LocalDate): Boolean = !isExpiredOn(attendanceDate) && !isCompleted

    fun allowsVisitCount(visitCount: Int): Boolean = packageType.allowsVisitCount(visitCount) &&
        visitCount <= remainingClasses
}
