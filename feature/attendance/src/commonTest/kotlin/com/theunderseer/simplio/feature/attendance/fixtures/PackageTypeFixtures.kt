package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

internal fun testClassPackage(id: Int = 1, plan: PackagePlan = PackagePlan.FOUR_CLASSES): PackageType =
    plan.toPackageType(id)

internal fun testPackageTypeEntity(packageType: PackageType, id: Int = 0): PackageTypeEntity = PackageTypeEntity(
    id = id,
    name = packageType.name,
    totalClasses = packageType.totalClasses,
    validityWeeks = packageType.validityWeeks,
    price = packageType.price,
    maxVisitsPerAttendance = packageType.maxVisitsPerAttendance,
)
