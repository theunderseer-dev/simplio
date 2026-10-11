package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceShareFormatterTest {
    private val student = testStudent()
    private val packageType = testClassPackage()

    private val today = LocalDate(2026, 10, 6)

    @Test
    fun `formats active student for sharing`() {
        // Given
        val attendance =
            Attendance(
                entries =
                listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 1),
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    LocalDate(2026, 10, 1) to 1,
                                    LocalDate(2026, 10, 3) to 1,
                                    LocalDate(2026, 10, 5) to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = today,
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
                        student = student,
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 1),
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    LocalDate(2026, 10, 1) to 1,
                                    LocalDate(2026, 10, 2) to 1,
                                    LocalDate(2026, 10, 3) to 1,
                                    LocalDate(2026, 10, 4) to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = today,
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
                        student = student,
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 8, 1),
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    LocalDate(2026, 8, 1) to 1,
                                    LocalDate(2026, 8, 8) to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = today,
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
                        student = student,
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 1),
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    LocalDate(2026, 10, 1) to 1,
                                    LocalDate(2026, 10, 3) to 1,
                                ),
                            ),
                        ),
                    ),
                    RegisteredStudent(
                        student =
                        Student(
                            id = 2,
                            fullName = "John Doe",
                            groupId = 1,
                        ),
                        studentPackage =
                        StudentPackage(
                            id = 2,
                            studentId = 2,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 8, 1),
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    LocalDate(2026, 8, 1) to 1,
                                    LocalDate(2026, 8, 8) to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            )

        // When
        val result =
            formatAttendanceForSharing(
                attendance = attendance,
                currentDate = today,
            )

        // Then
        assertEquals(
            "\tAnna Smith\t2 / 4\t(until 29/10)\n" +
                "♻️\tJohn Doe\t2 / 4\t(until 29/08)",
            result,
        )
    }
}
