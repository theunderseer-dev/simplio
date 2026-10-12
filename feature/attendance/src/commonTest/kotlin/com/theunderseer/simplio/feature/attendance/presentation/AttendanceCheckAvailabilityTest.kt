package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttendanceCheckAvailabilityTest {
    @Test
    fun `first attendance remains checked but check action is disabled`() {
        // When
        val row =
            rowFor(
                attendanceDates = setOf(firstAttendanceDate),
                currentDate = firstAttendanceDate,
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
                attendanceDates = setOf(firstAttendanceDate),
                currentDate = nextAttendanceDate,
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
                attendanceDates = setOf(firstAttendanceDate, nextAttendanceDate),
                currentDate = nextAttendanceDate,
            )

        // Then
        assertEquals(AttendanceCheckState.Checked, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    private fun rowFor(attendanceDates: Set<LocalDate>, currentDate: LocalDate): StudentAttendanceUiState =
        testAttendanceContentUiState(
            attendance =
            Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = testStudent(),
                        studentPackage =
                        StudentPackage(
                            id = 1,
                            studentId = 1,
                            packageType = fourClassPackage,
                            startedOn = firstAttendanceDate,
                            attendanceHistory =
                            PackageAttendanceHistory(
                                visitsByDate = attendanceDates.associateWith { 1 },
                            ),
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(fourClassPackage),
            currentDate = currentDate,
        ).rows.single()
}
