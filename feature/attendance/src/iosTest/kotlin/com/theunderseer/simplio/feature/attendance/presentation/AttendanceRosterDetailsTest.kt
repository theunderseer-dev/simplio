package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AttendanceRosterDetailsTest {
    @Test
    fun `opening a student shows their current package and attendance only`() = runComposeUiTest {
        val packageType = AttendanceTestData.duoSpecialPackage
        val student = AttendanceTestData.student.copy(note = "Use the side entrance")
        val attendance = testStudentAttendanceUiState(
            student = student,
            packageType = packageType,
            purchasedOn = LocalDate(2026, 9, 28),
            visitsByDate = mapOf(LocalDate(2026, 10, 13) to 2, LocalDate(2026, 10, 2) to 1),
            previousAttendanceDates = setOf(LocalDate(2026, 9, 21)),
        ).attendance
        val writes = mutableListOf<String>()
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap())),
                now = rosterDate,
                onRefresh = {},
                onSignOut = {},
                onEdit = { error("Details should not open the attendance form") },
                onDeleteStudent = { error("Opening details should not delete the student") },
                onUpdateStudentNote = { _, note ->
                    writes += note
                    null
                },
            )
        }

        onNodeWithText(student.fullName).performTouchInput { click() }
        onNodeWithText("Student details").assertIsDisplayed()
        onNodeWithText(student.fullName).assertIsDisplayed()
        onNodeWithText("3/8").assertIsDisplayed()
        onNodeWithContentDescription("Class 1, Oct 2. People attending: 1").assertIsDisplayed()
        onNodeWithContentDescription("Class 2, Oct 13. People attending: 2").assertIsDisplayed()
        onNodeWithContentDescription("Sep 21", substring = true).assertDoesNotExist()
        onNodeWithText("Purchase date").performScrollTo().assertIsDisplayed()
        onNodeWithText("Sep 28, 2026").assertIsDisplayed()
        onNodeWithText("First attendance").assertExists()
        onNodeWithText("Expiration date").assertExists()
        onNodeWithText("Oct 30, 2026").assertExists()
        onNodeWithText(packageType.name).performScrollTo().assertIsDisplayed()
        onNodeWithTag(NOTE_TAG).performScrollTo().assertTextContains(student.note)

        onNodeWithContentDescription("Back to Attendance").performClick()
        onNodeWithText("Student details").assertDoesNotExist()
        onNodeWithText(student.fullName).assertIsDisplayed()
        runOnIdle { assertTrue(writes.isEmpty()) }
    }

    @Test
    fun `a prepaid package has no visits or expiration until the first attendance`() = runComposeUiTest {
        val attendance = testStudentAttendanceUiState(startedOn = null).attendance
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap())),
                now = rosterDate,
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
            )
        }

        onNodeWithText(attendance.entries.single().student.fullName).performClick()
        onNodeWithText("0/4").assertIsDisplayed()
        onNodeWithText("No attendance recorded").assertIsDisplayed()
        onNodeWithText("Not started").assertIsDisplayed()
        onNodeWithText("Expiration date").assertIsDisplayed()
        onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun `note saves on focus loss and clearing it on back updates the roster`() = runComposeUiTest {
        var roster by mutableStateOf(singleStudentRoster())
        val writes = mutableListOf<String>()
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(roster),
                now = rosterDate,
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
                onUpdateStudentNote = { studentId, note ->
                    assertEquals(roster.attendance.entries.single().student.id, studentId)
                    writes += note
                    roster = roster.withOnlyStudentNote(note)
                    null
                },
            )
        }

        onNodeWithText(AttendanceTestData.student.fullName).performClick()
        val note = onNodeWithTag(NOTE_TAG).performScrollTo()
        note.performTouchInput { click() }
        note.assertIsFocused().performTextReplacement("  Bring a mat  ")
        runOnIdle { assertTrue(writes.isEmpty()) }
        onNodeWithText("Student details").performTouchInput { click() }
        runOnIdle { assertEquals(listOf("Bring a mat"), writes) }
        note.assertTextContains("Bring a mat")

        onNodeWithContentDescription("Back to Attendance").performClick()
        onNodeWithText("Bring a mat").assertIsDisplayed()
        onNodeWithText(AttendanceTestData.student.fullName).performClick()
        note.performScrollTo().performTextReplacement("")
        onNodeWithContentDescription("Back to Attendance").performClick()
        onNodeWithText("Bring a mat").assertDoesNotExist()
        runOnIdle { assertEquals(listOf("Bring a mat", ""), writes) }
    }

    @Test
    fun `back waits for a pending save and a failed save retains the note to retry`() = runComposeUiTest {
        var roster by mutableStateOf(singleStudentRoster())
        val writes = mutableListOf<String>()
        val firstSave = CompletableDeferred<String?>()
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(roster),
                now = rosterDate,
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
                onUpdateStudentNote = { _, note ->
                    writes += note
                    if (writes.size == 1) {
                        firstSave.await()
                    } else {
                        roster = roster.withOnlyStudentNote(note)
                        null
                    }
                },
            )
        }

        onNodeWithText(AttendanceTestData.student.fullName).performClick()
        val note = onNodeWithTag(NOTE_TAG).performScrollTo()
        note.performTextReplacement("Keep the draft")
        onNodeWithText("Student details").performTouchInput { click() }
        val back = onNodeWithContentDescription("Back to Attendance")
        back.performClick()
        onNodeWithText("Student details").assertIsDisplayed()
        runOnIdle { assertEquals(listOf("Keep the draft"), writes) }

        firstSave.complete("Couldn't save the note. Try again.")
        onNodeWithText("Couldn't save the note. Try again.").performScrollTo().assertIsDisplayed()
        note.assertTextContains("Keep the draft")
        back.performClick()
        onNodeWithText("Student details").assertDoesNotExist()
        onNodeWithText("Keep the draft").assertIsDisplayed()
        runOnIdle { assertEquals(List(2) { "Keep the draft" }, writes) }
    }

    private fun singleStudentRoster() = AttendanceRoster(
        attendance = testStudentAttendanceUiState().attendance,
        groupSchedules = emptyMap(),
    )

    private fun AttendanceRoster.withOnlyStudentNote(note: String): AttendanceRoster {
        val entry = attendance.entries.single()
        return copy(attendance = Attendance(listOf(entry.copy(student = entry.student.copy(note = note)))))
    }
}

private val rosterDate = Instant.parse("2026-10-14T10:00:00Z")
private const val NOTE_TAG = "ios-student-details-note"
