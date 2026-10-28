package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.presentation.CreatePackageTypeUiState
import com.theunderseer.simplio.feature.attendance.presentation.PackageTypeCreationDialog
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PackageNumericRangeUiTest {
    @Test
    fun `price overflow explains the maximum and keeps that explanation on further typing`() = runComposeUiTest {
        showValidForm()
        val price = onNodeWithTag("create-package-price")
        price.performTextReplacement("999999999")
        onNodeWithTag("create-package-confirm").assertIsEnabled()
        price.performTextInput("9")
        price.assertTextContains("9999999999")
        onNodeWithText("Maximum allowed value: 2147483647.").assertIsDisplayed()
        onNodeWithText("Enter a whole price of zero or more.").assertDoesNotExist()
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
        price.performTextInput("9")
        price.assertTextContains("9999999999")
        onNodeWithText("Maximum allowed value: 2147483647.").assertIsDisplayed()
        onNodeWithText("Use up to 10 digits (0–9). Value was not changed.").assertDoesNotExist()
        price.performTextReplacement(Int.MAX_VALUE.toString())
        onNodeWithTag("create-package-confirm").assertIsEnabled()
    }

    @Test
    fun `validity overflow reports the week limit and accepts its exact boundary`() = runComposeUiTest {
        showValidForm()
        val weeks = onNodeWithTag("create-package-weeks")
        weeks.performTextReplacement((Int.MAX_VALUE / 7).toString())
        onNodeWithTag("create-package-confirm").assertIsEnabled()
        weeks.performTextReplacement((Int.MAX_VALUE / 7 + 1).toString())
        onNodeWithText("Maximum allowed value: 306783378.").assertIsDisplayed()
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
        weeks.performTextReplacement("9999999999")
        onNodeWithText("Maximum allowed value: 306783378.").assertIsDisplayed()
        weeks.performTextInput("9")
        onNodeWithText("Maximum allowed value: 306783378.").assertIsDisplayed()
        weeks.performTextReplacement("6")
        onNodeWithTag("create-package-confirm").assertIsEnabled()
    }

    private fun ComposeUiTest.showValidForm() {
        setContent { PackageTypeCreationDialog(emptyList(), CreatePackageTypeUiState.Idle, {}, {}, {}) }
        onNodeWithTag("create-package-name").performTextInput("Range test")
        onNodeWithTag("create-package-classes").performTextInput("4")
        onNodeWithTag("create-package-price").performTextInput("0")
        onNodeWithTag("create-package-weeks").performTextInput("6")
    }
}
