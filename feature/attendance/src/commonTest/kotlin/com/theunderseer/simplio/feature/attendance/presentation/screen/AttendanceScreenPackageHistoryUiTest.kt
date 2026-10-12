package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.duoSpecialPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.fourClassPackage
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextAttendanceDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.nextClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.student
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.RemoveStudentAttendanceUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenPackageHistoryUiTest {
    @Test
    fun `shows removal actions only for attendance after registration date`() = runComposeUiTest {
        // Given
        val state = packageHistoryState()

        setContent {
            AttendanceScreenForTest(
                state = state,
            )
        }

        // When
        onNodeWithText("3 / 4")
            .performClick()

        // Then: later attendance can be removed
        onNodeWithContentDescription(
            "Remove attendance on Oct 5 for Anna Smith",
        )
            .assertIsDisplayed()
            .assertHasClickAction()

        onNodeWithContentDescription(
            "Remove attendance on Oct 9 for Anna Smith",
        )
            .assertIsDisplayed()
            .assertHasClickAction()

        // And: registration attendance has no removal button
        onNodeWithContentDescription(
            "Remove attendance on Oct 2 for Anna Smith",
        )
            .assertDoesNotExist()

        onNodeWithText("Package history")
            .assertIsDisplayed()
    }

    @Test
    fun `confirms removal of selected attendance from package history`() = runComposeUiTest {
        // Given
        val removalRequests = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = packageHistoryState(),
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date
                },
            )
        }

        // When
        onNodeWithText("3 / 4")
            .performClick()

        onNodeWithContentDescription(
            "Remove attendance on Oct 5 for Anna Smith",
        ).performClick()

        // Then: confirmation refers to the selected date
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        onNodeWithText(
            "Remove attendance for Anna Smith on Oct 5?",
        ).assertIsDisplayed()

        assertEquals(
            emptyList(),
            removalRequests,
        )

        // When
        onNodeWithText("Remove")
            .performClick()

        // Then: the middle date is passed instead of the latest date
        assertEquals(
            listOf(student.id to nextAttendanceDate),
            removalRequests,
        )
    }

    @Test
    fun `cancelling package history attendance removal does not request deletion`() = runComposeUiTest {
        // Given
        val removalRequests = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = packageHistoryState(),
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date
                },
            )
        }

        onNodeWithText("3 / 4")
            .performClick()

        onNodeWithContentDescription(
            "Remove attendance on Oct 5 for Anna Smith",
        ).performClick()

        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        // When
        onNodeWithText("Cancel")
            .performClick()

        // Then
        assertEquals(
            emptyList(),
            removalRequests,
        )

        onNodeWithText("Remove attendance?")
            .assertDoesNotExist()
    }

    @Test
    fun `keeps package history open and renumbers attendance after removal`() = runComposeUiTest {
        // Given
        val screenState = mutableStateOf(packageHistoryState())

        setContent {
            AttendanceScreenForTest(
                state = screenState.value,
                onRemoveStudentAttendance = { studentId, date ->
                    val currentState = screenState.value

                    val updatedEntries =
                        currentState.attendance.entries.map { entry ->
                            if (entry.student.id == studentId) {
                                val studentPackage = entry.studentPackage

                                entry.copy(
                                    studentPackage = studentPackage.copy(
                                        attendanceHistory = PackageAttendanceHistory(
                                            visitsByDate =
                                            studentPackage.attendanceHistory.visitsByDate - date,
                                        ),
                                    ),
                                )
                            } else {
                                entry
                            }
                        }

                    screenState.value = testAttendanceContentUiState(
                        attendance = currentState.attendance.copy(
                            entries = updatedEntries,
                        ),
                        packageTypes = currentState.packageTypes,
                        currentDate = currentState.currentDate,
                        studentGroups = currentState.studentGroups,
                    ).copy(
                        removeStudentAttendanceState = RemoveStudentAttendanceUiState.Success,
                    )
                },
                onClearRemoveStudentAttendanceState = {
                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState = RemoveStudentAttendanceUiState.Idle,
                    )
                },
            )
        }

        onNodeWithText("3 / 4")
            .performClick()

        onNodeWithText("2. Oct 5")
            .assertIsDisplayed()

        onNodeWithText("3. Oct 9")
            .assertIsDisplayed()

        // When
        onNodeWithContentDescription(
            "Remove attendance on Oct 5 for Anna Smith",
        ).performClick()

        onNodeWithText("Remove")
            .performClick()

        // Then: the table count is updated
        onNodeWithText("2 / 4")
            .assertIsDisplayed()

        // And: history remains open with consecutive numbering
        onNodeWithText("Package history")
            .assertIsDisplayed()

        onNodeWithText("1. Oct 2")
            .assertIsDisplayed()

        onNodeWithText("2. Oct 9")
            .assertIsDisplayed()

        onNodeWithText("2. Oct 5")
            .assertDoesNotExist()

        onNodeWithText("3. Oct 9")
            .assertDoesNotExist()

        onNodeWithText("Remove attendance?")
            .assertDoesNotExist()
    }

    @Test
    fun `keeps removal confirmation open after failure and allows retry`() = runComposeUiTest {
        // Given
        val initialState = packageHistoryState()
        val screenState = mutableStateOf(initialState)
        val removalRequests = mutableListOf<Pair<Int, LocalDate>>()
        val errorMessage = "Failed to remove attendance"

        setContent {
            AttendanceScreenForTest(
                state = screenState.value,
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date

                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState =
                        RemoveStudentAttendanceUiState.Error(
                            message = errorMessage,
                        ),
                    )
                },
                onClearRemoveStudentAttendanceState = {
                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState = RemoveStudentAttendanceUiState.Idle,
                    )
                },
            )
        }

        onNodeWithText("3 / 4")
            .performClick()

        onNodeWithContentDescription(
            "Remove attendance on Oct 5 for Anna Smith",
        ).performClick()

        // When: removal fails
        onNodeWithText("Remove")
            .performClick()

        // Then: confirmation remains open with the error
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        onNodeWithText(errorMessage)
            .assertIsDisplayed()

        onNodeWithText("Remove")
            .assertIsEnabled()

        // And: table content and attendance remain unchanged
        onNodeWithText("3 / 4")
            .assertIsDisplayed()

        assertEquals(
            initialState.attendance,
            screenState.value.attendance,
        )

        assertEquals(
            listOf(student.id to nextAttendanceDate),
            removalRequests,
        )

        // When: removal is retried
        onNodeWithText("Remove")
            .performClick()

        // Then: the same attendance is requested again
        assertEquals(
            listOf(
                student.id to nextAttendanceDate,
                student.id to nextAttendanceDate,
            ),
            removalRequests,
        )

        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()
    }

    @Test
    fun `disables removal actions while saving and closes confirmation after success`() = runComposeUiTest {
        // Given
        val screenState = mutableStateOf(packageHistoryState())
        val removalRequests = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreenForTest(
                state = screenState.value,
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date

                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState =
                        RemoveStudentAttendanceUiState.Saving,
                    )
                },
                onClearRemoveStudentAttendanceState = {
                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState =
                        RemoveStudentAttendanceUiState.Idle,
                    )
                },
            )
        }

        onNodeWithText("3 / 4")
            .performClick()

        onNodeWithContentDescription(
            "Remove attendance on Oct 5 for Anna Smith",
        ).performClick()

        // When: removal starts
        onNodeWithText("Remove")
            .performClick()

        // Then: confirmation remains open with disabled actions
        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        onNodeWithText("Removing...")
            .assertIsDisplayed()
            .assertIsNotEnabled()

        onNodeWithText("Cancel")
            .assertIsNotEnabled()

        // When: disabled actions are clicked
        onNodeWithText("Removing...")
            .performClick()

        onNodeWithText("Cancel")
            .performClick()

        // Then: no additional request is dispatched; confirmation remains open
        assertEquals(
            listOf(student.id to nextAttendanceDate),
            removalRequests,
        )

        onNodeWithText("Remove attendance?")
            .assertIsDisplayed()

        // When: successful completion arrives
        runOnIdle {
            screenState.value = screenState.value.copy(
                removeStudentAttendanceState =
                RemoveStudentAttendanceUiState.Success,
            )
        }

        // Then: confirmation closes; package history remains open
        onNodeWithText("Remove attendance?")
            .assertDoesNotExist()

        onNodeWithText("Package history")
            .assertIsDisplayed()

        runOnIdle {
            assertEquals(
                RemoveStudentAttendanceUiState.Idle,
                screenState.value.removeStudentAttendanceState,
            )
        }
    }

    @Test
    fun `shows recorded visit counts for each duo attendance date`() = runComposeUiTest {
        // Given
        val state = packageHistoryState(
            packageType = duoSpecialPackage,
            visitsByDate = mapOf(
                firstAttendanceDate to 2,
                nextAttendanceDate to 1,
                nextClassDate to 2,
            ),
        )

        setContent {
            AttendanceScreenForTest(state = state)
        }

        // When
        onNodeWithContentDescription(
            "View package history for ${student.fullName}",
        ).performClick()

        // Then
        onNodeWithContentDescription(
            "Visits on Oct 2 for ${student.fullName}",
        ).assertTextEquals("2").assertIsDisplayed()

        onNodeWithContentDescription(
            "Visits on Oct 5 for ${student.fullName}",
        ).assertTextEquals("1").assertIsDisplayed()

        onNodeWithContentDescription(
            "Visits on Oct 9 for ${student.fullName}",
        ).assertTextEquals("2").assertIsDisplayed()

        // Registration attendance still cannot be removed.
        onNodeWithContentDescription(
            "Remove attendance on Oct 2 for ${student.fullName}",
        ).assertDoesNotExist()
    }

    @Test
    fun `hides visit counts for individual package attendance`() = runComposeUiTest {
        // Given
        val state = packageHistoryState()

        setContent {
            AttendanceScreenForTest(state = state)
        }

        // When
        onNodeWithContentDescription(
            "View package history for ${student.fullName}",
        ).performClick()

        // Then
        listOf("Oct 2", "Oct 5", "Oct 9").forEach { date ->
            onNodeWithContentDescription(
                "Visits on $date for ${student.fullName}",
            ).assertDoesNotExist()
        }
    }

    @Test
    fun `shows package history when classes count is clicked`() = runComposeUiTest {
        // Given
        val state = packageHistoryState()

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
                hasAnyDescendant(hasText("1. Oct 2")) and
                hasAnyDescendant(hasText("2. Oct 5")) and
                hasAnyDescendant(hasText("3. Oct 9")),
        ).assertIsDisplayed()
    }

    @Test
    fun `exposes attendance count as package attendance history action`() = runComposeUiTest {
        // Given
        val state = packageHistoryState()

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

    private fun packageHistoryState(
        packageType: PackageType = fourClassPackage,
        visitsByDate: Map<LocalDate, Int> = mapOf(
            firstAttendanceDate to 1,
            nextAttendanceDate to 1,
            nextClassDate to 1,
        ),
    ): AttendanceUiState.Content = testStudentAttendanceUiState(
        packageType = packageType,
        visitsByDate = visitsByDate,
        currentDate = nextClassDate,
    )
}
