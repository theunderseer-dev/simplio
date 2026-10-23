package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentRepository

class GetAttendanceUseCase(
    private val studentRepository: StudentRepository,
    private val studentPackageRepository: StudentPackageRepository,
) {
    suspend operator fun invoke(): Attendance {
        val entries =
            studentRepository
                .getAll()
                .map { student ->
                    // Registration persists the student and initial package atomically,
                    // so a registered student without a package indicates inconsistent data.
                    val studentPackage =
                        checkNotNull(
                            studentPackageRepository
                                .getLatestPackageForStudent(student.id),
                        ) {
                            "Registered student ${student.id} has no package"
                        }

                    RegisteredStudent(
                        student = student,
                        studentPackage = studentPackage,
                        previousAttendanceDates = studentPackageRepository.getAttendanceDatesForStudent(student.id) -
                            studentPackage.attendanceDates,
                    )
                }

        return Attendance(
            entries = entries,
        )
    }
}
