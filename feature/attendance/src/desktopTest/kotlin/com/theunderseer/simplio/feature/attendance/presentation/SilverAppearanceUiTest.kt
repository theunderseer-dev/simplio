package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceDialogForTest
import com.theunderseer.simplio.feature.attendance.fixtures.testClassAttendanceSheet
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.dialog.StudentRegistrationDialogForTest
import com.theunderseer.simplio.feature.attendance.presentation.screen.AttendanceScreenForTest
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SilverAppearanceUiTest {
    @Test
    fun `main screen keeps actions aligned and management after add package in both themes`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                val student = AttendanceTestData.student
                val state = testStudentAttendanceUiState().copy(
                    classHistoryState = ClassHistoryUiState.Content(
                        entries = listOf(
                            testClassHistoryEntry(
                                AttendanceTestData.secondClassDate,
                                listOf(ClassHistoryStudent(student, 1)),
                            ),
                        ),
                        nextBeforeDate = null,
                    ),
                )
                setContent {
                    SimplioTheme(dark) {
                        Surface(Modifier.fillMaxSize()) { AttendanceScreenForTest(state) }
                    }
                }
                val check = onNodeWithContentDescription("Mark ${student.fullName} present")
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val delete = onNodeWithContentDescription("Delete ${student.fullName}")
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(check.width >= 48 && check.height >= 48)
                assertTrue(kotlin.math.abs(check.center.y - delete.center.y) < 1f)
                val pack = onNodeWithText("Add Package").fetchSemanticsNode().boundsInRoot
                val more = onNodeWithContentDescription("Group options").fetchSemanticsNode().boundsInRoot
                assertTrue(more.left >= pack.right)
                export("main-${if (dark) "dark" else "light"}")
                onNodeWithContentDescription("Group options").performClick()
                onNodeWithText("Manage groups").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `group management keeps text inset and separates the warning in both themes`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                val state = testStudentAttendanceUiState()
                val group = state.studentGroups.first { it.id == AttendanceTestData.student.groupId }
                setContent {
                    SimplioTheme(dark) {
                        StudentGroupManagementDialog(state, {}, {}, {})
                    }
                }
                val card = onNodeWithTag("manage-group-${group.id}").fetchSemanticsNode().boundsInRoot
                val title = onNodeWithText(group.name).fetchSemanticsNode().boundsInRoot
                val warning = onNodeWithText("Move or remove the students before deleting this group.")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(title.left - card.left >= 12f)
                assertTrue(title.top - card.top >= 12f)
                assertTrue(warning.top - title.bottom >= 12f)
                assertTrue(card.bottom - warning.bottom >= 12f)
                export("manage-groups-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun `duo forms and calendar remain usable with the shared controls`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                setContent {
                    SimplioTheme(dark) {
                        StudentRegistrationDialogForTest(packageTypes = listOf(AttendanceTestData.duoSpecialPackage))
                    }
                }
                onNodeWithText("Group A").performClick()
                onNodeWithText("Note (optional)").performScrollTo().assertIsDisplayed()
                export("registration-${if (dark) "dark" else "light"}")
                onNodeWithText("Registration date").performScrollTo().performClick()
                onNodeWithText("Select registration date").assertIsDisplayed()
                export("calendar-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun `class attendance uses the shared check action in both themes`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                setContent {
                    SimplioTheme(dark) {
                        ClassAttendanceDialogForTest(
                            ClassAttendanceUiState.Content(testClassAttendanceSheet(duo = true)),
                            {},
                            {},
                            { _, _ -> },
                        )
                    }
                }
                onNodeWithTag("class-attendance-student-1").performClick()
                onNodeWithTag("class-attendance-people-1-2").performClick()
                onNodeWithText("2 / 8").assertIsDisplayed()
                export("class-${if (dark) "dark" else "light"}")
            }
        }
    }

    private fun ComposeUiTest.export(name: String) {
        val directory = System.getenv("SIMPLIO_VISUAL_OUTPUT") ?: return
        val file = File(directory, "$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", file)
    }
}
