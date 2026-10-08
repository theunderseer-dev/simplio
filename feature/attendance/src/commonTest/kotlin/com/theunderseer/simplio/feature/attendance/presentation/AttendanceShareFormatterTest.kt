package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceShareFormatterTest {
    private val packageType =
        PackageType(
            id = 1,
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 6,
            price = 1700,
        )

    @Test
    fun `formats active student for sharing`() {
        // Given
        val attendance =
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student =
                        Student(
                            id = 1,
                            fullName = "Anna Smith",
                        ),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 1),
                            attendanceDates =
                            setOf(
                                LocalDate(2026, 10, 1),
                                LocalDate(2026, 10, 3),
                                LocalDate(2026, 10, 5),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = LocalDate(2026, 10, 6),
            )

        // Then
        assertEquals(
            "\tAnna Smith\t3 / 5\t(until 12/11)",
            result,
        )
    }

    @Test
    fun `marks completed package for renewal`() {
        // Given
        val attendance =
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student =
                        Student(
                            id = 1,
                            fullName = "Anna Smith",
                        ),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 1),
                            attendanceDates =
                            setOf(
                                LocalDate(2026, 10, 1),
                                LocalDate(2026, 10, 2),
                                LocalDate(2026, 10, 3),
                                LocalDate(2026, 10, 4),
                                LocalDate(2026, 10, 5),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = LocalDate(2026, 10, 6),
            )

        // Then
        assertEquals(
            "♻️\tAnna Smith\t5 / 5\t(until 12/11)",
            result,
        )
    }

    @Test
    fun `marks expired package for renewal`() {
        // Given
        val attendance =
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student =
                        Student(
                            id = 1,
                            fullName = "Anna Smith",
                        ),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 8, 1),
                            attendanceDates =
                            setOf(
                                LocalDate(2026, 8, 1),
                                LocalDate(2026, 8, 8),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = LocalDate(2026, 10, 6),
            )

        // Then
        assertEquals(
            "♻️\tAnna Smith\t2 / 5\t(until 12/09)",
            result,
        )
    }

    @Test
    fun `formats multiple students on separate lines`() {
        // Given
        val attendance =
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student =
                        Student(
                            id = 1,
                            fullName = "Anna Smith",
                        ),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 1),
                            attendanceDates =
                            setOf(
                                LocalDate(2026, 10, 1),
                                LocalDate(2026, 10, 3),
                            ),
                        ),
                    ),
                    RegisteredStudent(
                        student =
                        Student(
                            id = 2,
                            fullName = "John Doe",
                        ),
                        studentPackage =
                        StudentPackage(
                            id = 2,
                            studentId = 2,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 8, 1),
                            attendanceDates =
                            setOf(
                                LocalDate(2026, 8, 1),
                                LocalDate(2026, 8, 8),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = LocalDate(2026, 10, 6),
            )

        // Then
        assertEquals(
            "\tAnna Smith\t2 / 5\t(until 12/11)\n" +
                "♻️\tJohn Doe\t2 / 5\t(until 12/09)",
            result,
        )
    }
}
