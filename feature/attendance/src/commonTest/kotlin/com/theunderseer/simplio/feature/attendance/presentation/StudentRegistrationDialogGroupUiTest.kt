package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentGroups
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogGroupUiTest {
    @Test
    fun `registration provides selected group id`() = runComposeUiTest {
        // Given
        var registeredGroupId: Int? = null

        setContent {
            StudentRegistrationDialog(
                studentGroups = testStudentGroups(),
                packageTypes = listOf(testClassPackage()),
                initialDate = LocalDate(2026, 10, 9),
                onRegister = { _, groupId, _, _, _ ->
                    registeredGroupId = groupId
                },
                onDismiss = {},
            )
        }

        // When
        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("Group B")
            .performClick()

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(2, registeredGroupId)
    }
}
