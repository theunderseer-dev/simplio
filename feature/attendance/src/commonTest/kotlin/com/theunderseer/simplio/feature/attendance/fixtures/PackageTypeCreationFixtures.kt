package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

internal fun testCreatePackageTypeRequest(
    type: PackageType = AttendanceTestData.privateEightClassPackage,
): CreatePackageTypeRequest = CreatePackageTypeRequest(
    name = type.name,
    totalClasses = type.totalClasses,
    price = type.price,
    validityWeeks = type.validityWeeks,
    maxVisitsPerAttendance = type.maxVisitsPerAttendance,
)
