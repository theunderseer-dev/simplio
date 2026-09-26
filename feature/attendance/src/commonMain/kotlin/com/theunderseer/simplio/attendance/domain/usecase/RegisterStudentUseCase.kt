package com.theunderseer.simplio.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

class RegisterStudentUseCase {
    operator fun invoke(
        fullName: String,
        packageType: PackageType,
        registeredOn: LocalDate,
    ) = StudentPackage(
        student = Student(fullName = fullName),
        packageType = packageType,
        startedOn = registeredOn,
        attendanceDates = setOf(registeredOn),
    )
}
