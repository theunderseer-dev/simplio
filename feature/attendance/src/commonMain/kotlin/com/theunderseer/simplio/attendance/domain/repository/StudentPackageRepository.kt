package com.theunderseer.simplio.attendance.domain.repository

import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage

interface StudentPackageRepository {
    fun getLatestPackageForStudent(studentId: Int): StudentPackage?

    fun save(studentPackage: StudentPackage)
}
