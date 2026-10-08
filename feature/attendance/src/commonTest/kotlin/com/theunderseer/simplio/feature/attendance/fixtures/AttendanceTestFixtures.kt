package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

internal fun testFourClassPackage(id: Int = 1): PackageType = PackageType(
    id = id,
    name = "4-class package",
    totalClasses = 4,
    validityWeeks = 4,
    price = 1400,
)

internal fun testFiveClassPackage(id: Int = 1): PackageType = PackageType(
    id = id,
    name = "5-class package",
    totalClasses = 5,
    validityWeeks = 5,
    price = 1700,
)
