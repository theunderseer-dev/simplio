package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.TEST_GROUP_B_ID
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
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
            StudentRegistrationDialogForTest(
                packageTypes = listOf(testClassPackage()),
                initialDate = LocalDate(2026, 10, 9),
                onRegister = { _, groupId, _, _, _ ->
                    registeredGroupId = groupId
                },
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
        assertEquals(TEST_GROUP_B_ID, registeredGroupId)
    }
}
