package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import com.theunderseer.simplio.feature.attendance.fixtures.testClassSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class StudentRegistrationGroupScheduleUiTest {
    private val group = StudentGroup(3, "Private")
    private val callback = StudentRegistrationTestCallback()
    private val schedule = testClassSchedule(
        slots = listOf(
            WeeklyClassSlot("thursday", DayOfWeek.THURSDAY, LocalTime(18, 0), LocalTime(19, 0)),
        ),
    )

    @Test
    fun `selecting custom group replaces legacy date with group's latest class date`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = AttendanceTestData.currentDate,
                studentGroups = AttendanceTestData.studentGroups + group,
                groupSchedules = mapOf(group.id to listOf(schedule)),
                onRegister = callback::onRegister,
            )
        }
        onNodeWithTag("registration-full-name").performTextInput("Anna")
        onNodeWithText(group.name).performClick()
        onNodeWithText("Register").performClick()
        val submitted = assertNotNull(callback.submission)
        assertEquals(group.id, submitted.groupId)
        assertEquals(LocalDate(2026, 10, 8), submitted.registeredOn)
    }

    @Test
    fun `group without a schedule explains why registration has no available dates`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = AttendanceTestData.currentDate,
                studentGroups = listOf(group),
                groupSchedules = mapOf(group.id to emptyList()),
            )
        }
        onNodeWithTag("registration-full-name").performTextInput("Anna")
        onNodeWithText(group.name).performClick()
        onNodeWithText("No scheduled dates available yet").assertIsDisplayed()
        onNodeWithText("Register").assertIsNotEnabled()
    }

    @Test
    fun `new schedule registers on latest past weekday with first attendance`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = LocalDate(2026, 10, 9),
                studentGroups = listOf(group),
                groupSchedules = mapOf(group.id to listOf(schedule.copy(startsOn = LocalDate(2026, 10, 9)))),
                onRegister = callback::onRegister,
            )
        }
        onNodeWithTag("registration-full-name").performTextInput("Anna")
        onNodeWithText(group.name).performClick()
        onNodeWithText("Register").performClick()
        val submitted = assertNotNull(callback.submission)
        assertEquals(LocalDate(2026, 10, 8), submitted.registeredOn)
        assertEquals(true, submitted.recordFirstAttendance)
    }

    @Test
    fun `new schedule registers on latest past weekday without first attendance`() = runComposeUiTest {
        setContent {
            StudentRegistrationDialogForTest(
                initialDate = LocalDate(2026, 10, 9),
                studentGroups = listOf(group),
                groupSchedules = mapOf(group.id to listOf(schedule.copy(startsOn = LocalDate(2026, 10, 9)))),
                onRegister = callback::onRegister,
            )
        }
        onNodeWithTag("registration-full-name").performTextInput("Anna")
        onNodeWithText(group.name).performClick()
        onNodeWithText("Record first attendance").performClick()
        onNodeWithText("Register").performClick()
        val submitted = assertNotNull(callback.submission)
        assertEquals(LocalDate(2026, 10, 8), submitted.registeredOn)
        assertEquals(false, submitted.recordFirstAttendance)
    }
}
