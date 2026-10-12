package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenUiTest {
    private val today = LocalDate(2026, 10, 5)
    private val firstAttendance = LocalDate(2026, 10, 2)
    private val nextClassDate = LocalDate(2026, 10, 9)

    private val student = testStudent(id = 7)
    private val duoStudent = testStudent(
        id = 7,
        fullName = "Anna & John",
    )

    private val packageType = testClassPackage()
    private val duoPackage = testClassPackage(
        id = 3,
        plan = PackagePlan.DUO_SPECIAL,
    )

    private val displayedPackageStart = LocalDate(2026, 10, 1)
    private val displayedVisits = mapOf(
        displayedPackageStart to 1,
        LocalDate(2026, 10, 3) to 1,
        today to 1,
    )

    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            startedOn = displayedPackageStart,
            visitsByDate = displayedVisits,
        )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText("Anna Smith")
            .assertIsDisplayed()

        onNodeWithText("3 / 4")
            .assertIsDisplayed()

        onNodeWithText("Oct 1")
            .assertIsDisplayed()

        onNodeWithText("Oct 29")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()
    }

    @Test
    fun `displays empty attendance state`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState()

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText("No students yet")
            .assertIsDisplayed()

        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `shows copy confirmation after copy action`() = runComposeUiTest {
        // Given
        val state = attendanceState()

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithContentDescription(
            "Copy attendance for ${DefaultStudentGroups.GROUP_A.name}",
        ).performClick()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()
    }

    @Test
    fun `shows package history when classes count is clicked`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            startedOn = displayedPackageStart,
            visitsByDate = displayedVisits,
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithText("3 / 4")
            .performClick()

        // Then
        onNodeWithText("Package history")
            .assertIsDisplayed()

        onNode(
            hasTestTag("package-history-menu") and
                hasAnyDescendant(hasText("1. Oct 1")) and
                hasAnyDescendant(hasText("2. Oct 3")) and
                hasAnyDescendant(hasText("3. Oct 5")),
        ).assertIsDisplayed()
    }

    @Test
    fun `exposes attendance count as package attendance history action`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            startedOn = displayedPackageStart,
            visitsByDate = displayedVisits,
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithContentDescription(
            "View package history for Anna Smith",
        ).assertIsDisplayed()
    }

    @Test
    fun `keeps attendance picker open while saving and closes after success`() = runComposeUiTest {
        // Given
        val selectedDate = today

        val state = mutableStateOf(
            attendanceState(currentDate = nextClassDate),
        )

        val confirmedAttendance =
            mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreenForTest(
                state = state.value,
                onMarkStudentPresent = { studentId, date, visitCount ->
                    confirmedAttendance += Triple(
                        studentId,
                        date,
                        visitCount,
                    )
                    state.value = state.value.copy(
                        markStudentPresentState =
                        MarkStudentPresentUiState.Saving,
                    )
                },
                onClearMarkStudentPresentState = {
                    state.value = state.value.copy(
                        markStudentPresentState =
                        MarkStudentPresentUiState.Idle,
                    )
                },
            )
        }

        // When: a past class date is selected
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        )
            .performClick()

        // Then: selection alone does not submit attendance
        assertEquals(emptyList(), confirmedAttendance)

        // When: attendance is submitted
        onNodeWithText("OK")
            .performClick()

        // Then: the selected date and visit count are dispatched
        assertEquals(
            listOf(Triple(student.id, selectedDate, 1)),
            confirmedAttendance,
        )

        // And: the picker remains open while saving
        onNodeWithText("Select attendance date")
            .assertIsDisplayed()

        onNodeWithText("Saving...")
            .assertIsNotEnabled()

        onNodeWithText("Cancel")
            .assertIsNotEnabled()

        // When: saving succeeds
        runOnIdle {
            state.value = state.value.copy(
                markStudentPresentState =
                MarkStudentPresentUiState.Success,
            )
        }

        // Then: the picker closes
        onNodeWithText("Select attendance date")
            .assertDoesNotExist()

        // And: the handled result is cleared
        runOnIdle {
            assertEquals(
                MarkStudentPresentUiState.Idle,
                state.value.markStudentPresentState,
            )
        }
    }

    @Test
    fun `shows checked state when latest scheduled attendance is recorded`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            visitsByDate = mapOf(firstAttendance to 1, today to 1),
            currentDate = LocalDate(2026, 10, 6),
        )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText("✓")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Checked",
                ),
            )
    }

    @Test
    fun `opens attendance date picker when pending check is clicked`() = runComposeUiTest {
        // Given
        val state = attendanceState(currentDate = LocalDate(2026, 10, 6))

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText("Select attendance date")
            .assertIsDisplayed()
    }

    @Test
    fun `requests attendance removal with student id and date`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            visitsByDate = mapOf(firstAttendance to 1, today to 1),
            currentDate = LocalDate(2026, 10, 6),
        )

        val removedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = state,
                onRemoveStudentAttendance = { studentId, date ->
                    removedAttendance += studentId to date
                },
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        // Opening the dialog does not remove attendance
        assertEquals(
            emptyList(),
            removedAttendance,
        )

        // When
        onNodeWithText("Remove")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        assertEquals(
            listOf(student.id to today),
            removedAttendance,
        )
    }

    @Test
    fun `initially selects latest scheduled class date in attendance picker`() = runComposeUiTest {
        // Given
        val state = attendanceState(currentDate = LocalDate(2026, 10, 8))

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        )
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun `allows only scheduled attendance dates up to current date`() = runComposeUiTest {
        // Given
        val state = attendanceState(currentDate = nextClassDate)

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then: past Monday and today's Friday are available
        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        ).assertIsEnabled()

        onNodeWithText(
            text = "Friday, October 9, 2026",
            substring = true,
        ).assertIsEnabled()

        onNodeWithText(
            text = "Thursday, October 8, 2026",
            substring = true,
        ).assertIsNotEnabled()

        onNodeWithText(
            text = "Monday, October 12, 2026",
            substring = true,
        ).assertIsNotEnabled()
    }

    @Test
    fun `opens attendance removal confirmation when checked attendance is clicked`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            visitsByDate = mapOf(firstAttendance to 1, today to 1),
            currentDate = LocalDate(2026, 10, 8),
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna Smith present")
            .performClick()

        // Then
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        onNodeWithText(
            "Remove attendance for Anna Smith on Oct 5?",
        ).assertIsDisplayed()

        onNodeWithText("Remove")
            .assertIsDisplayed()

        onNodeWithText("Cancel")
            .assertIsDisplayed()

        onNodeWithText("Select attendance date")
            .assertDoesNotExist()
    }

    @Test
    fun `provides two visits when duo attendance is confirmed`() = runComposeUiTest {
        // Given
        val attendanceDate = today

        val state = attendanceState(
            student = duoStudent,
            packageType = duoPackage,
            visitsByDate = mapOf(firstAttendance to 2),
            currentDate = attendanceDate,
        )

        val markedAttendance = mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreenForTest(
                state = state,
                onMarkStudentPresent = { id, date, visitCount ->
                    markedAttendance += Triple(id, date, visitCount)
                },
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna & John present")
            .performClick()

        // Then: opening the dialog does not dispatch attendance
        onNodeWithText("2 people")
            .assertIsSelected()

        assertEquals(emptyList(), markedAttendance)

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(Triple(duoStudent.id, attendanceDate, 2)),
            markedAttendance,
        )
    }

    @Test
    fun `provides one visit when duo package has only one visit remaining`() = runComposeUiTest {
        // Given
        val firstAttendance = LocalDate(2026, 9, 21)
        val attendanceDate = today

        val state = attendanceState(
            student = duoStudent,
            packageType = duoPackage,
            startedOn = firstAttendance,
            visitsByDate = mapOf(
                firstAttendance to 2,
                LocalDate(2026, 9, 25) to 2,
                LocalDate(2026, 9, 28) to 2,
                LocalDate(2026, 10, 2) to 1,
            ),
            currentDate = attendanceDate,
        )

        val markedAttendance = mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreenForTest(
                state = state,
                onMarkStudentPresent = { id, date, visitCount ->
                    markedAttendance += Triple(id, date, visitCount)
                },
            )
        }

        // When
        onNodeWithContentDescription("Mark Anna & John present")
            .performClick()

        // Then
        onNodeWithText("1 person")
            .assertIsSelected()

        onNodeWithText("2 people")
            .assertIsNotEnabled()

        onNodeWithText("Only 1 visit remaining.")
            .assertIsDisplayed()

        assertEquals(emptyList(), markedAttendance)

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(Triple(duoStudent.id, attendanceDate, 1)),
            markedAttendance,
        )
    }

    @Test
    fun `displays attendance error message`() = runComposeUiTest {
        // Given
        val message = "Not enough visits remaining"

        val state =
            AttendanceUiState.Error(
                message = message,
            )

        // When
        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // Then
        onNodeWithText(message)
            .assertIsDisplayed()
    }

    @Test
    fun `shows mark error inside picker and preserves selection for retry`() = runComposeUiTest {
        // Given
        val errorMessage = "Failed to mark student present"
        val state = mutableStateOf(
            attendanceState(
                student = duoStudent,
                packageType = duoPackage,
                visitsByDate = mapOf(firstAttendance to 2),
                currentDate = nextClassDate,
            ),
        )

        val confirmedAttendance =
            mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreenForTest(
                state = state.value,
                onMarkStudentPresent = { studentId, date, visitCount ->
                    confirmedAttendance += Triple(
                        studentId,
                        date,
                        visitCount,
                    )
                    state.value = state.value.copy(
                        markStudentPresentState =
                        MarkStudentPresentUiState.Saving,
                    )
                },
                onClearMarkStudentPresentState = {
                    state.value = state.value.copy(
                        markStudentPresentState =
                        MarkStudentPresentUiState.Idle,
                    )
                },
            )
        }

        // When: a date and one person are selected
        onNodeWithContentDescription("Mark Anna & John present")
            .performClick()

        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        )
            .performClick()

        onNodeWithText("1 person")
            .performClick()

        onNodeWithText("OK")
            .performClick()

        // And: saving fails
        runOnIdle {
            state.value = state.value.copy(
                markStudentPresentState =
                MarkStudentPresentUiState.Error(
                    message = errorMessage,
                ),
            )
        }

        // Then: the picker remains open with an inline error
        onNodeWithText("Select attendance date")
            .assertIsDisplayed()

        onNode(
            hasText(errorMessage) and hasAnyAncestor(isDialog()),
        )
            .assertIsDisplayed()

        onNodeWithText("1 person")
            .assertIsSelected()

        onNodeWithText("OK")
            .assertIsEnabled()

        assertEquals(
            listOf(Triple(duoStudent.id, today, 1)),
            confirmedAttendance,
        )

        // When: the same selection is submitted again
        onNodeWithText("OK")
            .performClick()

        // Then: both the date and visit count are preserved
        assertEquals(
            listOf(
                Triple(duoStudent.id, today, 1),
                Triple(duoStudent.id, today, 1),
            ),
            confirmedAttendance,
        )

        // And: the previous error disappears while retrying
        onNodeWithText(errorMessage)
            .assertDoesNotExist()

        onNodeWithText("Saving...")
            .assertIsNotEnabled()

        onNodeWithText("Select attendance date")
            .assertIsDisplayed()
    }

    @Test
    fun `displays students in their corresponding group sections`() = runComposeUiTest {
        // Given
        val groupAStudent = student.copy(
            groupId = DefaultStudentGroups.GROUP_A_ID,
        )
        val groupBStudent = testStudent(
            id = 8,
            fullName = "Bob Lee",
            groupId = DefaultStudentGroups.GROUP_B_ID,
        )

        // Group B deliberately appears first in the source attendance.
        val entries = listOf(groupBStudent, groupAStudent).map { student ->
            attendanceState(student = student).attendance.entries.single()
        }

        val state = testAttendanceContentUiState(
            attendance = Attendance(entries = entries),
            packageTypes = listOf(packageType),
            currentDate = today,
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNode(
            hasText(DefaultStudentGroups.GROUP_A.name) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupAStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        onNode(
            hasText(groupAStudent.fullName) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupAStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        onNode(
            hasText(DefaultStudentGroups.GROUP_B.name) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupBStudent.groupId}"),
                ),
        ).assertIsDisplayed()

        onNode(
            hasText(groupBStudent.fullName) and
                hasAnyAncestor(
                    hasTestTag("student-group-${groupBStudent.groupId}"),
                ),
        ).assertIsDisplayed()
    }

    @Test
    fun `hides group sections without students`() = runComposeUiTest {
        // Given
        val state = attendanceState(
            student = student.copy(
                groupId = DefaultStudentGroups.GROUP_A_ID,
            ),
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithText(DefaultStudentGroups.GROUP_A.name)
            .assertIsDisplayed()

        onNodeWithText(DefaultStudentGroups.GROUP_B.name)
            .assertDoesNotExist()
    }

    private fun emptyAttendanceState(
        packageTypes: List<PackageType> = emptyList(),
        currentDate: LocalDate = today,
    ): AttendanceUiState.Content = testAttendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = packageTypes,
        currentDate = currentDate,
    )

    private fun attendanceState(
        student: Student = this.student,
        packageType: PackageType = this.packageType,
        startedOn: LocalDate = firstAttendance,
        visitsByDate: Map<LocalDate, Int> = mapOf(startedOn to 1),
        currentDate: LocalDate = today,
    ): AttendanceUiState.Content = testAttendanceContentUiState(
        attendance = Attendance(
            entries = listOf(
                RegisteredStudent(
                    student = student,
                    studentPackage = StudentPackage(
                        id = 1,
                        studentId = student.id,
                        packageType = packageType,
                        startedOn = startedOn,
                        attendanceHistory = PackageAttendanceHistory(
                            visitsByDate = visitsByDate,
                        ),
                    ),
                ),
            ),
        ),
        packageTypes = listOf(packageType),
        currentDate = currentDate,
    )
}
