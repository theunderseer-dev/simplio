package com.theunderseer.simplio.feature.attendance.domain.model

data class PackageType(
    val id: Int,
    val name: String,
    val totalClasses: Int,
    val validityWeeks: Int?,
    val price: Int,
    val maxVisitsPerAttendance: Int = 1,
) {
    fun allowsVisitCount(visitCount: Int): Boolean = visitCount in 1..maxVisitsPerAttendance
}
