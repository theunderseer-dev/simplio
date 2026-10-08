package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceCheckAvailabilityTest {
    private val firstAttendance = LocalDate(2026, 10, 2)
    private val laterAttendance = LocalDate(2026, 10, 5)

    private val packageType = testFourClassPackage()

    @Test
    fun `first attendance remains checked but check action is disabled`() {
        // When
        val row =
            rowFor(
                attendanceDates = setOf(firstAttendance),
                currentDate = firstAttendance,
            )

        // Then
        assertEquals(AttendanceCheckState.Checked, row.checkState)
        assertFalse(row.isCheckEnabled)
    }

    @Test
    fun `pending attendance check action is enabled`() {
        // When
        val row =
            rowFor(
                attendanceDates = setOf(firstAttendance),
                currentDate = laterAttendance,
            )

        // Then
        assertEquals(AttendanceCheckState.Pending, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    @Test
    fun `later attendance remains checked and check action is enabled`() {
        // When
        val row =
            rowFor(
                attendanceDates = setOf(firstAttendance, laterAttendance),
                currentDate = laterAttendance,
            )

        // Then
        assertEquals(AttendanceCheckState.Checked, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    private fun rowFor(attendanceDates: Set<LocalDate>, currentDate: LocalDate): StudentAttendanceUiState =
        attendanceContentUiState(
            attendance =
            Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = Student(id = 7, fullName = "Anna Smith"),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 7,
                            packageType = packageType,
                            startedOn = firstAttendance,
                            attendanceDates = attendanceDates,
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = currentDate,
        ).rows.single()
}
