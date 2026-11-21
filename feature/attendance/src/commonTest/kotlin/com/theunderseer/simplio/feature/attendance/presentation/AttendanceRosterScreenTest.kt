package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AttendanceRosterScreenTest {
    @Test
    fun `roster shows only mobile columns and offers renewal`() = runComposeUiTest {
        val student = AttendanceTestData.student.copy(
            fullName = AttendanceTestData.studentWithCompanionName,
            note = AttendanceTestData.studentNote,
        )
        val packageType = AttendanceTestData.duoSpecialPackage
        val attendance = testStudentAttendanceUiState(student = student, packageType = packageType).attendance
        var edit: AttendanceRosterEdit? = null
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap(), listOf(packageType))),
                now = Instant.parse("2026-11-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = {
                    edit = it
                    null
                },
            )
        }

        onNodeWithText(student.fullName).assertIsDisplayed()
        onNodeWithContentDescription("1 of 8 visits used").assertIsDisplayed()
        onNodeWithText("Expires Oct 30").assertIsDisplayed()
        onNodeWithText("Renew").assertIsEnabled()
        onNodeWithText("Oct 2").assertDoesNotExist()
        onNodeWithText(packageType.name).assertDoesNotExist()
        onNodeWithText(student.note).assertDoesNotExist()
        onNodeWithText("Renew").performClick()
        val dialog = hasAnyAncestor(isDialog())
        onNode(dialog and hasText("Purchase date")).assertExists()
        onNode(dialog and hasText("Record first attendance")).performScrollTo().performClick()
        onNodeWithTag("registration-note").performScrollTo().performTextReplacement("Renewal note")
        onNode(dialog and hasText("Renew")).performClick()
        runOnIdle {
            val renewal = assertIs<AttendanceRosterEdit.Renew>(edit)
            assertEquals(LocalDate(2026, 11, 2), renewal.date)
            assertEquals(packageType, renewal.template)
            assertEquals("Renewal note", renewal.note)
            assertFalse(renewal.recordFirstAttendance)
        }
    }

    @Test
    fun `empty roster is visible after loading`() = runComposeUiTest {
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
            )
        }
        onNodeWithText("No students yet").assertIsDisplayed()
    }

    @Test
    fun `check opens the shared calendar and saves the selected class`() = runComposeUiTest {
        val attendance = testStudentAttendanceUiState(startedOn = null).attendance
        var edit: AttendanceRosterEdit? = null
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = {
                    edit = it
                    null
                },
            )
        }
        onNodeWithText("Check").performClick()
        val dialog = hasAnyAncestor(isDialog())
        onNode(dialog and hasText("Select attendance date")).assertIsDisplayed()
        onNode(dialog and hasText("OK")).performClick()
        runOnIdle {
            val marked = assertIs<AttendanceRosterEdit.Mark>(edit)
            assertEquals(LocalDate(2026, 10, 2), marked.date)
            assertEquals(1, marked.visits)
        }
        onNode(dialog and hasText("Select attendance date")).assertDoesNotExist()
    }
}
