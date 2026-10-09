package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

interface StudentPackageRepository {
    suspend fun create(studentId: Int, packageType: PackageType, startedOn: LocalDate): StudentPackage

    suspend fun getLatestPackageForStudent(studentId: Int): StudentPackage?

    suspend fun recordAttendance(packageId: Int, attendedOn: LocalDate, visitCount: Int = 1)

    suspend fun removeAttendance(packageId: Int, attendedOn: LocalDate)
}
