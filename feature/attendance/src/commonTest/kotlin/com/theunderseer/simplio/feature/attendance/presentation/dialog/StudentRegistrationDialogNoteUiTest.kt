package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogNoteUiTest {
    @Test
    fun `registration provides trimmed optional note`() = runComposeUiTest {
        val callback = StudentRegistrationTestCallback()
        setContent { StudentRegistrationDialogForTest(onRegister = callback::onRegister) }

        onNodeWithTag("registration-full-name").performTextInput(AttendanceTestData.student.fullName)
        onNodeWithTag("registration-note").performTextInput("  ${AttendanceTestData.studentNote}  ")
        onNodeWithText("Group A").performScrollTo().performClick()
        onNodeWithText("Register").performClick()

        assertEquals(AttendanceTestData.studentNote, assertNotNull(callback.submission).note)
    }

    @Test
    fun `note can be left empty when registering a student`() = runComposeUiTest {
        val callback = StudentRegistrationTestCallback()
        setContent { StudentRegistrationDialogForTest(onRegister = callback::onRegister) }

        onNodeWithTag("registration-full-name").performTextInput(AttendanceTestData.student.fullName)
        onNodeWithText("Group A").performScrollTo().performClick()
        onNodeWithText("Register").performClick()

        assertEquals("", assertNotNull(callback.submission).note)
    }
}
