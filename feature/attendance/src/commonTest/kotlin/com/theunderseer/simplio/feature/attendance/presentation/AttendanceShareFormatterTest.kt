package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceShareFormatterTest {
    @Test
    fun `formats active student for sharing`() {
        // Given
        val attendance = Attendance(
            entries = listOf(
                registeredStudent(
                    visitsByDate = mapOf(
                        firstAttendanceDate to 1,
                        nextAttendanceDate to 1,
                        nextClassDate to 1,
                    ),
                ),
            ),
        )

        // When
        val result = formatAttendanceForSharing(
            attendance = attendance,
            currentDate = nextClassDate,
        )

        // Then
        assertEquals(
            "Anna Smith 3/4 (until 30/10)",
            result,
        )
    }

    @Test
    fun `marks completed package for renewal`() {
        // Given
        val fourthAttendanceDate = LocalDate(2026, 10, 12)
        val attendance = Attendance(
            entries = listOf(
                registeredStudent(
                    visitsByDate = mapOf(
                        firstAttendanceDate to 1,
                        nextAttendanceDate to 1,
                        nextClassDate to 1,
                        fourthAttendanceDate to 1,
                    ),
                ),
            ),
        )

        // When
        val result = formatAttendanceForSharing(
            attendance = attendance,
            currentDate = fourthAttendanceDate,
        )

        // Then
        assertEquals(
            "♻️Anna Smith 4/4 (until 30/10)",
            result,
        )
    }

    @Test
    fun `marks expired package for renewal`() {
        // Given
        val attendance = Attendance(
            entries = listOf(
                registeredStudent(
                    startedOn = LocalDate(2026, 8, 1),
                    visitsByDate = mapOf(
                        LocalDate(2026, 8, 1) to 1,
                        LocalDate(2026, 8, 8) to 1,
                    ),
                ),
            ),
        )

        // When
        val result = formatAttendanceForSharing(
            attendance = attendance,
            currentDate = currentDate,
        )

        // Then
        assertEquals(
            "♻️Anna Smith 2/4 (until 29/08)",
            result,
        )
    }

    @Test
    fun `formats multiple students on separate lines`() {
        // Given
        val attendance = Attendance(
            entries = listOf(
                registeredStudent(
                    visitsByDate = mapOf(
                        firstAttendanceDate to 1,
                        nextAttendanceDate to 1,
                    ),
                ),
                registeredStudent(
                    student = testStudent(
                        id = 2,
                        fullName = "John Doe",
                    ),
                    startedOn = LocalDate(2026, 8, 1),
                    visitsByDate = mapOf(
                        LocalDate(2026, 8, 1) to 1,
                        LocalDate(2026, 8, 8) to 1,
                    ),
                ),
            ),
        )

        // When
        val result = formatAttendanceForSharing(
            attendance = attendance,
            currentDate = currentDate,
        )

        // Then
        assertEquals(
            "Anna Smith 2/4 (until 30/10)\n" +
                "♻️John Doe 2/4 (until 29/08)",
            result,
        )
    }

    private fun registeredStudent(
        student: Student = AttendanceTestData.student,
        startedOn: LocalDate = firstAttendanceDate,
        visitsByDate: Map<LocalDate, Int> = mapOf(startedOn to 1),
    ): RegisteredStudent = RegisteredStudent(
        student = student,
        studentPackage = StudentPackage(
            id = student.id,
            studentId = student.id,
            packageType = fourClassPackage,
            startedOn = startedOn,
            attendanceHistory = PackageAttendanceHistory(
                visitsByDate = visitsByDate,
            ),
        ),
    )
}
