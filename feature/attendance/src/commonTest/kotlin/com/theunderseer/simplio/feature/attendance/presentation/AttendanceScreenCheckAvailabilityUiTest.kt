package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenCheckAvailabilityUiTest {
    @Test
    fun `first attendance check is disabled and cannot open an attendance dialog`() = runComposeUiTest {
        // Given
        val firstAttendance = LocalDate(2026, 10, 2)
        val student = Student(id = 7, fullName = "Anna Smith")
        val packageType = testFourClassPackage()
        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries = listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = student.id,
                                packageType = packageType,
                                startedOn = firstAttendance,
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = firstAttendance,
            )
        val markedAttendance = mutableListOf<Pair<Int, LocalDate>>()
        val removedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { studentId, date ->
                    markedAttendance += studentId to date
                },
                onRemoveStudentAttendance = { studentId, date ->
                    removedAttendance += studentId to date
                },
            )
        }

        // Then: the first attendance still appears checked
        val check = onNodeWithContentDescription("Mark Anna Smith present")

        check.assertIsNotEnabled()
        check.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Checked",
            ),
        )
        onNodeWithText("1 / 4").assertIsDisplayed()

        // When: a click is attempted
        check.performClick()

        // Then: neither attendance action is opened or dispatched
        onNodeWithText("Remove attendance?").assertDoesNotExist()
        onNodeWithText("Select attendance date").assertDoesNotExist()
        assertEquals(emptyList(), markedAttendance)
        assertEquals(emptyList(), removedAttendance)
    }
}
