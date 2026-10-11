package com.theunderseer.simplio.feature.attendance.domain.model

enum class PackagePlan(
    val packageName: String,
    val totalClasses: Int,
    val validityWeeks: Int,
    val price: Int,
    val maxVisitsPerAttendance: Int,
) {
    FOUR_CLASSES(
        packageName = "4-class package",
        totalClasses = 4,
        validityWeeks = 4,
        price = 1400,
        maxVisitsPerAttendance = 1,
    ),
    FIVE_CLASSES(
        packageName = "5-class package",
        totalClasses = 5,
        validityWeeks = 5,
        price = 1700,
        maxVisitsPerAttendance = 1,
    ),
    DUO_SPECIAL(
        packageName = "Duo 8 · Special rate",
        totalClasses = 8,
        validityWeeks = 4,
        price = 2400,
        maxVisitsPerAttendance = 2,
    ),
    ;

    fun toPackageType(id: Int): PackageType = PackageType(
        id = id,
        name = packageName,
        totalClasses = totalClasses,
        validityWeeks = validityWeeks,
        price = price,
        maxVisitsPerAttendance = maxVisitsPerAttendance,
    )
}
