package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceShareFormatterTest {
    private val packageType = testFourClassPackage()

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
            "\tAnna Smith\t3 / 4\t(until 29/10)",
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
            "♻️\tAnna Smith\t4 / 4\t(until 29/10)",
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
            "♻️\tAnna Smith\t2 / 4\t(until 29/08)",
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
            "\tAnna Smith\t2 / 4\t(until 29/10)\n" +
                "♻️\tJohn Doe\t2 / 4\t(until 29/08)",
            result,
        )
    }
}
