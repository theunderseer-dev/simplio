package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner
import kotlinx.datetime.LocalDate

class RegisterStudentUseCase(
    private val studentRepository: StudentRepository,
    private val studentPackageRepository: StudentPackageRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        fullName: String,
        packageType: PackageType,
        registeredOn: LocalDate,
        visitCount: Int = 1,
    ): RegisteredStudent = transactionRunner.run {
        require(packageType.allowsVisitCount(visitCount)) {
            "Visit count is not allowed for the selected package"
        }

        val student = studentRepository.create(fullName = fullName)

        val studentPackage =
            studentPackageRepository.create(
                studentId = student.id,
                packageType = packageType,
                startedOn = registeredOn,
                visitCount = visitCount,
            )

        RegisteredStudent(student = student, studentPackage = studentPackage)
    }
}
