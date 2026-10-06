package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.model.Student
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenTest {
    private val today = LocalDate(2026, 10, 5)

    private val fourClassPackage =
        PackageType(
            id = 1,
            name = "4-class package",
            totalClasses = 4,
            validityWeeks = 4,
            price = 1400,
        )

    private val fiveClassPackage =
        PackageType(
            id = 1,
            name = "5-class package",
            totalClasses = 5,
            validityWeeks = 5,
            price = 1700,
        )

    @Test
    fun `displays registered student attendance`() = runComposeUiTest {
        // Given
        val studentPackage =
            StudentPackage(
                id = 1,
                studentId = 1,
                packageType = fiveClassPackage,
                startedOn = LocalDate(2026, 10, 1),
                attendanceDates =
                setOf(
                    LocalDate(2026, 10, 1),
                    LocalDate(2026, 10, 3),
                    LocalDate(2026, 10, 5),
                ),
            )

        val state =
            AttendanceUiState.Content(
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
                packageTypes = listOf(fiveClassPackage),
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithText("Anna Smith")
            .assertIsDisplayed()

        onNodeWithText("3 / 5")
            .assertIsDisplayed()

        onNodeWithText("Oct 1")
            .assertIsDisplayed()

        onNodeWithText("Nov 5")
            .assertIsDisplayed()

        onNodeWithText("5-class package")
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
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithText("No students yet")
            .assertIsDisplayed()

        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `opens student registration dialog when add student is clicked`() = runComposeUiTest {
        val state = emptyAttendanceState(packageTypes = listOf(fourClassPackage))

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        onNodeWithText("Add Student")
            .performClick()

        onNodeWithText("Full name")
            .assertIsDisplayed()

        onNodeWithText("4-class package")
            .assertIsDisplayed()

        onNodeWithText("Register")
            .assertIsDisplayed()
    }

    @Test
    fun `displays add student action when attendance has students`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
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
                                packageType = fourClassPackage,
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fourClassPackage),
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithText("Add Student")
            .assertIsDisplayed()
    }

    @Test
    fun `opens date picker when registration date is clicked`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _ -> },
                onDismiss = {},
            )
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        // Then
        onNodeWithText("Select registration date")
            .assertIsDisplayed()
    }

    @Test
    fun `updates registration date after selecting past date`() = runComposeUiTest {
        // Given
        setContent {
            StudentRegistrationDialog(
                packageTypes = listOf(fourClassPackage),
                initialDate = today,
                onRegister = { _, _, _ -> },
                onDismiss = {},
            )
        }

        // When
        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText("Saturday, October 3, 2026")
            .performClick()

        onNodeWithText("OK")
            .performClick()

        onNodeWithText("Oct 3")
            .assertIsDisplayed()
    }

    @Test
    fun `provides selected registration date when student is registered`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState(packageTypes = listOf(fiveClassPackage))

        var registeredOn: LocalDate? = null

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = LocalDate(2026, 10, 6),
                onRegisterStudent = { _, _, date ->
                    registeredOn = date
                },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // When
        onNodeWithText("Add Student")
            .performClick()

        onNodeWithContentDescription("Registration date")
            .performClick()

        onNodeWithText("Saturday, October 3, 2026")
            .performClick()

        onNodeWithText("OK")
            .performClick()

        onNode(hasSetTextAction())
            .performTextInput("Anna Smith")

        onNodeWithText("5-class package")
            .performClick()

        onNodeWithText("Register")
            .performClick()

        // Then
        assertEquals(
            LocalDate(2026, 10, 3),
            registeredOn,
        )
    }

    @Test
    fun `shows delete action for registered student`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
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
                                packageType = fourClassPackage,
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fourClassPackage),
            )

        // When
        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithContentDescription("Delete Anna Smith")
            .assertIsDisplayed()
    }

    @Test
    fun `shows confirmation dialog before deleting student`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
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
                                packageType = fourClassPackage,
                                startedOn = LocalDate(2026, 10, 5),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 5),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fourClassPackage),
            )

        var deletedStudentId: Int? = null

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = { studentId ->
                    deletedStudentId = studentId
                },
                onCopyAttendance = {},
            )
        }

        // When
        onNodeWithContentDescription("Delete Anna Smith")
            .performClick()

        // Then
        onNodeWithText("Delete student?")
            .assertIsDisplayed()

        onNodeWithText("Delete \"Anna Smith\"?")
            .assertIsDisplayed()

        onNodeWithText("Cancel")
            .assertIsDisplayed()

        onNodeWithText("Delete")
            .assertIsDisplayed()

        assertEquals(
            null,
            deletedStudentId,
        )
    }

    @Test
    fun `displays copy attendance action`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState()

        // When
        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // Then
        onNodeWithContentDescription("Copy attendance")
            .assertIsDisplayed()
    }

    @Test
    fun `provides formatted attendance when copy action is clicked`() = runComposeUiTest {
        // Given
        val state =
            AttendanceUiState.Content(
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
                                packageType = fiveClassPackage,
                                startedOn = LocalDate(2026, 10, 1),
                                attendanceDates =
                                setOf(
                                    LocalDate(2026, 10, 1),
                                    LocalDate(2026, 10, 3),
                                ),
                            ),
                        ),
                    ),
                ),
                packageTypes = listOf(fiveClassPackage),
            )

        var copiedText: String? = null

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = today,
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = { text ->
                    copiedText = text
                },
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        // Then
        assertEquals(
            "\tAnna Smith\t2 / 5\t(until 05/11)",
            copiedText,
        )
    }

    @Test
    fun `shows copy confirmation after copy action`() = runComposeUiTest {
        // Given
        val state = emptyAttendanceState()

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = LocalDate(2026, 10, 6),
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
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
    fun `hides copy confirmation automatically`() = runComposeUiTest {
        // Given
        mainClock.autoAdvance = false

        val state = emptyAttendanceState()

        setContent {
            AttendanceScreen(
                state = state,
                currentDate = LocalDate(2026, 10, 6),
                onRegisterStudent = { _, _, _ -> },
                onDeleteStudent = {},
                onCopyAttendance = {},
            )
        }

        // When
        onNodeWithContentDescription("Copy attendance")
            .performClick()

        mainClock.advanceTimeByFrame()

        // Then
        onNodeWithText("Copied to clipboard")
            .assertIsDisplayed()

        mainClock.advanceTimeBy(5_000)
        waitForIdle()

        onNodeWithText("Copied to clipboard")
            .assertDoesNotExist()
    }

    private fun emptyAttendanceState(packageTypes: List<PackageType> = emptyList()): AttendanceUiState.Content =
        AttendanceUiState.Content(
            attendance = Attendance(entries = emptyList()),
            packageTypes = packageTypes,
        )
}
