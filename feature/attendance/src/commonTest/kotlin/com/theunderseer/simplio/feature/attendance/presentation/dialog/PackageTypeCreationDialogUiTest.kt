package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testCreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.presentation.CreatePackageTypeUiState
import com.theunderseer.simplio.feature.attendance.presentation.PackageTypeCreationDialog
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PackageTypeCreationDialogUiTest {
    @Test
    fun `no expiration creates private template and hides week field`() = runComposeUiTest {
        var submitted: CreatePackageTypeRequest? = null
        setContent {
            PackageTypeCreationDialog(emptyList(), CreatePackageTypeUiState.Idle, { submitted = it }, {}, {})
        }
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
        enterPackageFields()
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
        onNodeWithTag("create-package-no-expiration").performClick()
        onNodeWithTag("create-package-weeks").assertDoesNotExist()
        onNodeWithTag("create-package-confirm").assertIsEnabled().performClick()
        assertEquals(testCreatePackageTypeRequest(), submitted)
    }

    @Test
    fun `finite custom duo submits independent capacity validity and visit limit`() = runComposeUiTest {
        var submitted: CreatePackageTypeRequest? = null
        setContent {
            PackageTypeCreationDialog(emptyList(), CreatePackageTypeUiState.Idle, { submitted = it }, {}, {})
        }
        enterPackageFields()
        onNodeWithTag("create-package-weeks").performTextInput("6")
        onNodeWithTag("create-package-allow-two").performClick()
        onNodeWithTag("create-package-confirm").assertIsEnabled().performClick()
        assertEquals(testCreatePackageTypeRequest().copy(validityWeeks = 6, maxVisitsPerAttendance = 2), submitted)
    }

    @Test
    fun `duplicate name and invalid numeric values disable creation`() = runComposeUiTest {
        setContent {
            PackageTypeCreationDialog(
                listOf(AttendanceTestData.privateEightClassPackage),
                CreatePackageTypeUiState.Idle,
                {},
                {},
                {},
            )
        }
        enterPackageFields()
        onNodeWithTag("create-package-no-expiration").performClick()
        onNodeWithText("A package with this name already exists.").assertIsDisplayed()
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
        onNodeWithTag("create-package-name").performTextReplacement(AttendanceTestData.privateTwelveClassPackage.name)
        onNodeWithTag("create-package-price").performTextReplacement("99999999999999999999")
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
        onNodeWithTag("create-package-price").performTextReplacement("0")
        onNodeWithTag("create-package-confirm").assertIsEnabled()
        onNodeWithTag("create-package-classes").performTextReplacement("1")
        onNodeWithTag("create-package-allow-two").performClick()
        onNodeWithTag("create-package-confirm").assertIsNotEnabled()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.enterPackageFields() {
    val type = AttendanceTestData.privateEightClassPackage
    onNodeWithTag("create-package-name").performTextInput(type.name)
    onNodeWithTag("create-package-classes").performTextInput(type.totalClasses.toString())
    onNodeWithTag("create-package-price").performTextInput(type.price.toString())
}
