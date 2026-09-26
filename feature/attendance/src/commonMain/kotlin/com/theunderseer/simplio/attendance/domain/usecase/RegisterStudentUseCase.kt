package com.theunderseer.simplio.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegistrationDetails
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate

class RegisterStudentUseCase {
    operator fun invoke(
        fullName: String,
        packageType: PackageType,
        registeredOn: LocalDate,
    ): RegistrationDetails =
        RegistrationDetails(
            student = Student(fullName = fullName),
            studentPackage =
                StudentPackage(
                    packageType = packageType,
                    startedOn = registeredOn,
                    attendanceDates = setOf(registeredOn),
                ),
        )
}
