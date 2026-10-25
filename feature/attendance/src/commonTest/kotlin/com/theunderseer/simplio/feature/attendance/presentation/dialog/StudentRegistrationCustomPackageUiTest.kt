package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationCustomPackageUiTest {
    @Test
    fun `registration explains unlimited validity only while first attendance is selected`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(packageTypes = listOf(AttendanceTestData.privateEightClassPackage))
        }
        onNodeWithText(AttendanceTestData.privateEightClassPackage.name).performScrollTo().assertIsDisplayed()
        onNodeWithText("No expiration").performScrollTo().assertIsDisplayed()
        onNodeWithText("Record first attendance").performScrollTo().performClick()
        onNodeWithText("No expiration").assertDoesNotExist()
        onNodeWithText("Record first attendance").performScrollTo().performClick()
        onNodeWithText("No expiration").performScrollTo().assertIsDisplayed()
    }
}
