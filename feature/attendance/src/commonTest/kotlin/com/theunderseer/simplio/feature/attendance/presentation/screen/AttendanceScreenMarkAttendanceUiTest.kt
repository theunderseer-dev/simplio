package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.currentDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.secondClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.thirdClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.MarkStudentPresentUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenMarkAttendanceUiTest {
    @Test
    fun `keeps attendance picker open while saving and closes after success`() = runComposeUiTest {
        // Given
        val state = mutableStateOf(testStudentAttendanceUiState())

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
            listOf(Triple(student.id, secondClassDate, 1)),
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
    fun `opens attendance date picker when pending check is clicked`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState()

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
    fun `initially selects latest scheduled class date in attendance picker`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState()

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
        val state = testStudentAttendanceUiState(currentDate = thirdClassDate)

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
    fun `provides two visits when duo attendance is confirmed`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            packageType = duoSpecialPackage,
            visitsByDate = mapOf(firstClassDate to 2),
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
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

        // Then: opening the dialog does not dispatch attendance
        onNodeWithText("2 people")
            .assertIsSelected()

        assertEquals(emptyList(), markedAttendance)

        // When
        onNodeWithText("OK")
            .performClick()

        // Then
        assertEquals(
            listOf(Triple(student.id, secondClassDate, 2)),
            markedAttendance,
        )
    }

    @Test
    fun `provides one visit when duo package has only one visit remaining`() = runComposeUiTest {
        // Given
        val firstAttendance = LocalDate(2026, 9, 21)

        val state = testStudentAttendanceUiState(
            packageType = duoSpecialPackage,
            startedOn = firstAttendance,
            visitsByDate = mapOf(
                firstAttendance to 2,
                LocalDate(2026, 9, 25) to 2,
                LocalDate(2026, 9, 28) to 2,
                LocalDate(2026, 10, 2) to 1,
            ),
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
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

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
            listOf(Triple(student.id, secondClassDate, 1)),
            markedAttendance,
        )
    }

    @Test
    fun `shows mark error inside picker and preserves selection for retry`() = runComposeUiTest {
        // Given
        val errorMessage = "Failed to mark student present"
        val state = mutableStateOf(
            testStudentAttendanceUiState(
                packageType = duoSpecialPackage,
                visitsByDate = mapOf(firstClassDate to 2),
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
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

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
            listOf(Triple(student.id, secondClassDate, 1)),
            confirmedAttendance,
        )

        // When: the same selection is submitted again
        onNodeWithText("OK")
            .performClick()

        // Then: both the date and visit count are preserved
        assertEquals(
            listOf(
                Triple(student.id, secondClassDate, 1),
                Triple(student.id, secondClassDate, 1),
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
    fun `attendance picker disables recorded dates from student package`() = runComposeUiTest {
        // Given
        val state = testStudentAttendanceUiState(
            visitsByDate = mapOf(
                firstClassDate to 1,
                secondClassDate to 1,
            ),
            currentDate = thirdClassDate,
        )

        setContent {
            AttendanceScreenForTest(state = state)
        }

        // When
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

        // Then
        onNodeWithText(
            text = "Friday, October 2, 2026",
            substring = true,
        ).assertIsNotEnabled()

        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        ).assertIsNotEnabled()

        onNodeWithText(
            text = "Friday, October 9, 2026",
            substring = true,
        ).assertIsEnabled()
    }

    @Test
    fun `another student's attendance does not disable date in picker`() = runComposeUiTest {
        // Given
        val bob = testStudent(id = 8, fullName = "Bob Lee")
        val confirmedAttendance = mutableListOf<Triple<Int, LocalDate, Int>>()

        val annaEntry = testStudentAttendanceUiState(
            visitsByDate = mapOf(
                firstClassDate to 1,
                secondClassDate to 1,
            ),
        ).attendance.entries.single()

        val bobEntry = testStudentAttendanceUiState(student = bob).attendance.entries.single()

        val state = testAttendanceContentUiState(
            attendance = Attendance(entries = listOf(annaEntry, bobEntry)),
            packageTypes = listOf(fourClassPackage),
            currentDate = currentDate,
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
                onMarkStudentPresent = { id, date, visitCount ->
                    confirmedAttendance += Triple(id, date, visitCount)
                },
            )
        }

        // When
        onNodeWithContentDescription(
            "Mark ${bob.fullName} present",
        ).performClick()

        onNodeWithText(
            text = "Monday, October 5, 2026",
            substring = true,
        )
            .assertIsEnabled()
            .performClick()

        onNodeWithText("OK").performClick()

        // Then
        assertEquals(
            listOf(Triple(bob.id, secondClassDate, 1)),
            confirmedAttendance,
        )
    }

    @Test
    fun `attendance picker uses selected package registration date`() = runComposeUiTest {
        // Given
        val packageRegistrationDate = secondClassDate

        val state = testStudentAttendanceUiState(
            startedOn = packageRegistrationDate,
            currentDate = thirdClassDate,
        )

        setContent {
            AttendanceScreenForTest(state = state)
        }

        // When
        onNodeWithContentDescription(
            "Mark ${student.fullName} present",
        ).performClick()

        // Then
        onNodeWithText(
            text = "Friday, October 2, 2026",
            substring = true,
        ).assertIsNotEnabled()

        onNodeWithText(
            text = "Friday, October 9, 2026",
            substring = true,
        ).assertIsEnabled()
    }
}
