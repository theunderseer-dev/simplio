package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.screen.AttendanceScreenForTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PackageTypeManagementUiTest {
    @Test
    fun `manage packages from the menu requires confirmation before hiding a template`() = runComposeUiTest {
        val old = AttendanceTestData.fiveClassPackage.copy(validityWeeks = 6)
        setContent {
            var catalog by remember { mutableStateOf(listOf(old, AttendanceTestData.fourClassPackage)) }
            var deletion by remember { mutableStateOf(DeletePackageTypeUiState.Idle) }
            AttendanceScreenForTest(
                state = testStudentAttendanceUiState().copy(packageTypes = catalog, deletePackageTypeState = deletion),
                onDeletePackageType = { id ->
                    catalog = catalog.filterNot { it.id == id }
                    deletion = DeletePackageTypeUiState.Deleted
                },
                onClearDeletePackageTypeState = { deletion = DeletePackageTypeUiState.Idle },
            )
        }

        onNodeWithContentDescription("Schedule options").performClick()
        onNodeWithText("Manage Packages").performClick()
        onNodeWithText("5 classes · 6 weeks").assertIsDisplayed()
        onNodeWithContentDescription("Delete ${old.name}").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithText(old.name).assertIsDisplayed()
        onNodeWithContentDescription("Delete ${old.name}").performClick()
        onNodeWithText("Delete package").performClick()
        onNodeWithText(old.name).assertDoesNotExist()
        onNodeWithText(AttendanceTestData.fourClassPackage.name).assertIsDisplayed()
    }
}
