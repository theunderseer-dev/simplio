package com.theunderseer.simplio.feature.attendance.domain.model

import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class InitialAttendanceTest {
    @Test
    fun `rejects zero visits`() {
        assertFailsWith<IllegalArgumentException> {
            InitialAttendance(
                date = firstClassDate,
                visitCount = 0,
            )
        }
    }

    @Test
    fun `rejects negative visits`() {
        assertFailsWith<IllegalArgumentException> {
            InitialAttendance(
                date = firstClassDate,
                visitCount = -1,
            )
        }
    }
}
