package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testCompletedPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenResponsiveLayoutUiTest {
    @Test
    fun `columns resize with the window and stay aligned across groups and attendance actions`() = runComposeUiTest {
        val fraction = mutableStateOf(0.6f)
        val active = testStudent(fullName = "Anna Smith with a very long student name")
        val completed = testStudent(id = 2, fullName = "John Doe", groupId = DefaultStudentGroups.GROUP_B.id)
        val date = AttendanceTestData.sixthClassDate
        val state = testAttendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(active, testStudentPackage(studentId = active.id)),
                    RegisteredStudent(completed, testCompletedPackage(completed, id = 2)),
                ),
            ),
            packageTypes = AttendanceTestData.availablePackages,
            currentDate = date,
        ).copy(
            classHistoryState = ClassHistoryUiState.Content(
                entries = listOf(testClassHistoryEntry(date)),
                nextBeforeDate = null,
            ),
        )
        setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth(fraction.value)) {
                    AttendanceScreenForTest(state)
                }
            }
        }

        fun cell(studentId: Int, text: String) = onNode(
            hasText(text) and hasAnyAncestor(hasTestTag("student-row-$studentId")),
        )

        val nameWidths = mutableListOf<Float>()
        val packageWidths = mutableListOf<Float>()
        for (width in listOf(0.6f, 1f, 0.45f)) {
            runOnIdle { fraction.value = width }
            val firstCells = listOf(
                cell(active.id, active.fullName),
                cell(active.id, "1 / 4"),
                cell(active.id, "Oct 2"),
                cell(active.id, "Oct 30"),
                cell(active.id, AttendanceTestData.fourClassPackage.name),
                onNodeWithContentDescription("Mark ${active.fullName} present"),
                onNodeWithContentDescription("Delete ${active.fullName}"),
            )
            val secondCells = listOf(
                cell(completed.id, completed.fullName),
                cell(completed.id, "4 / 4"),
                cell(completed.id, "Oct 2"),
                cell(completed.id, "Oct 30"),
                cell(completed.id, AttendanceTestData.fourClassPackage.name),
                onNodeWithContentDescription("Renew package for ${completed.fullName}"),
                onNodeWithContentDescription("Delete ${completed.fullName}"),
            )
            val firstBounds = firstCells.map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
            val secondBounds = secondCells.map { it.assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
            val rowBounds = onNodeWithTag("student-row-${active.id}").fetchSemanticsNode().boundsInRoot
            val historyBounds = onNodeWithTag("class-history-entry-${date.toEpochDays()}")
                .fetchSemanticsNode().boundsInRoot

            assertTrue(abs(firstBounds.first().left - rowBounds.left) <= 1f)
            assertTrue(abs(firstBounds.last().right - rowBounds.right) <= 1f)
            assertTrue(abs(rowBounds.left - historyBounds.left) <= 1f)
            assertTrue(abs(rowBounds.right - historyBounds.right) <= 1f)
            for ((first, second) in firstBounds.zip(secondBounds)) {
                assertTrue(abs(first.left - second.left) <= 1f, "Columns must align across check and renew rows")
            }
            for (bounds in listOf(firstBounds, secondBounds)) {
                for ((left, right) in bounds.zipWithNext()) {
                    assertTrue(left.right <= right.left + 1f, "Columns must not overlap when the window shrinks")
                }
            }
            nameWidths += firstBounds.first().width
            packageWidths += firstBounds[4].width
        }

        for (widths in listOf(nameWidths, packageWidths)) {
            assertTrue(widths[1] > widths[0], "Columns must grow with the window")
            assertTrue(widths[2] < widths[0], "Columns must shrink with the window")
        }
    }
}
