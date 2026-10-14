package com.theunderseer.simplio.feature.attendance.domain.schedule

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceCheckStateTest {
    @Test
    fun `attendance is pending when latest scheduled class is not recorded`() {
        // When
        val state = attendanceCheckState(
            attendanceDates = setOf(
                firstClassDate,
                secondClassDate,
            ),
            latestScheduledClassDate = thirdClassDate,
        )

        // Then
        assertEquals(AttendanceCheckState.Pending, state)
    }

    @Test
    fun `attendance is checked when latest scheduled class is recorded`() {
        // When
        val state = attendanceCheckState(
            attendanceDates = setOf(
                firstClassDate,
                secondClassDate,
                thirdClassDate,
            ),
            latestScheduledClassDate = thirdClassDate,
        )

        // Then
        assertEquals(AttendanceCheckState.Checked, state)
    }

    @Test
    fun `older missing classes do not prevent latest class from being checked`() {
        // When: the second class was skipped
        val state = attendanceCheckState(
            attendanceDates = setOf(
                firstClassDate,
                thirdClassDate,
            ),
            latestScheduledClassDate = thirdClassDate,
        )

        // Then
        assertEquals(AttendanceCheckState.Checked, state)
    }
}
