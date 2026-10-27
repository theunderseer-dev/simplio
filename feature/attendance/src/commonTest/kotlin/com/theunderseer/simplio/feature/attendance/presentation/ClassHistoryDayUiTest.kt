package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class ClassHistoryDayUiTest {
    private val date = AttendanceTestData.secondClassDate
    private val morning = Instant.parse("2026-10-05T02:00:00Z")
    private val morningEnd = Instant.parse("2026-10-05T03:15:00Z")
    private val morningLabel = "09:00–10:15 · Asia/Bangkok"

    @Test
    fun `same day and interval show one title one time label and students from both groups`() = runComposeUiTest {
        val anna = ClassHistoryStudent(AttendanceTestData.student, 2)
        val bob = ClassHistoryStudent(testStudent(id = 2, fullName = "Bob Lee", groupId = 2), 1)
        val entries = listOf(
            testClassHistoryEntry(date, listOf(anna), "a", morningLabel, morning, morningEnd),
            testClassHistoryEntry(date, listOf(bob), "b", morningLabel, morning, morningEnd),
            testClassHistoryEntry(
                date,
                sessionId = "empty",
                timeLabel = morningLabel,
                startsAt = morning,
                endsAt = morningEnd,
            ),
        )
        setContent {
            ClassHistorySection(
                ClassHistoryUiState.Content(entries, null),
                AttendanceTestData.studentGroups,
                onLoadNextPage = {},
                onRetry = {},
                onTrackAttendance = {},
            )
        }
        onAllNodesWithText("Oct 5, 2026 (Monday)").assertCountEquals(1)
        onAllNodesWithText(morningLabel).assertCountEquals(1)
        onNodeWithText(anna.student.fullName).assertIsDisplayed()
        onNodeWithText(bob.student.fullName).assertIsDisplayed()
        onNodeWithText("Group A").assertIsDisplayed()
        onNodeWithText("Group B").assertIsDisplayed()
        onNodeWithText("3 visits").assertIsDisplayed()
        onNodeWithText("Attendance not recorded").assertDoesNotExist()
    }

    @Test
    fun `evening and morning appear under one unchanged date title with evening first`() = runComposeUiTest {
        val eveningLabel = "18:00–19:00 · Asia/Bangkok"
        val entries = listOf(
            testClassHistoryEntry(
                date,
                sessionId = "morning",
                timeLabel = morningLabel,
                startsAt = morning,
                endsAt = morningEnd,
            ),
            testClassHistoryEntry(
                date,
                sessionId = "evening",
                timeLabel = eveningLabel,
                startsAt = Instant.parse("2026-10-05T11:00:00Z"),
                endsAt = Instant.parse("2026-10-05T12:00:00Z"),
            ),
        )
        setContent {
            ClassHistorySection(
                ClassHistoryUiState.Content(entries, null),
                AttendanceTestData.studentGroups,
                onLoadNextPage = {},
                onRetry = {},
                onTrackAttendance = {},
            )
        }
        onAllNodesWithText("Oct 5, 2026 (Monday)").assertCountEquals(1)
        onNodeWithText(eveningLabel).assertIsDisplayed()
        onNodeWithText(morningLabel).assertIsDisplayed()
        assertTrue(
            onNodeWithText(eveningLabel).fetchSemanticsNode().boundsInRoot.top <
                onNodeWithText(morningLabel).fetchSemanticsNode().boundsInRoot.top,
        )
    }
}
