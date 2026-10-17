package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttendanceCheckAvailabilityTest {
    @Test
    fun `first attendance remains checked and check action is enabled`() {
        // When
        val row = rowFor(
            attendanceDates = setOf(firstClassDate),
            currentDate = firstClassDate,
        )

        // Then
        assertEquals(AttendanceCheckState.Checked, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    @Test
    fun `pending attendance check action is enabled`() {
        // When
        val row = rowFor(
            attendanceDates = setOf(firstClassDate),
            currentDate = secondClassDate,
        )

        // Then
        assertEquals(AttendanceCheckState.Pending, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    @Test
    fun `later attendance remains checked and check action is enabled`() {
        // When
        val row = rowFor(
            attendanceDates = setOf(firstClassDate, secondClassDate),
            currentDate = secondClassDate,
        )

        // Then
        assertEquals(AttendanceCheckState.Checked, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    @Test
    fun `package without attendance has an enabled pending check action`() {
        // When
        val row = testStudentAttendanceUiState(
            startedOn = null,
            currentDate = firstClassDate,
        ).rows.single()

        // Then
        assertEquals(AttendanceCheckState.Pending, row.checkState)
        assertTrue(row.isCheckEnabled)
    }

    private fun rowFor(attendanceDates: Set<LocalDate>, currentDate: LocalDate): StudentAttendanceUiState =
        testStudentAttendanceUiState(
            startedOn = firstClassDate,
            visitsByDate = attendanceDates.associateWith { 1 },
            currentDate = currentDate,
        ).rows.single()
}
