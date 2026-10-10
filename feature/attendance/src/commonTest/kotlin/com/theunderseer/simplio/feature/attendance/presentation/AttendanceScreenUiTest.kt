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
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceHistory
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenUiTest {
    private val today = LocalDate(2026, 10, 5)

    private val packageType = testFourClassPackage()
    private val duoPackage =
        PackageType(
            id = 3,
            name = "Duo 8 · Special rate",
            totalClasses = 8,
            validityWeeks = 4,
            price = 2400,
            maxVisitsPerAttendance = 2,
        )

    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 1,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 1),
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        LocalDate(2026, 10, 1) to 1,
                        LocalDate(2026, 10, 3) to 1,
                        LocalDate(2026, 10, 5) to 1,
                    ),
                ),
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = 1,
                                fullName = "Anna Smith",
                            ),
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
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
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
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
        val state =
            emptyAttendanceState(
                currentDate = LocalDate(2026, 10, 6),
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
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()
    }

    @Test
    fun `shows attendance dates when classes count is clicked`() = runComposeUiTest {
        // Given
        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = 1,
                                fullName = "Anna Smith",
                            ),
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = 1,
                                packageType = packageType,
                                startedOn = LocalDate(2026, 10, 1),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 1) to 1,
                                        LocalDate(2026, 10, 3) to 1,
                                        LocalDate(2026, 10, 5) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
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
            )
        }

        // When
        onNodeWithText("3 / 4")
            .performClick()

        // Then
        onNodeWithText("Attendance dates")
            .assertIsDisplayed()

        onNode(
            hasTestTag("attendance-dates-menu") and
                hasAnyDescendant(hasText("1. Oct 1")) and
                hasAnyDescendant(hasText("2. Oct 3")) and
                hasAnyDescendant(hasText("3. Oct 5")),
        ).assertIsDisplayed()
    }

    @Test
    fun `exposes attendance count as attendance history action`() = runComposeUiTest {
        // Given
        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = 1,
                                fullName = "Anna Smith",
                            ),
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = 1,
                                packageType = packageType,
                                startedOn = LocalDate(2026, 10, 1),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 1) to 1,
                                        LocalDate(2026, 10, 3) to 1,
                                        LocalDate(2026, 10, 5) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = today,
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
            )
        }

        // Then
        onNodeWithContentDescription(
            "View attendance dates for Anna Smith",
        ).assertIsDisplayed()
    }

    @Test
    fun `keeps attendance picker open while saving and closes after success`() = runComposeUiTest {
        // Given
        val student = Student(
            id = 7,
            fullName = "Anna Smith",
        )
        val firstAttendance = LocalDate(2026, 10, 2)
        val selectedDate = LocalDate(2026, 10, 5)

        val studentPackage = StudentPackage(
            id = 1,
            studentId = student.id,
            packageType = packageType,
            startedOn = firstAttendance,
            attendanceHistory = AttendanceHistory(
                visitsByDate = mapOf(firstAttendance to 1),
            ),
        )

        val state = mutableStateOf(
            attendanceContentUiState(
                attendance = Attendance(
                    entries = listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 9),
            ),
        )

        val confirmedAttendance =
            mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreen(
                state = state.value,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
                onRemoveStudentAttendance = { _, _ -> },
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
        val student = Student(
            id = 7,
            fullName = "Anna Smith",
        )

        val state = attendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = StudentPackage(
                            id = 1,
                            studentId = student.id,
                            packageType = packageType,
                            startedOn = LocalDate(2026, 10, 2),
                            attendanceHistory =
                            AttendanceHistory(
                                visitsByDate =
                                mapOf(
                                    LocalDate(2026, 10, 2) to 1,
                                    LocalDate(2026, 10, 5) to 1,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = LocalDate(2026, 10, 6),
        )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
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
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = student.id,
                                packageType = packageType,
                                startedOn = LocalDate(2026, 10, 2),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 2) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 6),
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
    fun `confirms attendance removal with student id and date and closes dialog`() = runComposeUiTest {
        // Given
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = student.id,
                                packageType = packageType,
                                startedOn = LocalDate(2026, 10, 2),
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        LocalDate(2026, 10, 2) to 1,
                                        LocalDate(2026, 10, 5) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 6),
            )

        val removedAttendance = mutableListOf<Pair<Int, LocalDate>>()

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { studentId, date ->
                    removedAttendance += studentId to date
                },
                onClearMarkStudentPresentState = {},
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
        assertEquals(
            listOf(student.id to LocalDate(2026, 10, 5)),
            removedAttendance,
        )

        onNodeWithText("Remove attendance?")
            .assertDoesNotExist()
    }

    @Test
    fun `initially selects latest scheduled class date in attendance picker`() = runComposeUiTest {
        // Given
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = student.id,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 2),
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        LocalDate(2026, 10, 2) to 1,
                    ),
                ),
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 8),
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
        val student =
            Student(
                id = 7,
                fullName = "Anna Smith",
            )

        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = student.id,
                packageType = packageType,
                startedOn = LocalDate(2026, 10, 2),
                attendanceHistory =
                AttendanceHistory(
                    visitsByDate =
                    mapOf(
                        LocalDate(2026, 10, 2) to 1,
                    ),
                ),
            )

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(packageType),
                currentDate = LocalDate(2026, 10, 9),
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
        val student = Student(
            id = 7,
            fullName = "Anna Smith",
        )
        val studentPackage = StudentPackage(
            id = 1,
            studentId = student.id,
            packageType = packageType,
            startedOn = LocalDate(2026, 10, 2),
            attendanceHistory =
            AttendanceHistory(
                visitsByDate =
                mapOf(
                    LocalDate(2026, 10, 2) to 1,
                    LocalDate(2026, 10, 5) to 1,
                ),
            ),
        )
        val state = attendanceContentUiState(
            attendance = Attendance(
                entries = listOf(
                    RegisteredStudent(
                        student = student,
                        studentPackage = studentPackage,
                    ),
                ),
            ),
            packageTypes = listOf(packageType),
            currentDate = LocalDate(2026, 10, 8),
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
        val firstAttendance = LocalDate(2026, 10, 2)
        val attendanceDate = LocalDate(2026, 10, 5)
        val studentId = 7

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = studentId,
                                fullName = "Anna & John",
                            ),
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = studentId,
                                packageType = duoPackage,
                                startedOn = firstAttendance,
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(firstAttendance to 2),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(duoPackage),
                currentDate = attendanceDate,
            )

        val markedAttendance = mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { id, date, visitCount ->
                    markedAttendance += Triple(id, date, visitCount)
                },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
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
            listOf(Triple(studentId, attendanceDate, 2)),
            markedAttendance,
        )
    }

    @Test
    fun `provides one visit when duo package has only one visit remaining`() = runComposeUiTest {
        // Given
        val firstAttendance = LocalDate(2026, 9, 21)
        val attendanceDate = LocalDate(2026, 10, 5)
        val studentId = 7

        val state =
            attendanceContentUiState(
                attendance =
                Attendance(
                    entries =
                    listOf(
                        RegisteredStudent(
                            student =
                            Student(
                                id = studentId,
                                fullName = "Anna & John",
                            ),
                            studentPackage =
                            StudentPackage(
                                id = 1,
                                studentId = studentId,
                                packageType = duoPackage,
                                startedOn = firstAttendance,
                                attendanceHistory =
                                AttendanceHistory(
                                    visitsByDate =
                                    mapOf(
                                        firstAttendance to 2,
                                        LocalDate(2026, 9, 25) to 2,
                                        LocalDate(2026, 9, 28) to 2,
                                        LocalDate(2026, 10, 2) to 1,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(duoPackage),
                currentDate = attendanceDate,
            )

        val markedAttendance = mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { id, date, visitCount ->
                    markedAttendance += Triple(id, date, visitCount)
                },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
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
            listOf(Triple(studentId, attendanceDate, 1)),
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
            AttendanceScreen(
                state = state,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
                onMarkStudentPresent = { _, _, _ -> },
                onRemoveStudentAttendance = { _, _ -> },
                onClearMarkStudentPresentState = {},
            )
        }

        // Then
        onNodeWithText(message)
            .assertIsDisplayed()
    }

    @Test
    fun `shows mark error inside picker and preserves selection for retry`() = runComposeUiTest {
        // Given
        val student = Student(
            id = 7,
            fullName = "Anna & John",
        )
        val firstAttendance = LocalDate(2026, 10, 2)
        val selectedDate = LocalDate(2026, 10, 5)
        val errorMessage = "Failed to mark student present"

        val studentPackage = StudentPackage(
            id = 1,
            studentId = student.id,
            packageType = duoPackage,
            startedOn = firstAttendance,
            attendanceHistory = AttendanceHistory(
                visitsByDate = mapOf(firstAttendance to 2),
            ),
        )

        val state = mutableStateOf(
            attendanceContentUiState(
                attendance = Attendance(
                    entries = listOf(
                        RegisteredStudent(
                            student = student,
                            studentPackage = studentPackage,
                        ),
                    ),
                ),
                packageTypes = listOf(duoPackage),
                currentDate = LocalDate(2026, 10, 9),
            ),
        )

        val confirmedAttendance =
            mutableListOf<Triple<Int, LocalDate, Int>>()

        setContent {
            AttendanceScreen(
                state = state.value,
                onRegisterStudent = { _, _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
                onRemoveStudentAttendance = { _, _ -> },
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
            listOf(Triple(student.id, selectedDate, 1)),
            confirmedAttendance,
        )

        // When: the same selection is submitted again
        onNodeWithText("OK")
            .performClick()

        // Then: both the date and visit count are preserved
        assertEquals(
            listOf(
                Triple(student.id, selectedDate, 1),
                Triple(student.id, selectedDate, 1),
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

    private fun emptyAttendanceState(
        packageTypes: List<PackageType> = emptyList(),
        currentDate: LocalDate = today,
    ): AttendanceUiState.Content = attendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = packageTypes,
        currentDate = currentDate,
    )
}
