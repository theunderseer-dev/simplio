package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import kotlinx.datetime.LocalDate

class RegisterStudentUseCase(
    private val studentRepository: StudentRepository,
    private val studentPackageRepository: StudentPackageRepository,
) {
    operator fun invoke(
        fullName: String,
        packageType: PackageType,
        registeredOn: LocalDate,
    ): RegisteredStudent {
        val student = studentRepository.newStudent(fullName = fullName)

        val studentPackage =
            studentPackageRepository.create(
                studentId = student.id,
                packageType = packageType,
                startedOn = registeredOn,
            )

        return RegisteredStudent(student = student, studentPackage = studentPackage)
    }
}
