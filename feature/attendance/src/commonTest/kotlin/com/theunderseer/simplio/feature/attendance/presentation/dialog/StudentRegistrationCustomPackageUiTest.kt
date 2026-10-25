package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationCustomPackageUiTest {
    @Test
    fun `registration hides unlimited validity hint with or without first attendance`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(packageTypes = listOf(AttendanceTestData.privateEightClassPackage))
        }
        onNodeWithText(AttendanceTestData.privateEightClassPackage.name).performScrollTo().assertIsDisplayed()
        val firstAttendance = onNodeWithText("Record first attendance")
        firstAttendance.performScrollTo().assertIsOn()
        onNodeWithText("No expiration").assertDoesNotExist()
        firstAttendance.performClick().assertIsOff()
        onNodeWithText("No expiration").assertDoesNotExist()
        firstAttendance.performClick().assertIsOn()
        onNodeWithText("No expiration").assertDoesNotExist()
    }
}
