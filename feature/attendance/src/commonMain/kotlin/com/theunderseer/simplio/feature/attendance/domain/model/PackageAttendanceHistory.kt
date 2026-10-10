package com.theunderseer.simplio.feature.attendance.domain.model

import kotlinx.datetime.LocalDate

class PackageAttendanceHistory(visitsByDate: Map<LocalDate, Int>) {
    val visitsByDate: Map<LocalDate, Int> = visitsByDate.toMap()

    init {
        require(this.visitsByDate.values.all { it > 0 }) {
            "Visits per attendance date must be positive"
        }
    }

    val usedClasses: Int
        get() = visitsByDate.values.sum()

    override fun equals(other: Any?): Boolean = this === other ||
        (other is PackageAttendanceHistory && visitsByDate == other.visitsByDate)

    override fun hashCode(): Int = visitsByDate.hashCode()

    override fun toString(): String = "AttendanceHistory(visitsByDate=$visitsByDate)"
}
