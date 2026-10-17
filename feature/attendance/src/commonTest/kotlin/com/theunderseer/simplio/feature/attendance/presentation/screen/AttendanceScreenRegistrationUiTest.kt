package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenRegistrationUiTest {
    private val today = AttendanceTestData.registrationDate
    private val packageType = AttendanceTestData.fourClassPackage
    private val registrationCallback = StudentRegistrationTestCallback()

    @Test
    fun `opens student registration dialog when add student is clicked`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceScreenForTest(
                state = emptyAttendanceState(),
            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        // Then
        onNodeWithText("Record first attendance")
            .assertIsDisplayed()
            .assertIsOn()

        onNodeWithText("Full name")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()

        onNodeWithText("Register")
            .assertIsDisplayed()
    }

    @Test
    fun `displays add student action when attendance has students`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            packageType = packageType,
            startedOn = today,
            currentDate = today,
        )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `provides selected registration date when student is registered`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceScreenForTest(
                state = emptyAttendanceState(),
                onRegisterStudent = registrationCallback::onRegister,
            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Group A")
            .performClick()

        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText(
            text = "Thursday, October 1, 2026",
            substring = true,
        ).performClick()

        onNodeWithText("OK")
            .performClick()

        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(
            LocalDate(2026, 10, 1),
            submission.registeredOn,
        )
    }

    @Test
    fun `provides two visits when duo student is registered`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState(
            packageTypes = listOf(
                packageType,
                AttendanceTestData.duoSpecialPackage,
            ),
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
                onRegisterStudent = registrationCallback::onRegister,
            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Group A")
            .performClick()

        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        onNode(hasSetTextAction())
            .performTextInput("Anna & John")

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(2, submission.visitCount)
        assertEquals(true, submission.recordFirstAttendance)
    }

    @Test
    fun `provides false when first attendance is unchecked`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceScreenForTest(
                state = emptyAttendanceState(),
                onRegisterStudent = registrationCallback::onRegister,
            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Group A")
            .performClick()

        onNodeWithText("Record first attendance")
            .assertIsOn()
            .performClick()
            .assertIsOff()

        onNode(hasSetTextAction())
            .performTextInput(AttendanceTestData.student.fullName)

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(false, submission.recordFirstAttendance)
    }

    @Test
    fun `hides duo visit selection when first attendance is unchecked`() = runComposeUiTest {
        // Given
        setContent {
            AttendanceScreenForTest(
                state = emptyAttendanceState(
                    packageTypes = listOf(
                        packageType,
                        AttendanceTestData.duoSpecialPackage,
                    ),
                ),
            )
        }

        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        onNodeWithText("1 person")
            .assertIsDisplayed()

        onNodeWithText("2 people")
            .assertIsDisplayed()

        // When
        onNodeWithText("Record first attendance")
            .performClick()

        // Then
        onNodeWithText("People attending")
            .assertDoesNotExist()

        onNodeWithText("1 person")
            .assertDoesNotExist()

        onNodeWithText("2 people")
            .assertDoesNotExist()

        // When
        onNodeWithText("Record first attendance")
            .performClick()

        // Then
        onNodeWithText("1 person")
            .assertIsDisplayed()

        onNodeWithText("2 people")
            .assertIsDisplayed()
    }

    private fun emptyAttendanceState(
        packageTypes: List<PackageType> = listOf(packageType),
        currentDate: LocalDate = today,
    ): AttendanceUiState.Content = testAttendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = packageTypes,
        currentDate = currentDate,
    )
}
