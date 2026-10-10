package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageAttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenPackageHistoryUiTest {
    @Test
    fun `shows removal actions only for attendance after registration date`() = runComposeUiTest {
        // Given
        val firstAttendance = LocalDate(2026, 10, 2)
        val secondAttendance = LocalDate(2026, 10, 5)
        val thirdAttendance = LocalDate(2026, 10, 9)
        val packageType = testFourClassPackage()

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = 7,
                                fullName = "Anna Smith",
                            ),
                            studentPackage =
                            StudentPackage(
                                id = 11,
                                studentId = 7,
                                packageType = packageType,
                                startedOn = firstAttendance,
                                attendanceHistory =
                                PackageAttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        firstAttendance to 1,
                                        secondAttendance to 1,
                                        thirdAttendance to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = thirdAttendance,
            )

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},
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
            AttendanceScreen(
                state = packageHistoryState(),
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date
                },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},
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
            listOf(7 to LocalDate(2026, 10, 5)),
            removalRequests,
        )
    }

    @Test
    fun `cancelling package history attendance removal does not request deletion`() = runComposeUiTest {
        // Given
        val removalRequests = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreen(
                state = packageHistoryState(),
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date
                },
                onClearMarkStudentPresentState = {},
                onClearRemoveStudentAttendanceState = {},
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
            AttendanceScreen(
                state = screenState.value,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
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

                    screenState.value = attendanceContentUiState(
                        attendance = currentState.attendance.copy(
                            entries = updatedEntries,
                        ),
                        packageTypes = currentState.packageTypes,
                        currentDate = currentState.currentDate,
                    ).copy(
                        removeStudentAttendanceState = RemoveStudentAttendanceUiState.Success,
                    )
                },
                onClearMarkStudentPresentState = {},
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
            AttendanceScreen(
                state = screenState.value,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date

                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState =
                        RemoveStudentAttendanceUiState.Error(
                            message = errorMessage,
                        ),
                    )
                },
                onClearMarkStudentPresentState = {},
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
            listOf(7 to LocalDate(2026, 10, 5)),
            removalRequests,
        )

        // When: removal is retried
        onNodeWithText("Remove")
            .performClick()

        // Then: the same attendance is requested again
        assertEquals(
            listOf(
                7 to LocalDate(2026, 10, 5),
                7 to LocalDate(2026, 10, 5),
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
            AttendanceScreen(
                state = screenState.value,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { studentId, date ->
                    removalRequests += studentId to date

                    screenState.value = screenState.value.copy(
                        removeStudentAttendanceState =
                        RemoveStudentAttendanceUiState.Saving,
                    )
                },
                onClearMarkStudentPresentState = {},
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
            listOf(7 to LocalDate(2026, 10, 5)),
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

    private fun packageHistoryState(): AttendanceUiState.Content {
        val firstAttendance = LocalDate(2026, 10, 2)
        val secondAttendance = LocalDate(2026, 10, 5)
        val latestAttendance = LocalDate(2026, 10, 9)
        val packageType = testFourClassPackage()

        return attendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = Student(
                            id = 7,
                            fullName = "Anna Smith",
                        ),
                        studentPackage = StudentPackage(
                            id = 11,
                            studentId = 7,
                            packageType = packageType,
                            startedOn = firstAttendance,
                            attendanceHistory = PackageAttendanceHistory(
                                visitsByDate = mapOf(
                                    firstAttendance to 1,
                                    secondAttendance to 1,
                                    latestAttendance to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = latestAttendance,
        )
    }
}
