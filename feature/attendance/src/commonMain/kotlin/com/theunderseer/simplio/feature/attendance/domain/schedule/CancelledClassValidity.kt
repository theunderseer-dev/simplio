package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Eligibility is historical; later attendance or renewal must not erase the lost validity date. */
internal fun StudentPackage.isEligibleForCancellationOn(date: LocalDate): Boolean {
    val start = startedOn ?: return false
    val expiration = expiresOn ?: return false
    val usedOnDate = attendanceHistory.visitsByDate.filterKeys { it <= date }.values.sumOf { it.toLong() }
    return date >= maxOf(purchasedOn, start) && date <= expiration && usedOnDate < packageType.totalClasses
}

/** Shift by one distinct recurring class date, without changing purchase terms or credits. */
internal fun ClassSchedule.nextValidityDate(after: LocalDate): LocalDate {
    // Series lifetime controls which classes can be tracked, not a purchased package's compensation.
    val recurring = copy(startsOn = null, endsOn = null, archivedAt = null)
    val first = after.plus(DatePeriod(days = 1))
    return checkNotNull(recurring.sessionsBetween(first, after.plus(DatePeriod(days = 14))).firstOrNull()).date
}
