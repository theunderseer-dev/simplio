package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogExpirationUiTest {
    @Test
    fun `shows expiration for the default package when first attendance is enabled`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = AttendanceTestData.registrationDate,
            )
        }

        // Then
        onNodeWithText("Record first attendance").assertIsOn()
        onNodeWithText("Valid until Oct 30, 2026")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `updates expiration when package changes and keeps duo validity at four weeks`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = AttendanceTestData.registrationDate,
                packageTypes = listOf(
                    AttendanceTestData.fourClassPackage,
                    AttendanceTestData.fiveClassPackage,
                    AttendanceTestData.duoSpecialPackage,
                ),
            )
        }

        // When
        onNodeWithText("Package").performScrollTo().performClick()
        onNodeWithText(AttendanceTestData.fiveClassPackage.name).performClick()

        // Then
        onNodeWithText("Valid until Nov 6, 2026").performScrollTo().assertIsDisplayed()
        onNodeWithText("Valid until Oct 30, 2026").assertDoesNotExist()

        // When
        onNodeWithText("Package").performScrollTo().performClick()
        onNodeWithText(AttendanceTestData.duoSpecialPackage.name).performClick()

        // Then
        onNodeWithText("Valid until Oct 30, 2026").performScrollTo().assertIsDisplayed()
        onNodeWithText("Valid until Nov 6, 2026").assertDoesNotExist()
    }

    @Test
    fun `updates expiration when registration date changes`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = AttendanceTestData.registrationDate,
            )
        }

        onNodeWithText("Valid until Oct 30, 2026").performScrollTo().assertIsDisplayed()

        // When
        onNodeWithContentDescription("Registration date").performScrollTo().performClick()
        onNodeWithText(
            text = "Thursday, October 1, 2026",
            substring = true,
        ).performClick()
        onNodeWithText("OK").performClick()

        // Then
        onNodeWithText("Valid until Oct 29, 2026").performScrollTo().assertIsDisplayed()
        onNodeWithText("Valid until Oct 30, 2026").assertDoesNotExist()
    }

    @Test
    fun `hides expiration when first attendance is disabled and restores it for the selected package`() =
        runComposeUiTest {
            // Given
            setContent {
                StudentRegistrationDialogForTest(
                    initialDate = AttendanceTestData.registrationDate,
                    packageTypes = AttendanceTestData.availablePackages,
                )
            }
        }
}
