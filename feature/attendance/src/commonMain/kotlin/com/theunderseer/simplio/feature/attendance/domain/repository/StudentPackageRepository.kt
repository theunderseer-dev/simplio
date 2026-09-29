package com.theunderseer.simplio.feature.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

interface StudentPackageRepository {
    fun create(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
    ): StudentPackage

    fun getLatestPackageForStudent(studentId: Int): StudentPackage?

    fun update(studentPackage: StudentPackage)
}
