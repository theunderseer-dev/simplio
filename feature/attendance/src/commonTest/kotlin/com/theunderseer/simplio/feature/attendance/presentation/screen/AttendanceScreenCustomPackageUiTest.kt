package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenCustomPackageUiTest {
    @Test
    fun `add package opens creation without changing existing students`() = runComposeUiTest {
        val state = testStudentAttendanceUiState()
        setContent { AttendanceScreenForTest(state = state) }
        onNodeWithText("Add Group").assertIsDisplayed()
        onNodeWithText("Add Package").assertIsDisplayed().performClick()
        onNodeWithText("Create package").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Create package").assertDoesNotExist()
        onNodeWithText(AttendanceTestData.student.fullName).assertIsDisplayed()
    }

    @Test
    fun `active unlimited package displays no expiration instead of an empty date`() = runComposeUiTest {
        val state = testStudentAttendanceUiState(packageType = AttendanceTestData.privateEightClassPackage)
        setContent { AttendanceScreenForTest(state = state) }
        onNodeWithText("No expiration").assertIsDisplayed()
        onNodeWithText(AttendanceTestData.privateEightClassPackage.name).assertIsDisplayed()
    }
}
