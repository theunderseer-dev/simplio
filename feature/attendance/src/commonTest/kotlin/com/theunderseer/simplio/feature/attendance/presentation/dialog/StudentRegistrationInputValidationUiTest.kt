package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationInputValidationUiTest {
    @Test
    fun `companion names are allowed and overlong paste is rejected without replacing the valid name`() =
        runComposeUiTest {
            val callback = StudentRegistrationTestCallback()
            setContent { StudentRegistrationDialogForTest(onRegister = callback::onRegister) }
            onNodeWithTag("registration-full-name").performTextInput(AttendanceTestData.studentWithCompanionName)
            onNodeWithText("Group A").performClick()
            onNodeWithText("Register").assertIsEnabled()
            onNodeWithTag("registration-full-name").performTextReplacement("x".repeat(201))
            onNodeWithTag("registration-full-name").assertTextContains(AttendanceTestData.studentWithCompanionName)
            onNodeWithText("Use up to 200 characters on one line. Value was not changed.").assertIsDisplayed()
            onNodeWithText("Register").assertIsNotEnabled()
            onNodeWithTag("registration-full-name").performTextReplacement(AttendanceTestData.studentWithCompanionName)
            onNodeWithText("Register").assertIsEnabled().performClick()
            assertEquals(AttendanceTestData.studentWithCompanionName, assertNotNull(callback.submission).fullName)
        }
}
