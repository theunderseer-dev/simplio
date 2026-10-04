package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogTest {
    @Test
    fun `displays student registration form`() = runComposeUiTest {
        val packageTypes =
            listOf(
                PackageType(
                    id = 1,
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                ),
                PackageType(
                    id = 2,
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                ),
            )

        setContent {
            StudentRegistrationDialog(
                packageTypes = packageTypes,
                onRegister = { _, _ -> },
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
    fun `registers student with entered name and selected package`() = runComposeUiTest {
        // Given
        val packageType =
            PackageType(
                id = 2,
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 6,
                price = 1700,
            )

        var registeredName: String? = null
        var registeredPackage: PackageType? = null

        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(packageType),
                onRegister = { fullName, selectedPackage ->
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
}
