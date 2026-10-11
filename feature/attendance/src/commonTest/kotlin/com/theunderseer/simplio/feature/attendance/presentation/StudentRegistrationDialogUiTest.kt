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
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroups
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val studentGroups = testStudentGroups()

    private val fourClassPackage = testClassPackage()

    private val fiveClassPackage = testClassPackage(
        id = 2,
        plan = PackagePlan.FIVE_CLASSES,
    )

    private val duoPackage = testClassPackage(
        id = 3,
        plan = PackagePlan.DUO_SPECIAL,
    )

    private val individualPackages = listOf(
        fourClassPackage,
        fiveClassPackage,
    )

    private val packagesWithDuo = listOf(
        fourClassPackage,
        duoPackage,
    )

    @Test
    fun `displays student registration form`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialog(
                studentGroups = studentGroups,
                packageTypes = individualPackages,
                initialDate = today,
                onRegister = { _, _, _, _, _ -> },
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
        // When
        setContent {
            StudentRegistrationDialog(
                studentGroups = studentGroups,
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _, _, _ -> },
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
        var registeredName: String? = null
        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                studentGroups = studentGroups,
                packageTypes = listOf(fiveClassPackage),
                initialDate = today,
                onRegister = { fullName, _, selectedPackage, _, _ ->
                    registeredName = fullName
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        onNodeWithText("Group A")
            .performClick()

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

        // Then
        assertEquals("Anna Smith", registeredName)
        assertEquals(fiveClassPackage, registeredPackage)
    }

    @Test
    fun `registers student with selected registration date`() = runComposeUiTest {
        // Given
        var registeredOn: LocalDate? = null

        setContent {
            StudentRegistrationDialog(
                studentGroups = studentGroups,
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _, date, _ ->
                    registeredOn = date
                },
                onDismiss = {},
            )
        }

        // When
        onNodeWithText("Group A")
            .performClick()

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
                studentGroups = studentGroups,
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _, _, _ -> },
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
                studentGroups = studentGroups,
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _, _, _ -> },
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
        val fourClassPackage = testClassPackage(id = 7)

        val fiveClassPackage = testClassPackage(
            id = 1,
            plan = PackagePlan.FIVE_CLASSES,
        )

        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                studentGroups = studentGroups,
                packageTypes = listOf(fiveClassPackage, fourClassPackage),
                initialDate = today,
                onRegister = { _, _, selectedPackage, _, _ ->
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        // Then: name is still required
        onNodeWithText("Group A")
            .performClick()

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
        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                studentGroups = studentGroups,
                packageTypes = individualPackages,
                initialDate = today,
                onRegister = { _, _, selectedPackage, _, _ ->
                    registeredPackage = selectedPackage
                },
                onDismiss = {},
            )
        }

        // Then: unselected options are initially hidden
        onNodeWithText("Group A")
            .performClick()

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
                studentGroups = studentGroups,
                packageTypes = packagesWithDuo,
                initialDate = today,
                onRegister = { _, _, _, _, _ -> },
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
                studentGroups = studentGroups,
                packageTypes = packagesWithDuo,
                initialDate = today,
                onRegister = { _, _, _, _, visitCount ->
                    registeredVisitCount = visitCount
                },
                onDismiss = {},
            )
        }

        onNodeWithText("Group A")
            .performClick()

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
                studentGroups = studentGroups,
                packageTypes = packagesWithDuo,
                initialDate = today,
                onRegister = { _, _, _, _, visitCount ->
                    registeredVisitCount = visitCount
                },
                onDismiss = {},
            )
        }

        // When
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
}
