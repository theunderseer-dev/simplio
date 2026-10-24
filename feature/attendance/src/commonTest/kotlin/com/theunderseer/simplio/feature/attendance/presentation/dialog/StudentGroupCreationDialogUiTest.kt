package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.CreateStudentGroupUiState
import com.theunderseer.simplio.feature.attendance.presentation.StudentGroupCreationDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class StudentGroupCreationDialogUiTest {
    @Test
    fun `duplicate name disables create and explains the error`() = runComposeUiTest {
        setContent {
            StudentGroupCreationDialog(
                AttendanceTestData.studentGroups,
                listOf(DefaultClassSchedule.schedule),
                AttendanceTestData.currentDate,
                CreateStudentGroupUiState.Idle,
                {},
                {},
            )
        }
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithTag("group-name").performTextInput(" group   a ")
        onNodeWithText("A group with this name already exists").assertIsDisplayed()
        onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `create uses selected existing schedule without creating a duplicate series`() = runComposeUiTest {
        var request: CreateStudentGroupRequest? = null
        setContent {
            StudentGroupCreationDialog(
                AttendanceTestData.studentGroups,
                listOf(DefaultClassSchedule.schedule),
                AttendanceTestData.currentDate,
                CreateStudentGroupUiState.Idle,
                { request = it },
                {},
            )
        }
        onNodeWithTag("group-name").performTextInput("Private")
        onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals(DefaultClassSchedule.ID, assertNotNull(request).existingScheduleId)
        assertEquals(null, assertNotNull(request).newSchedule)
    }

    @Test
    fun `saving disables submit and dismiss`() = runComposeUiTest {
        setContent {
            StudentGroupCreationDialog(
                AttendanceTestData.studentGroups,
                listOf(DefaultClassSchedule.schedule),
                AttendanceTestData.currentDate,
                CreateStudentGroupUiState.Saving,
                {},
                {},
            )
        }
        onNodeWithText("Saving...").assertIsNotEnabled()
        onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun `new schedule offers weekday and venue controls`() = runComposeUiTest {
        setContent {
            StudentGroupCreationDialog(
                AttendanceTestData.studentGroups,
                listOf(DefaultClassSchedule.schedule),
                AttendanceTestData.currentDate,
                CreateStudentGroupUiState.Idle,
                {},
                {},
            )
        }
        onNodeWithTag("group-name").performTextInput("Private")
        onNodeWithText("New schedule").performClick()
        onNodeWithTag("group-day-MONDAY").performClick()
        onNodeWithTag("group-day-FRIDAY").performClick()
        onNodeWithTag("group-day-TUESDAY").assertIsDisplayed().performClick()
        onNodeWithTag("group-time-zone").performScrollTo().assertIsDisplayed()
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
        onNodeWithText("Create").assertIsEnabled()
    }
}
