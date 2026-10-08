package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
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

    private val fourClassPackage =
        PackageType(
            id = 1,
            name = "4-class package",
            totalClasses = 4,
            validityWeeks = 4,
            price = 1400,
        )

    @Test
    fun `displays student registration form`() = runComposeUiTest {
        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 4,
                    price = 1400,
                ),
                PackageType(
                    id = 2,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 5,
                    price = 1700,
                ),
            )

        val registeredDate = LocalDate(2026, 10, 5)

        setContent {
            StudentRegistrationDialog(
                packageTypes = packageTypes,
                initialDate = registeredDate,
                onRegister = { _, _, _ -> },
                onDismiss = {},
            )
        }

        onNodeWithText("Add Student")
            .assertIsDisplayed()

        onNodeWithText("Full name")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()

        onNodeWithText("5-class package")
            .assertIsDisplayed()

        onNodeWithText("Register")
            .assertIsDisplayed()
    }

    @Test
    fun `displays initial registration date`() = runComposeUiTest {
        // Given
        val today =
            LocalDate(2026, 10, 5)

        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 4,
                    price = 1400,
                ),
            )

        // When
        setContent {
            StudentRegistrationDialog(
                packageTypes = packageTypes,
                initialDate = today,
                onRegister = { _, _, _ -> },
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
        val packageType =
            PackageType(
                id = 2,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 5,
                price = 1700,
            )

        val registeredDate = LocalDate(2026, 10, 5)

        var registeredName: String? = null
        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(packageType),
                initialDate = registeredDate,
                onRegister = { fullName, selectedPackage, _ ->
                    registeredName = fullName
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        // When
        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("5-class package")
            .performClick()

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            "Anna Smith",
            registeredName,
        )

        assertEquals(
            packageType,
            registeredPackage,
        )
    }

    @Test
    fun `registers student with selected registration date`() = runComposeUiTest {
        // Given
        val packageType =
            PackageType(
                id = 1,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 5,
                price = 1700,
            )

        var registeredOn: LocalDate? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(packageType),
                initialDate = LocalDate(2026, 10, 6),
                onRegister = { _, _, date ->
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

        onNodeWithText("5-class package")
            .performClick()

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
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _ -> },
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
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _ -> },
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
}
