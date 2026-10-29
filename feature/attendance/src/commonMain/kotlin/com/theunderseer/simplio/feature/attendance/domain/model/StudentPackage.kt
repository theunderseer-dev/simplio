package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class StudentPackage(
    val id: Int,
    val studentId: Int,
    val packageType: PackageType,
    val purchasedOn: LocalDate,
    val startedOn: LocalDate? = null,
    val attendanceHistory: PackageAttendanceHistory =
        PackageAttendanceHistory(
            visitsByDate = emptyMap(),
        ),
    val validityExtensions: Map<String, Int> = emptyMap(),
) {
    val validityExtensionDays: Int
        get() = validityExtensions.values.sumOf { it.toLong() }.also {
            require(validityExtensions.values.all { days -> days > 0 } && it <= Int.MAX_VALUE)
        }.toInt()
    val attendanceDates: Set<LocalDate>
        get() = attendanceHistory.visitsByDate.keys

    val usedClasses: Int
        get() = attendanceHistory.usedClasses

    val remainingClasses: Int
        get() = (packageType.totalClasses - usedClasses).coerceAtLeast(0)

    val expiresOn: LocalDate?
        get() = packageType.validityWeeks?.let { weeks ->
            startedOn?.plus(DatePeriod(days = weeks * 7))?.plus(DatePeriod(days = validityExtensionDays))
        }

    val isCompleted: Boolean
        get() = usedClasses >= packageType.totalClasses

    val earliestRenewalDate: LocalDate
        get() {
            val lastDate = maxOf(purchasedOn, attendanceDates.maxOrNull() ?: purchasedOn)
            val expirationDate = expiresOn
            return if (!isCompleted && expirationDate != null) {
                maxOf(lastDate, expirationDate.plus(DatePeriod(days = 1)))
            } else {
                lastDate
            }
        }

    fun isExpiredOn(attendanceDate: LocalDate): Boolean = expiresOn?.let { attendanceDate > it } ?: false

    fun isActiveOn(attendanceDate: LocalDate): Boolean = !isExpiredOn(attendanceDate) && !isCompleted

    fun allowsVisitCount(visitCount: Int): Boolean = packageType.allowsVisitCount(visitCount) &&
        visitCount <= remainingClasses
}
