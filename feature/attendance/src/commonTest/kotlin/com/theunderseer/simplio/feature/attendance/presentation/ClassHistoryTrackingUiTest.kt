package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSessionStatus
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ClassHistoryTrackingUiTest {
    @Test
    fun `independent schedules sharing a visible interval keep separate actions and final states`() = runComposeUiTest {
        val session = ClassAttendanceTestData.session
        val first = testClassHistoryEntry(
            session.date,
            sessionId = session.id,
            timeLabel = ClassAttendanceTestData.timeLabel,
            startsAt = session.startsAt,
            endsAt = session.endsAt,
        )
            .copy(canTrackAttendance = true, scheduleName = "Shared groups")
        val second = first.copy(sessionId = "independent-series", scheduleName = "Private")
        val state = mutableStateOf(ClassHistoryUiState.Content(listOf(first, second), null))
        var selected: String? = null
        setContent { ClassHistorySection(state.value, AttendanceTestData.studentGroups, {}, {}, { selected = it }) }
        onNodeWithText("Shared groups · Track attendance").performClick()
        assertEquals(first.sessionId, selected)
        onNodeWithText("Private · Track attendance").performClick()
        assertEquals(second.sessionId, selected)
        runOnIdle {
            state.value = state.value.copy(
                entries = listOf(
                    first.copy(status = ClassSessionStatus.HELD, canTrackAttendance = false),
                    second.copy(status = ClassSessionStatus.CANCELLED, canTrackAttendance = false),
                ),
            )
        }
        onNodeWithTag("track-class-${first.key}").assertDoesNotExist()
        onNodeWithTag("track-class-${second.key}").assertDoesNotExist()
        onNodeWithText("Shared groups · Class held").assertExists()
        onNodeWithText("Private · Class cancelled").assertExists()
        onNodeWithText("Attendance not recorded").assertDoesNotExist()
    }
}
