package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class AttendanceRosterScreenTest {
    @Test
    fun `roster shows expiration below the name and note below the visits and offers renewal`() = runComposeUiTest {
        val student = AttendanceTestData.student.copy(
            fullName = AttendanceTestData.studentWithCompanionName,
            note = AttendanceTestData.studentNote,
        )
        val packageType = AttendanceTestData.duoSpecialPackage
        val attendance = testStudentAttendanceUiState(student = student, packageType = packageType).attendance
        var edit: AttendanceRosterEdit? = null
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap(), listOf(packageType))),
                now = Instant.parse("2026-11-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = {
                    edit = it
                    null
                },
            )
        }

        val name = onNodeWithText(student.fullName, useUnmergedTree = true)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val expiration = onNodeWithText("Expires Oct 30", useUnmergedTree = true)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val visits = onNodeWithContentDescription("1 of 8 visits used", useUnmergedTree = true)
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val note = onNodeWithText(student.note, useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(name.left, expiration.left)
        assertTrue(name.bottom < expiration.top)
        assertTrue(visits.left > name.right)
        val visitCenter = (visits.top + visits.bottom) / 2f
        assertTrue(visitCenter in ((name.top + name.bottom) / 2f)..((expiration.top + expiration.bottom) / 2f))
        assertTrue(note.top > maxOf(expiration.bottom, visits.bottom))
        val renew = onNodeWithContentDescription("Renew package for ${student.fullName}")
        renew.assertIsEnabled().assertHeightIsAtLeast(44.dp)
        onNodeWithText("Oct 2").assertDoesNotExist()
        onNodeWithText(packageType.name).assertDoesNotExist()
        renew.performClick()
        val dialog = hasAnyAncestor(isDialog())
        onNode(dialog and hasText("Purchase date")).assertExists()
        onNode(dialog and hasText("Record first attendance")).performScrollTo().performClick()
        onNodeWithTag("registration-note").performScrollTo().performTextReplacement("Renewal note")
        onNode(dialog and hasText("Renew")).performClick()
        runOnIdle {
            val renewal = assertIs<AttendanceRosterEdit.Renew>(edit)
            assertEquals(LocalDate(2026, 11, 2), renewal.date)
            assertEquals(packageType, renewal.template)
            assertEquals("Renewal note", renewal.note)
            assertFalse(renewal.recordFirstAttendance)
        }
    }

    @Test
    fun `empty roster is visible after loading`() = runComposeUiTest {
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
            )
        }
        onNodeWithText("No students yet").assertIsDisplayed()
    }

    @Test
    fun `roster preserves group and student order and omits empty groups`() = runComposeUiTest {
        val firstGroup = StudentGroup(1, "Tuesday class")
        val emptyGroup = StudentGroup(2, "Empty class")
        val secondGroup = StudentGroup(3, "Monday class")
        val student = AttendanceTestData.student
        val students = listOf(
            student.copy(id = 2, fullName = "Ben", groupId = secondGroup.id),
            student.copy(id = 3, fullName = "Zoe", groupId = firstGroup.id),
            student.copy(id = 4, fullName = "Ana", groupId = firstGroup.id),
        )
        val roster = AttendanceRoster(
            attendance = Attendance(students.flatMap { testStudentAttendanceUiState(student = it).attendance.entries }),
            groupSchedules = emptyMap(),
            studentGroups = listOf(firstGroup, emptyGroup, secondGroup),
        )
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(roster),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
            )
        }

        val labels = listOf(firstGroup.name, "Zoe", "Ana", secondGroup.name, "Ben")
        val positions = labels.map {
            onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot().top
        }
        assertTrue(positions.zipWithNext().all { (above, below) -> above < below })
        val separator = onNodeWithTag("ios-group-divider-${firstGroup.id}").getUnclippedBoundsInRoot()
        assertTrue(separator.top > positions[0] && separator.bottom < positions[1])
        onNodeWithText(emptyGroup.name).assertDoesNotExist()
    }

    @Test
    fun `attendance options expose sign out in the overflow menu`() = runComposeUiTest {
        var signOuts = 0
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = { signOuts++ },
                onEdit = { null },
            )
        }

        onNodeWithText("Refresh").assertDoesNotExist()
        onNodeWithText("Sign out").assertDoesNotExist()
        onNodeWithContentDescription("More options").assertHeightIsAtLeast(44.dp).performClick()
        onNodeWithText("Sign out").assertIsDisplayed().performClick()
        onNodeWithText("Sign out").assertDoesNotExist()
        runOnIdle { assertEquals(1, signOuts) }
    }

    @Test
    fun `pulling on an empty roster refreshes once and shows progress below the title`() = runComposeUiTest {
        var refreshes = 0
        var refreshing by mutableStateOf(false)
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {
                    refreshes++
                    refreshing = true
                },
                onSignOut = {},
                onEdit = { null },
                isRefreshing = refreshing,
            )
        }

        val list = onNodeWithTag("ios-attendance-list")
        list.performTouchInput { swipeDown() }
        val progress = onNodeWithContentDescription("Refreshing attendance")
        val progressTop = progress.assertIsDisplayed().getUnclippedBoundsInRoot().top
        assertTrue(progressTop > onNodeWithText("Attendance").getUnclippedBoundsInRoot().bottom)
        assertTrue(progressTop < onNodeWithText("No students yet").getUnclippedBoundsInRoot().top)
        list.performTouchInput { swipeDown() }
        runOnIdle {
            assertEquals(1, refreshes)
            refreshing = false
        }
        progress.assertDoesNotExist()
    }

    @Test
    fun `checked and exhausted packages offer different actions`() = runComposeUiTest {
        val checkedStudent = AttendanceTestData.student
        val exhaustedStudent = checkedStudent.copy(id = 2, fullName = "Exhausted student")
        val checked = testStudentAttendanceUiState(
            student = checkedStudent,
            startedOn = AttendanceTestData.fourthClassDate,
        ).attendance
        val exhausted = testStudentAttendanceUiState(
            student = exhaustedStudent,
            visitsByDate = AttendanceTestData.packageClassDates.take(4).associateWith { 1 },
        ).attendance
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(
                    AttendanceRoster(Attendance(checked.entries + exhausted.entries), emptyMap()),
                ),
                now = Instant.parse("2026-10-12T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
            )
        }

        onNodeWithContentDescription("Renew package for ${exhaustedStudent.fullName}").assertIsEnabled()
        onNodeWithContentDescription("Remove attendance for ${checkedStudent.fullName}").performClick()
        onNode(hasAnyAncestor(isDialog()) and hasText("Remove attendance?")).assertIsDisplayed()
    }

    @Test
    fun `add actions stack above the plus button and close on tap outside or selection`() = runComposeUiTest {
        var darkTheme by mutableStateOf(false)
        setContent {
            SimplioTheme(darkTheme = darkTheme) {
                Surface {
                    AttendanceRosterScreen(
                        state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                        now = Instant.parse("2026-10-02T10:00:00Z"),
                        onRefresh = {},
                        onSignOut = {},
                        onEdit = { null },
                        creation = creationForTest(),
                    )
                }
            }
        }
        val expand = onNodeWithContentDescription("Add actions")
        val collapse = onNodeWithContentDescription("Close add actions")
        val student = onNodeWithText("Add student")
        val pack = onNodeWithText("Add package")
        val schedule = onNodeWithText("Add schedule")
        student.assertDoesNotExist()
        expand.performClick()
        onNodeWithText("No students yet").assertDoesNotExist()
        val studentBounds = student.assertIsDisplayed().getUnclippedBoundsInRoot()
        val packageBounds = pack.assertIsDisplayed().getUnclippedBoundsInRoot()
        val scheduleBounds = schedule.assertIsDisplayed().getUnclippedBoundsInRoot()
        val plusBounds = collapse.getUnclippedBoundsInRoot()
        val width = studentBounds.right - studentBounds.left
        assertEquals(width, packageBounds.right - packageBounds.left)
        assertEquals(width, scheduleBounds.right - scheduleBounds.left)
        assertTrue(studentBounds.bottom < packageBounds.top)
        assertTrue(packageBounds.bottom < scheduleBounds.top)
        assertTrue(scheduleBounds.bottom < plusBounds.top)

        onNodeWithContentDescription("Dismiss add actions").performClick()
        student.assertDoesNotExist()
        onNodeWithText("No students yet").assertIsDisplayed()
        expand.performClick()
        collapse.performClick()
        pack.assertDoesNotExist()
        expand.performClick()
        schedule.performClick()
        schedule.assertDoesNotExist()
        val dialog = hasAnyAncestor(isDialog())
        onNode(dialog and hasText("Add Schedule")).assertIsDisplayed()
        onNode(dialog and hasText("Cancel")).performClick()

        runOnIdle { darkTheme = true }
        expand.performClick()
        student.assertIsDisplayed()
        pack.assertIsDisplayed()
        schedule.assertIsDisplayed()
        collapse.performClick()
        schedule.assertDoesNotExist()
    }

    @Test
    fun `quick actions open shared forms and a failed package save can be retried`() = runComposeUiTest {
        val requests = mutableListOf<CreatePackageTypeRequest>()
        val creation = creationForTest { request ->
            requests += request
            if (requests.size == 1) {
                CreatePackageTypeUiState.SaveFailed
            } else {
                CreatePackageTypeUiState.Success(request.name)
            }
        }
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(Attendance(emptyList()), emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = { null },
                creation = creation,
            )
        }
        val addActions = onNodeWithContentDescription("Add actions")
        val dialog = hasAnyAncestor(isDialog())
        addActions.performClick()
        onNodeWithText("Add student").performClick()
        onNode(dialog and hasText("Add Student")).assertIsDisplayed()
        onNode(dialog and hasText("Cancel")).performClick()

        addActions.performClick()
        onNodeWithText("Add package").performClick()
        onNode(dialog and hasText("Create package")).assertIsDisplayed()
        val packageName = onNodeWithTag("create-package-name").performScrollTo()
        packageName.performTouchInput { click() }
        packageName.assertIsFocused().performTextReplacement("Private package")
        onNodeWithTag("create-package-classes").performScrollTo().performTextReplacement("8")
        onNodeWithTag("create-package-price").performScrollTo().performTextReplacement("1200")
        onNodeWithTag("create-package-weeks").performScrollTo().performTextReplacement("4")
        val confirm = onNodeWithTag("create-package-confirm")
        confirm.assertIsEnabled().performClick()
        onNodeWithTag("create-package-error").assertExists()
        confirm.assertIsEnabled().performClick()
        onNode(dialog and hasText("Create package")).assertDoesNotExist()
        runOnIdle {
            assertEquals(List(2) { CreatePackageTypeRequest("Private package", 8, 1200, 4, 1) }, requests)
        }
    }

    @Test
    fun `check opens the shared calendar and saves the selected class`() = runComposeUiTest {
        val attendance = testStudentAttendanceUiState(startedOn = null).attendance
        var edit: AttendanceRosterEdit? = null
        setContent {
            AttendanceRosterScreen(
                state = AttendanceRosterState.Content(AttendanceRoster(attendance, emptyMap())),
                now = Instant.parse("2026-10-02T10:00:00Z"),
                onRefresh = {},
                onSignOut = {},
                onEdit = {
                    edit = it
                    null
                },
            )
        }
        onNodeWithContentDescription("Mark ${AttendanceTestData.student.fullName} present").performClick()
        val dialog = hasAnyAncestor(isDialog())
        onNode(dialog and hasText("Select attendance date")).assertIsDisplayed()
        onNode(dialog and hasText("OK")).performClick()
        runOnIdle {
            val marked = assertIs<AttendanceRosterEdit.Mark>(edit)
            assertEquals(LocalDate(2026, 10, 2), marked.date)
            assertEquals(1, marked.visits)
        }
        onNode(dialog and hasText("Select attendance date")).assertDoesNotExist()
    }

    private fun creationForTest(
        addPackage: suspend (CreatePackageTypeRequest) -> CreatePackageTypeUiState = { error("Unexpected save") },
    ) = IosAttendanceCreation(
        addStudent = { error("Unexpected registration") },
        addPackage = addPackage,
        addSchedule = { error("Unexpected schedule save") },
    )
}
