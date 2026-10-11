package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceCheckAvailabilityTest {
    private val firstAttendance = LocalDate(2026, 10, 2)
    private val laterAttendance = LocalDate(2026, 10, 5)

    private val packageType = testClassPackage()

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
                        student = testStudent(),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = packageType,
                            startedOn = firstAttendance,
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate = attendanceDates.associateWith { 1 },
                            ),
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = currentDate,
        ).rows.single()
}
