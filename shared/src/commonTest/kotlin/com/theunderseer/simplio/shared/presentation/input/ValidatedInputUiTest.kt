package com.theunderseer.simplio.shared.presentation.input

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import com.theunderseer.simplio.shared.fixtures.InputTestData
import com.theunderseer.simplio.shared.fixtures.ValidatedInputFormForTest
import com.theunderseer.simplio.shared.validation.InputPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ValidatedInputUiTest {
    @Test
    fun `invalid replacement keeps the original value explains rejection and blocks saving until corrected`() =
        runComposeUiTest {
            var saved: String? = null
            setContent { ValidatedInputFormForTest(onSave = { saved = it }) }
            onNodeWithTag("number").performTextReplacement("-12")
            onNodeWithTag("number").assertTextContains(InputTestData.number)
            onNodeWithText("Use up to 10 digits (0–9). Value was not changed.").assertIsDisplayed()
            onNodeWithText("Save").assertIsNotEnabled()
            onNodeWithTag("number").performTextReplacement("12")
            onNodeWithText("Use up to 10 digits (0–9). Value was not changed.").assertDoesNotExist()
            onNodeWithText("Save").assertIsEnabled().performClick()
            assertEquals("12", saved)
        }

    @Test
    fun `rejected middle insertion preserves selection so the next replacement edits the intended digit`() =
        runComposeUiTest {
            setContent { ValidatedInputFormForTest(initialText = InputTestData.editableNumber) }
            onNodeWithTag("number").performTextInputSelection(TextRange(1, 2))
            onNodeWithTag("number").performTextInput("a")
            onNodeWithTag("number").assertTextContains(InputTestData.editableNumber)
            onNodeWithTag("number").performTextInput("9")
            onNodeWithTag("number").assertTextContains("193")
            onNodeWithText("Save").assertIsEnabled()
        }

    @Test
    fun `deletion zero and integer overflow remain editable and are not silently clamped`() = runComposeUiTest {
        setContent { ValidatedInputFormForTest() }
        onNodeWithTag("number").performTextClearance()
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithTag("number").performTextInput("0")
        onNodeWithTag("number").assertTextContains("0")
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithTag("number").performTextReplacement("2147483648")
        onNodeWithTag("number").assertTextContains("2147483648")
        onNodeWithText("Save").assertIsNotEnabled()
        onNodeWithTag("number").performTextReplacement(Int.MAX_VALUE.toString())
        onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `pasted line break is rejected rather than silently changing a single line value`() = runComposeUiTest {
        setContent {
            ValidatedInputFormForTest(
                initialText = InputTestData.editableNumber,
                policy = InputPolicy.SingleLine(80),
            )
        }
        onNodeWithTag("number").performTextReplacement("unsafe\nname")
        onNodeWithTag("number").assertTextContains(InputTestData.editableNumber)
        onNodeWithText("Use up to 80 characters on one line. Value was not changed.").assertIsDisplayed()
        onNodeWithText("Save").assertIsNotEnabled()
    }
}
