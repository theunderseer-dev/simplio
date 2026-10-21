package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
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
class StudentRegistrationDialogScheduleUiTest {
    @Test
    fun `calendar and submission use scheduled dates with or without first attendance`() {
        for (recordAttendance in listOf(true, false)) {
            runComposeUiTest {
                val callback = StudentRegistrationTestCallback()
                setContent {
                    StudentRegistrationDialogForTest(
                        initialDate = AttendanceTestData.currentDate,
                        onRegister = callback::onRegister,
                    )
                }
                onNodeWithTag("registration-full-name").performTextInput(AttendanceTestData.student.fullName)
                onNodeWithText("Group A").performClick()
                if (!recordAttendance) {
                    onNodeWithText("Record first attendance").performScrollTo().performClick()
                }
                onNodeWithContentDescription("Registration date").performScrollTo().performClick()
                onNodeWithText("Friday, October 2, 2026", substring = true).assertIsEnabled()
                onNodeWithText("Monday, October 5, 2026", substring = true).assertIsEnabled()
                for (date in listOf(
                    "Saturday, October 3, 2026",
                    "Sunday, October 4, 2026",
                    "Tuesday, October 6, 2026",
                    "Wednesday, October 7, 2026",
                    "Thursday, October 8, 2026",
                    "Friday, October 9, 2026",
                )) {
                    onNodeWithText(date, substring = true).assertIsNotEnabled()
                }
                onNodeWithText("OK").assertIsEnabled().performClick()
                onNodeWithText("Oct 5").assertIsDisplayed()
                onNodeWithText("Register").assertIsEnabled().performClick()
                val submission = assertNotNull(callback.submission)
                assertEquals(AttendanceTestData.secondClassDate, submission.registeredOn)
                assertEquals(recordAttendance, submission.recordFirstAttendance)
            }
        }
    }
}
