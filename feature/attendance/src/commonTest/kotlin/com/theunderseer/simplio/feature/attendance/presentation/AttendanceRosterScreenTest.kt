package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AttendanceRosterScreenTest {
    @Test
    fun `roster shows only mobile columns and a disabled renewal control`() = runComposeUiTest {
        val student = AttendanceTestData.student.copy(
            fullName = AttendanceTestData.studentWithCompanionName,
            note = AttendanceTestData.studentNote,
        )
        val packageType = AttendanceTestData.duoSpecialPackage
        val attendance = testStudentAttendanceUiState(student = student, packageType = packageType).attendance
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap())),
                now = Instant.parse("2026-11-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
            )
        }

        onNodeWithText(student.fullName).assertIsDisplayed()
        onNodeWithContentDescription("1 of 8 visits used").assertIsDisplayed()
        onNodeWithText("Expires Oct 30").assertIsDisplayed()
        onNodeWithText("Renew").assertIsNotEnabled()
        onNodeWithText("Oct 2").assertDoesNotExist()
        onNodeWithText(packageType.name).assertDoesNotExist()
        onNodeWithText(student.note).assertDoesNotExist()
    }

    @Test
    fun `empty roster is visible after loading`() = runComposeUiTest {
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
            )
        }
        onNodeWithText("No students yet").assertIsDisplayed()
    }
}
