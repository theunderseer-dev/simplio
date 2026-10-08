package com.theunderseer.simplio.feature.attendance.domain.usecase

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.presentation.testFiveClassPackage
import com.theunderseer.simplio.feature.attendance.presentation.testFourClassPackage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetAttendanceUseCaseTest {
    @Test
    fun `returns registered students with their packages`() = runTest {
        // Given
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val anna =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        val bob =
            studentRepository.seed(
                Student(
                    id = 2,
                    fullName = "Bob Lee",
                ),
            )

        val packageType = testFourClassPackage()

        val annaPackage =
            studentPackageRepository.seed(
                studentId = anna.id,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 1),
            )

        val bobPackage =
            studentPackageRepository.seed(
                studentId = bob.id,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 2),
            )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        // When
        val attendance =
            getAttendance()

        // Then
        assertEquals(
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student = anna,
                        studentPackage = annaPackage,
                    ),
                    RegisteredStudent(
                        student = bob,
                        studentPackage = bobPackage,
                    ),
                ),
            ),
            attendance,
        )
    }

    @Test
    fun `returns empty attendance when no students are registered`() = runTest {
        // Given
        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = FakeStudentRepository(),
                studentPackageRepository = FakeStudentPackageRepository(),
            )

        // When
        val attendance =
            getAttendance()

        // Then
        assertEquals(
            Attendance(
                entries = emptyList(),
            ),
            attendance,
        )
    }

    @Test
    fun `fails when registered student has no package`() = runTest {
        // Given
        val studentRepository =
            FakeStudentRepository()

        val studentPackageRepository =
            FakeStudentPackageRepository()

        val student =
            studentRepository.seed(
                Student(
                    id = 1,
                    fullName = "Anna Smith",
                ),
            )

        val getAttendance =
            GetAttendanceUseCase(
                studentRepository = studentRepository,
                studentPackageRepository = studentPackageRepository,
            )

        // When / Then
        val error =
            assertFailsWith<IllegalStateException> {
                getAttendance()
            }

        assertEquals(
            "Registered student ${student.id} has no package",
            error.message,
        )
    }
}
