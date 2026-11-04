package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.CreatePackageTypeUiState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenCustomPackageUiTest {
    @Test
    fun `add package opens creation without changing existing students`() = runComposeUiTest {
        val state = testStudentAttendanceUiState()
        setContent { AttendanceScreenForTest(state = state) }
        onNodeWithText("Add Schedule").assertIsDisplayed()
        onNodeWithText("Add Package").assertIsDisplayed().performClick()
        onNodeWithText("Create package").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Create package").assertDoesNotExist()
        onNodeWithText(AttendanceTestData.student.fullName).assertIsDisplayed()
    }

    @Test
    fun `successful creation closes the form and confirms after success is cleared`() = runComposeUiTest {
        val type = AttendanceTestData.privateEightClassPackage
        val state = mutableStateOf(testStudentAttendanceUiState())
        setContent {
            AttendanceScreenForTest(
                state = state.value,
                onCreatePackageType = { request ->
                    state.value = state.value.copy(
                        packageTypes = state.value.packageTypes + type,
                        createPackageTypeState = CreatePackageTypeUiState.Success(request.name),
                    )
                },
                onClearCreatePackageTypeState = {
                    state.value = state.value.copy(createPackageTypeState = CreatePackageTypeUiState.Idle)
                },
            )
        }

        onNodeWithText("Add Package").performClick()
        onNodeWithTag("create-package-name").performTextInput(type.name)
        onNodeWithTag("create-package-classes").performTextInput(type.totalClasses.toString())
        onNodeWithTag("create-package-price").performTextInput(type.price.toString())
        onNodeWithTag("create-package-no-expiration").performClick()
        onNodeWithTag("create-package-confirm").performClick()

        onNodeWithText("Create package").assertDoesNotExist()
        val confirmation = onNodeWithText("Package added: ${type.name}")
        waitUntil(timeoutMillis = 5_000) { runCatching { confirmation.assertIsDisplayed() }.isSuccess }
        confirmation.assertIsDisplayed()
    }

    @Test
    fun `active unlimited package displays no expiration instead of an empty date`() = runComposeUiTest {
        val state = testStudentAttendanceUiState(packageType = AttendanceTestData.privateEightClassPackage)
        setContent { AttendanceScreenForTest(state = state) }
        onNodeWithText("No expiration").assertIsDisplayed()
        onNodeWithText(AttendanceTestData.privateEightClassPackage.name).assertIsDisplayed()
    }
}
