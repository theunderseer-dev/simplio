package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.UpdateStudentNoteUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenStudentNoteUiTest {
    private val student = AttendanceTestData.student.copy(note = AttendanceTestData.studentNote)

    @Test
    fun `displays existing note and prefills editor while cancel leaves it unchanged`() = runComposeUiTest {
        val requests = mutableListOf<Pair<Int, String>>()
        setContent {
            AttendanceScreenForTest(
                state = testStudentAttendanceUiState(student = student),
                onUpdateStudentNote = { id, note -> requests += id to note },
            )
        }

        onNodeWithTag("student-note-${student.id}")
            .assertTextContains(student.note)
            .performClick()
        onNodeWithTag("student-note-input").assertTextContains(student.note)
        onNodeWithTag("student-note-input").performTextReplacement("Changed draft")
        onNodeWithText("Cancel").performClick()

        onNodeWithTag("student-note-input").assertDoesNotExist()
        onNodeWithTag("student-note-${student.id}").assertTextContains(student.note)
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `clearing note sends empty string for the selected student`() = runComposeUiTest {
        val requests = mutableListOf<Pair<Int, String>>()
        setContent {
            AttendanceScreenForTest(
                state = testStudentAttendanceUiState(student = student),
                onUpdateStudentNote = { id, note -> requests += id to note },
            )
        }

        onNodeWithTag("student-note-${student.id}").performClick()
        onNodeWithTag("student-note-input").performTextClearance()
        onNodeWithText("Save").performClick()

        assertEquals(listOf(student.id to ""), requests)
    }

    @Test
    fun `failed save preserves draft and allows retry before closing on success`() = runComposeUiTest {
        var state by mutableStateOf(testStudentAttendanceUiState(student = student))
        var requests = 0
        val newNote = "Bring a receipt next time"
        setContent {
            AttendanceScreenForTest(
                state = state,
                onUpdateStudentNote = { id, note ->
                    requests++
                    state = if (requests == 1) {
                        state.copy(updateStudentNoteState = UpdateStudentNoteUiState.Error(id, "Could not save note"))
                    } else {
                        testStudentAttendanceUiState(student = student.copy(note = note)).copy(
                            updateStudentNoteState = UpdateStudentNoteUiState.Success(id),
                        )
                    }
                },
                onClearUpdateStudentNoteState = {
                    state = state.copy(updateStudentNoteState = UpdateStudentNoteUiState.Idle)
                },
            )
        }

        onNodeWithTag("student-note-${student.id}").performClick()
        onNodeWithTag("student-note-input").performTextReplacement(newNote)
        onNodeWithText("Save").performClick()

        onNodeWithText("Could not save note").assertIsDisplayed()
        onNodeWithTag("student-note-input").assertTextContains(newNote)
        onNodeWithText("Save").performClick()

        onNodeWithTag("student-note-input").assertDoesNotExist()
        onNodeWithTag("student-note-${student.id}").assertTextContains(newNote)
        assertEquals(2, requests)
    }

    @Test
    fun `saving disables editor and buttons until the note has been saved`() = runComposeUiTest {
        var state by mutableStateOf(testStudentAttendanceUiState(student = student))
        var requests = 0
        setContent {
            AttendanceScreenForTest(
                state = state,
                onUpdateStudentNote = { id, _ ->
                    requests++
                    state = state.copy(updateStudentNoteState = UpdateStudentNoteUiState.Saving(id))
                },
            )
        }

        onNodeWithTag("student-note-${student.id}").performClick()
        onNodeWithText("Save").performClick()

        onNodeWithTag("student-note-input").assertIsNotEnabled()
        onNodeWithText("Saving...").assertIsNotEnabled()
        onNodeWithText("Cancel").assertIsNotEnabled()
        assertEquals(1, requests)
    }
}
