package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.registrationDate
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
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
                initialDate = registrationDate,
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
        assertEquals(DefaultStudentGroups.GROUP_B.id, registeredGroupId)
    }
}
