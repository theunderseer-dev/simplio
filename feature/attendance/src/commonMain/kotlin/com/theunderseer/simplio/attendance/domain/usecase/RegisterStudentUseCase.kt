package com.theunderseer.simplio.attendance.domain.usecase

import com.theunderseer.simplio.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
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
            StudentPackage(
                studentId = student.id,
                packageType = packageType,
                startedOn = registeredOn,
            )

        studentPackageRepository.save(studentPackage)

        return RegisteredStudent(student = student, studentPackage = studentPackage)
    }
}
