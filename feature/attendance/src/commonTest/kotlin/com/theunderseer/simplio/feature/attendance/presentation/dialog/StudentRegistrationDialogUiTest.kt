package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogUiTest {
    private val fourClassPackage = AttendanceTestData.fourClassPackage
    private val fiveClassPackage = AttendanceTestData.fiveClassPackage

    private val individualPackages = AttendanceTestData.availablePackages

    private val packagesWithDuo = listOf(
        fourClassPackage,
        AttendanceTestData.duoSpecialPackage,
    )

    private val registrationCallback = StudentRegistrationTestCallback()

    @Test
    fun `displays student registration form`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = individualPackages,
            )
        }

        onNodeWithText("Add Student")
            .assertIsDisplayed()

        onNodeWithText("Full name")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()

        onNodeWithText("Package")
            .assertIsDisplayed()

        onNodeWithText("5-class package")
            .assertDoesNotExist()

        onNodeWithText("Register")
            .assertIsDisplayed()
    }

    @Test
    fun `displays initial registration date`() = runComposeUiTest {
        // When
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = AttendanceTestData.secondClassDate,
            )
        }

        // Then
        onNodeWithText("Registration date")
            .assertIsDisplayed()

        onNodeWithText("Oct 5")
            .assertIsDisplayed()
    }

    @Test
    fun `registers student with entered name and selected package`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = listOf(fiveClassPackage),
                onRegister = registrationCallback::onRegister,
            )
        }

        onNodeWithText("Group A")
            .performClick()

        // When
        onNodeWithTag("registration-full-name")
            .performTextInput("Anna Smith")

        // Then
        onNodeWithText("Register")
            .assertIsNotEnabled()

        // When
        onNodeWithText("Package")
            .performClick()

        onNodeWithText("5-class package")
            .performClick()

        onNodeWithText("Register")
            .assertIsEnabled()
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals("Anna Smith", submission.fullName)
        assertEquals(fiveClassPackage, submission.packageType)
    }

    @Test
    fun `registers student with selected registration date`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                onRegister = registrationCallback::onRegister,
            )
        }

        // When
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

        onNodeWithTag("registration-full-name")
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
    fun `opens date picker when registration date is clicked`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest()
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        // Then
        onNodeWithText("Select registration date")
            .assertIsDisplayed()
    }

    @Test
    fun `updates registration date after selecting past date`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest()
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText(
            text = "Thursday, October 1, 2026",
            substring = true,
        ).performClick()

        onNodeWithText("OK")
            .performClick()

        // Then
        onNodeWithText("Oct 1")
            .assertIsDisplayed()
    }

    @Test
    fun `registration uses four class package by default regardless of list order`() = runComposeUiTest {
        // Given
        val fourClassPackage = AttendanceTestData.fourClassPackage.copy(id = 7)
        val fiveClassPackage = AttendanceTestData.fiveClassPackage.copy(id = 1)

        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = listOf(fiveClassPackage, fourClassPackage),
                onRegister = registrationCallback::onRegister,
            )
        }

        onNodeWithText("Group A")
            .performClick()

        onNodeWithText("Register")
            .assertIsNotEnabled()

        // When
        onNodeWithTag("registration-full-name")
            .performTextInput("Anna Smith")

        onNodeWithText("Register")
            .assertIsEnabled()
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(fourClassPackage, submission.packageType)
    }

    @Test
    fun `registers student with package selected from dropdown`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = individualPackages,
                onRegister = registrationCallback::onRegister,
            )
        }

        onNodeWithText("Group A")
            .performClick()

        onNodeWithText("5-class package")
            .assertDoesNotExist()

        // When
        onNodeWithText("Package")
            .performClick()

        onNodeWithText("5-class package")
            .assertIsDisplayed()
            .performClick()

        // Then
        onNodeWithText("5-class package")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertDoesNotExist()

        // When
        onNodeWithTag("registration-full-name")
            .performTextInput("Anna Smith")

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(fiveClassPackage, submission.packageType)
    }

    @Test
    fun `selecting duo package defaults first attendance to two people`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = packagesWithDuo,
            )
        }

        onNodeWithText("1 person")
            .assertDoesNotExist()

        onNodeWithText("2 people")
            .assertDoesNotExist()

        // When
        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        // Then
        onNodeWithText("1 person").performScrollTo()
            .assertIsDisplayed()

        onNodeWithText("2 people").performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun `provides one visit when one person is selected for duo registration`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = packagesWithDuo,
                onRegister = registrationCallback::onRegister,
            )
        }

        onNodeWithText("Group A")
            .performClick()

        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        // When
        onNodeWithText("1 person").performScrollTo()
            .performClick()

        // Then
        onNodeWithText("1 person").performScrollTo()
            .assertIsSelected()

        onNodeWithText("2 people").performScrollTo()
            .assertIsNotSelected()

        // When
        onNodeWithTag("registration-full-name")
            .performTextInput("Anna & John")

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(1, submission.visitCount)
    }

    @Test
    fun `provides two visits by default when duo student is registered`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialogForTest(
                packageTypes = packagesWithDuo,
                onRegister = registrationCallback::onRegister,
            )
        }

        // When
        onNodeWithText("Group A")
            .performClick()

        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        onNodeWithTag("registration-full-name")
            .performTextInput("Anna & John")

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(2, submission.visitCount)
        assertEquals(true, submission.recordFirstAttendance)
    }
}
