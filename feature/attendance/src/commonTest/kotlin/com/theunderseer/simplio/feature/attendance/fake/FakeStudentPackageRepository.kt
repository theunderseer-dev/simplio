package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage

class FakeStudentPackageRepository(
    initialPackages: List<StudentPackage> = emptyList(),
) : StudentPackageRepository {
    private val packages = initialPackages.toMutableList()

    override fun getLatestPackageForStudent(studentId: Int): StudentPackage? =
        packages
            .filter { it.studentId == studentId }
            .maxByOrNull { it.startedOn }

    override fun save(studentPackage: StudentPackage) {
        packages += studentPackage
    }
}
