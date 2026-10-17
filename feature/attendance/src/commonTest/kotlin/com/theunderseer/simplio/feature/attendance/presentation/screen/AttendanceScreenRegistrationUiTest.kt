package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenRegistrationUiTest {
    private val today = AttendanceTestData.registrationDate
    private val packageType = testClassPackage()

    @Test
    fun `opens student registration dialog when add student is clicked`() = runComposeUiTest {
        val state = emptyAttendanceState()

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        onNodeWithText("Add Student")
            .performClick()

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
        val state = emptyAttendanceState()

        var registeredOn: LocalDate? = null

        setContent {
            AttendanceScreenForTest(
                state = state,
                onRegisterStudent = { _, _, _, date, _ ->
                    registeredOn = date
                },
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
        assertEquals(
            LocalDate(2026, 10, 1),
            registeredOn,
        )
    }

    @Test
    fun `provides two visits when duo student is registered`() = runComposeUiTest {
        // Given
        val duoPackage = testClassPackage(
            id = 3,
            plan = PackagePlan.DUO_SPECIAL,
        )

        val state = emptyAttendanceState(
            packageTypes = listOf(packageType, duoPackage),
        )

        var registeredVisitCount: Int? = null

        setContent {
            AttendanceScreenForTest(
                state = state,
                onRegisterStudent = { _, _, _, _, visitCount ->
                    registeredVisitCount = visitCount
                },
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
        assertEquals(
            2,
            registeredVisitCount,
        )
    }

    private fun emptyAttendanceState(
        packageTypes: List<PackageType> = listOf(packageType),
        currentDate: LocalDate = today,
    ): AttendanceUiState.Content = testAttendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = packageTypes,
        currentDate = currentDate,
    ).copy(
        studentGroups = DefaultStudentGroups.ALL,
    )
}
