package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val singlePackage = testFourClassPackage()
    val duoPackage =
        PackageType(
            id = 3,
            name = "Duo 8 · Special rate",
            totalClasses = 8,
            validityWeeks = 4,
            price = 2400,
            maxVisitsPerAttendance = 2,
        )

    @Test
    fun `displays student registration form`() = runComposeUiTest {
        val packageTypes = listOf(
            testFourClassPackage(),
            testFiveClassPackage(),
        )

        val registeredDate = LocalDate(2026, 10, 5)

        setContent {
            StudentRegistrationDialog(
                packageTypes = packageTypes,
                initialDate = registeredDate,
                onRegister = { _, _, _, _ -> },
                onDismiss = {},
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
        // Given
        val today =
            LocalDate(2026, 10, 5)

        val packageTypes = listOf(testFourClassPackage())

        // When
        setContent {
            StudentRegistrationDialog(
                packageTypes = packageTypes,
                initialDate = today,
                onRegister = { _, _, _, _ -> },
                onDismiss = {},
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
        val packageType = testFiveClassPackage()

        val registeredDate = LocalDate(2026, 10, 5)

        var registeredName: String? = null
        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(packageType),
                initialDate = registeredDate,
                onRegister = { fullName, selectedPackage, _, _ ->
                    registeredName = fullName
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        // When
        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        // Then: a package must still be selected
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
    }

    @Test
    fun `registers student with selected registration date`() = runComposeUiTest {
        // Given
        var registeredOn: LocalDate? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(singlePackage),
                initialDate = LocalDate(2026, 10, 6),
                onRegister = { _, _, date, _ ->
                    registeredOn = date
                },
                onDismiss = {},
            )
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText("Saturday, October 3, 2026")
            .performClick()

        onNodeWithText("OK")
            .performClick()

        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            LocalDate(2026, 10, 3),
            registeredOn,
        )
    }

    @Test
    fun `opens date picker when registration date is clicked`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(singlePackage),
                initialDate = today,
                onRegister = { _, _, _, _ -> },
                onDismiss = {},
            )
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
            StudentRegistrationDialog(
                packageTypes = listOf(singlePackage),
                initialDate = today,
                onRegister = { _, _, _, _ -> },
                onDismiss = {},
            )
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText(
            text = "Saturday, October 3, 2026",
            substring = true,
        )
            .performClick()

        onNodeWithText("OK")
            .performClick()

        onNodeWithText("Oct 3")
            .assertIsDisplayed()
    }

    @Test
    fun `registration uses four class package by default regardless of list order`() = runComposeUiTest {
        // Given
        val fourClassPackage =
            PackageType(
                id = 7,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
            )

        val fiveClassPackage =
            PackageType(
                id = 1,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 5,
                price = 1700,
            )

        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(fiveClassPackage, fourClassPackage),
                initialDate = LocalDate(2026, 10, 9),
                onRegister = { _, selectedPackage, _, _ ->
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        // Then: name is still required
        onNodeWithText("Register")
            .assertIsNotEnabled()

        // When
        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        // Then: package selection requires no additional action
        onNodeWithText("Register")
            .assertIsEnabled()

        // When
        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            fourClassPackage,
            registeredPackage,
        )
    }

    @Test
    fun `registers student with package selected from dropdown`() = runComposeUiTest {
        // Given
        val fourClassPackage =
            PackageType(
                id = 1,
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
            )

        val fiveClassPackage =
            PackageType(
                id = 2,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 5,
                price = 1700,
            )

        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(fourClassPackage, fiveClassPackage),
                initialDate = LocalDate(2026, 10, 9),
                onRegister = { _, selectedPackage, _, _ ->
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        // Then: unselected options are initially hidden
        onNodeWithText("5-class package")
            .assertDoesNotExist()

        // When
        onNodeWithText("Package")
            .performClick()

        // Then
        onNodeWithText("5-class package")
            .assertIsDisplayed()

        // When
        onNodeWithText("5-class package")
            .performClick()

        // Then: the selected package is displayed and the menu is closed
        onNodeWithText("5-class package")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertDoesNotExist()

        // When
        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            fiveClassPackage,
            registeredPackage,
        )
    }

    @Test
    fun `selecting duo package defaults first attendance to two people`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(singlePackage, duoPackage),
                initialDate = today,
                onRegister = { _, _, _, _ -> },
                onDismiss = {},
            )
        }

        // Then: the individual package requires no visit count selection
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
        onNodeWithText("1 person")
            .assertIsDisplayed()

        onNodeWithText("2 people")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun `provides one visit when one person is selected for duo registration`() = runComposeUiTest {
        // Given
        var registeredVisitCount: Int? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(singlePackage, duoPackage),
                initialDate = today,
                onRegister = { _, _, _, visitCount ->
                    registeredVisitCount = visitCount
                },
                onDismiss = {},
            )
        }

        onNodeWithText("Package")
            .performClick()

        onNodeWithText("Duo 8 · Special rate")
            .performClick()

        // When
        onNodeWithText("1 person")
            .performClick()

        // Then
        onNodeWithText("1 person")
            .assertIsSelected()

        onNodeWithText("2 people")
            .assertIsNotSelected()

        // When
        onNode(hasSetTextAction())
            .performTextInput("Anna & John")

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            1,
            registeredVisitCount,
        )
    }

    @Test
    fun `provides two visits by default when duo student is registered`() = runComposeUiTest {
        // Given
        var registeredVisitCount: Int? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(singlePackage, duoPackage),
                initialDate = today,
                onRegister = { _, _, _, visitCount ->
                    registeredVisitCount = visitCount
                },
                onDismiss = {},
            )
        }

        // When
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
}
