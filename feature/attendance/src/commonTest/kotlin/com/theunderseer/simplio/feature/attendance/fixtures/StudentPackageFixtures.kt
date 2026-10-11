package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType

internal fun testStudentPackageEntity(
    studentId: Int,
    packageType: PackageType,
    startedOnEpochDay: Long,
    id: Int = 0,
): StudentPackageEntity = StudentPackageEntity(
    id = id,
    studentId = studentId,
    packageTypeId = packageType.id,
    packageName = packageType.name,
    totalClasses = packageType.totalClasses,
    validityWeeks = packageType.validityWeeks,
    price = packageType.price,
    startedOnEpochDay = startedOnEpochDay,
    maxVisitsPerAttendance = packageType.maxVisitsPerAttendance,
)
