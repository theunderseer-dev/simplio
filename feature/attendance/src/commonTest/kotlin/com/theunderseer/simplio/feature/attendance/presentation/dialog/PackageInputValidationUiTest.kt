package com.theunderseer.simplio.feature.attendance.presentation.dialog

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
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.CreatePackageTypeUiState
import com.theunderseer.simplio.feature.attendance.presentation.PackageTypeCreationDialog
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PackageInputValidationUiTest {
    @Test
    fun `package numeric fields reject letters and signs while free price and positive finite weeks remain valid`() =
        runComposeUiTest {
            val type = AttendanceTestData.privateEightClassPackage
            setContent { PackageTypeCreationDialog(emptyList(), CreatePackageTypeUiState.Idle, {}, {}, {}) }
            onNodeWithTag("create-package-name").performTextInput(type.name)
            onNodeWithTag("create-package-classes").performTextInput(type.totalClasses.toString())
            onNodeWithTag("create-package-price").performTextInput("0")
            onNodeWithTag("create-package-weeks").performTextInput("6")
            onNodeWithTag("create-package-confirm").assertIsEnabled()
            onNodeWithTag("create-package-classes").performTextReplacement("8 classes")
            onNodeWithTag("create-package-classes").assertTextContains(type.totalClasses.toString())
            onNodeWithText("Use up to 10 digits (0–9). Value was not changed.").assertIsDisplayed()
            onNodeWithTag("create-package-confirm").assertIsNotEnabled()
            onNodeWithTag("create-package-classes").performTextReplacement("0")
            onNodeWithTag("create-package-confirm").assertIsNotEnabled()
            onNodeWithTag("create-package-classes").performTextReplacement(type.totalClasses.toString())
            onNodeWithTag("create-package-weeks").performTextReplacement("0")
            onNodeWithTag("create-package-confirm").assertIsNotEnabled()
            onNodeWithTag("create-package-weeks").performTextReplacement("6")
            onNodeWithTag("create-package-price").performTextReplacement("-1")
            onNodeWithTag("create-package-price").assertTextContains("0")
            onNodeWithTag("create-package-confirm").assertIsNotEnabled()
            onNodeWithTag("create-package-price").performTextReplacement("0")
            onNodeWithTag("create-package-confirm").assertIsEnabled()
        }
}
