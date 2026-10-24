package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
class StudentGroupScheduleOverlapUiTest {
    @Test
    fun `overlap blocks submit and changing times re-enables creation`() = runComposeUiTest {
        var submitted: CreateStudentGroupRequest? = null
        setContent {
            StudentGroupCreationDialog(
                AttendanceTestData.studentGroups,
                listOf(DefaultClassSchedule.schedule),
                AttendanceTestData.currentDate,
                CreateStudentGroupUiState.Idle,
                { submitted = it },
                {},
            )
        }
        onNodeWithTag("group-name").performTextInput("Private")
        onNodeWithText("New schedule").performClick()
        onNodeWithTag("group-time-zone").performScrollTo().performTextReplacement("Asia/Bangkok")
        waitUntil(timeoutMillis = 5000) {
            onAllNodesWithTag("schedule-overlap-error").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("schedule-overlap-error").performScrollTo().assertIsDisplayed()
        onNodeWithText("Create").assertIsNotEnabled()
        onNodeWithTag("Starts at").performScrollTo().performTextReplacement("11:00")
        onNodeWithTag("Ends at").performScrollTo().performTextReplacement("12:00")
        waitUntil(timeoutMillis = 5000) { runCatching { onNodeWithText("Create").assertIsEnabled() }.isSuccess }
        onNodeWithTag("schedule-overlap-error").assertDoesNotExist()
        onNodeWithText("Create").performClick()
        val request = assertNotNull(submitted)
        assertEquals("Private", request.name)
        assertEquals(11, assertNotNull(request.newSchedule).slots.first().startsAt.hour)
    }

    @Test
    fun `existing shared schedule remains selectable after new schedule collision`() = runComposeUiTest {
        var submitted: CreateStudentGroupRequest? = null
        setContent {
            StudentGroupCreationDialog(
                AttendanceTestData.studentGroups,
                listOf(DefaultClassSchedule.schedule),
                AttendanceTestData.currentDate,
                CreateStudentGroupUiState.Idle,
                { submitted = it },
                {},
            )
        }
        onNodeWithTag("group-name").performTextInput("Shared")
        onNodeWithText("New schedule").performClick()
        onNodeWithTag("group-time-zone").performScrollTo().performTextReplacement("Asia/Bangkok")
        waitUntil(timeoutMillis = 5000) {
            onAllNodesWithTag("schedule-overlap-error").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Use existing").performScrollTo().performClick()
        onNodeWithText("Create").assertIsEnabled().performClick()
        assertEquals(DefaultClassSchedule.ID, assertNotNull(submitted).existingScheduleId)
        assertEquals(null, assertNotNull(submitted).newSchedule)
    }
}
