package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
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
        groupId: Int,
        packageType: PackageType,
        registeredOn: LocalDate,
        visitCount: Int = 1,
        recordFirstAttendance: Boolean = true,
        note: String = "",
    ): RegisteredStudent = transactionRunner.run {
        if (recordFirstAttendance) {
            require(packageType.allowsVisitCount(visitCount)) {
                "Visit count is not allowed for the selected package"
            }
        }

        val student = studentRepository.create(
            fullName = fullName,
            groupId = groupId,
            note = note.trim(),
        )

        val studentPackage = studentPackageRepository.create(
            studentId = student.id,
            packageType = packageType,
            purchasedOn = registeredOn,
            initialAttendance =
            if (recordFirstAttendance) {
                InitialAttendance(date = registeredOn, visitCount = visitCount)
            } else {
                null
            },
        )

        RegisteredStudent(student = student, studentPackage = studentPackage)
    }
}
