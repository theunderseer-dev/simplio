package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class IosRegistrationPickerTest {
    @Test
    fun `date picker replaces the focused registration form and preserves the draft`() = runComposeUiTest {
        val group = StudentGroup(1, "Morning group")
        val schedule = testClassSchedule()
        val roster = AttendanceRoster(
            attendance = testStudentAttendanceUiState().attendance,
            groupSchedules = mapOf(group.id to listOf(schedule)),
            packageTypes = listOf(AttendanceTestData.fourClassPackage),
            studentGroups = listOf(group),
        )
        val creation = IosAttendanceCreation(
            addStudent = { RegisterStudentUiState.Success(it.name) },
            addPackage = { CreatePackageTypeUiState.Success(it.name) },
            addSchedule = { CreateStudentGroupUiState.Success(it.name) },
        )
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(roster),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
                creation = creation,
            )
        }
        val attendanceTitle = onNodeWithText("Attendance")
        val addActions = onNodeWithContentDescription("Add actions")
        val titleBounds = attendanceTitle.getUnclippedBoundsInRoot()
        val addBounds = addActions.getUnclippedBoundsInRoot()
        addActions.performTouchInput { click() }
        onNodeWithText("Add student").performTouchInput { click() }

        val name = onNodeWithTag("registration-full-name")
        name.performTouchInput { click(centerLeft.copy(x = width * 0.75f)) }
        name.assertIsFocused().performTextInput("Friday student")
        assertEquals(titleBounds, attendanceTitle.getUnclippedBoundsInRoot())
        assertEquals(addBounds, addActions.getUnclippedBoundsInRoot())
        val dialog = hasAnyAncestor(isDialog())
        onNode(dialog and hasText(group.name)).performScrollTo().performTouchInput { click() }

        val date = onNodeWithContentDescription("Registration date")
        date.performScrollTo().performTouchInput { click() }
        onNodeWithText("Select registration date").assertIsDisplayed()
        onAllNodes(isDialog()).assertCountEquals(1)
        name.assertDoesNotExist()
        onNodeWithText("Cancel").performTouchInput { click() }

        name.assertIsDisplayed().assertTextContains("Friday student")
        date.performScrollTo().performTouchInput { click() }
        onNodeWithText("OK").performTouchInput { click() }
        name.assertIsDisplayed().assertTextContains("Friday student")
        onNodeWithText("Oct 2").assertExists()
    }

    @Test
    fun `schedule pickers replace the form and keep its name`() = runComposeUiTest {
        setContent {
            StudentGroupCreationDialog(
                groups = emptyList(),
                schedules = emptyList(),
                today = AttendanceTestData.firstClassDate,
                state = CreateStudentGroupUiState.Idle,
                onCreate = {},
                onDismiss = {},
            )
        }
        val name = onNodeWithTag("group-name")
        name.performTouchInput { click() }
        name.assertIsFocused().performTextInput("Friday group")
        onNodeWithTag("group-start-date").performScrollTo().performTouchInput { click() }
        onNodeWithText("Schedule starts on").assertIsDisplayed()
        onAllNodes(isDialog()).assertCountEquals(1)
        onNodeWithText("Cancel").performTouchInput { click() }
        name.assertTextContains("Friday group")

        val startTime = hasAnyAncestor(hasTestTag("Starts at"))
        onNode(startTime and hasText("Pick")).performScrollTo().performTouchInput { click() }
        onNodeWithText("Start time").assertIsDisplayed()
        onAllNodes(isDialog()).assertCountEquals(1)
        onNodeWithText("Cancel").performTouchInput { click() }
        name.assertTextContains("Friday group")
    }
}
