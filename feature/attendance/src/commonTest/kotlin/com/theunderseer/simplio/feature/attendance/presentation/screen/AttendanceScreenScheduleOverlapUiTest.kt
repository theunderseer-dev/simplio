package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.schedule.ScheduleOverlap
import com.theunderseer.simplio.feature.attendance.domain.schedule.sessionsBetween
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenScheduleOverlapUiTest {
    @Test
    fun `imported collision is visible without blocking attendance screen actions`() = runComposeUiTest {
        val first = testClassSchedule(id = "a", name = "Morning")
        val second = first.copy(id = "b", name = "Private")
        val date = AttendanceTestData.firstClassDate
        val overlap = ScheduleOverlap(
            first,
            second,
            first.sessionsBetween(date, date).single(),
            second.sessionsBetween(date, date).single(),
        )
        val state = testAttendanceContentUiState(
            Attendance(emptyList()),
            AttendanceTestData.availablePackages,
            AttendanceTestData.currentDate,
        ).copy(classSchedules = listOf(first, second), scheduleOverlaps = listOf(overlap))
        setContent { AttendanceScreenForTest(state) }
        onNodeWithTag("schedule-overlap-notice").assertIsDisplayed()
        onNodeWithText("Overlapping schedules need review. Both schedules are saved.").assertIsDisplayed()
        onNodeWithText("Morning / Private").assertIsDisplayed()
        onNodeWithText("Add Student").assertIsEnabled()
        onNodeWithText("Add Group").assertIsEnabled()
    }
}
