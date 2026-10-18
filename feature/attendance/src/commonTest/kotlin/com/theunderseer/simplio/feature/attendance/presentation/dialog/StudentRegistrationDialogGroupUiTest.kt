package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationDialogGroupUiTest {
    @Test
    fun `registration provides selected group id`() = runComposeUiTest {
        // Given
        val registrationCallback = StudentRegistrationTestCallback()

        setContent {
            StudentRegistrationDialogForTest(
                onRegister = registrationCallback::onRegister,
            )
        }

        // When
        onNodeWithTag("registration-full-name")
            .performTextInput("Anna Smith")

        onNodeWithText("Group B")
            .performClick()

        onNodeWithText("Register")
            .performClick()

        // Then
        val submission = assertNotNull(registrationCallback.submission)

        assertEquals(
            DefaultStudentGroups.GROUP_B.id,
            submission.groupId,
        )
    }
}
