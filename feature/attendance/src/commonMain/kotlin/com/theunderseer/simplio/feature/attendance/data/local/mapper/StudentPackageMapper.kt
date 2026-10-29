package com.theunderseer.simplio.feature.attendance.data.local.mapper

import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

fun StudentPackageEntity.toDomain(attendance: List<AttendanceEntity>): StudentPackage = StudentPackage(
    id = id,
    studentId = studentId,
    packageType =
    PackageType(
        id = packageTypeId,
        name = packageName,
        totalClasses = totalClasses,
        validityWeeks = validityWeeks,
        price = price,
        maxVisitsPerAttendance = maxVisitsPerAttendance,
    ),
    purchasedOn = LocalDate.fromEpochDays(purchasedOnEpochDay),
    startedOn = startedOnEpochDay?.let {
        LocalDate.fromEpochDays(it)
    },
    validityExtensions = validityExtensions,
    attendanceHistory =
    PackageAttendanceHistory(
        visitsByDate =
        attendance.associate {
            LocalDate.fromEpochDays(it.attendedOnEpochDay) to it.visitCount
        },
    ),
)
