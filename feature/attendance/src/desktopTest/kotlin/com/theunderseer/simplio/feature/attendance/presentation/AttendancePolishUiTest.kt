package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.screen.AttendanceScreenForTest
import com.theunderseer.simplio.shared.presentation.theme.SimplioTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AttendancePolishUiTest {
    @Test
    fun `group heading has no divider and note feedback has inner padding`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                val student = AttendanceTestData.student
                val state = testStudentAttendanceUiState()
                val group = state.studentGroups.first { it.id == student.groupId }
                setContent {
                    SimplioTheme(dark) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            AttendanceScreenForTest(state)
                        }
                    }
                }
                val row = onNodeWithTag("student-row-${student.id}").fetchSemanticsNode().boundsInRoot
                onNodeWithTag("student-group-divider-${student.groupId}").assertDoesNotExist()
                val heading = onNodeWithContentDescription("Copy attendance for ${group.name}")
                    .fetchSemanticsNode().boundsInRoot
                val headingGap = row.top - heading.bottom
                assertTrue(headingGap >= -1f, "Group heading overlaps its first card: gap = $headingGap px")
                val note = onNodeWithTag("student-note-${student.id}")
                val layouts = mutableListOf<TextLayoutResult>()
                note.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val noteNode = note.fetchSemanticsNode()
                val bounds = noteNode.boundsInRoot
                val expectedPadding = with(noteNode.layoutInfo.density) { 16.dp.toPx() }
                val horizontalPadding = bounds.width - layouts.single().size.width
                val verticalPadding = bounds.height - layouts.single().size.height
                // Text layout uses whole pixels while semantic bounds may contain fractions.
                assertTrue(
                    horizontalPadding >= expectedPadding - 1f,
                    "Note horizontal padding: $horizontalPadding px; expected at least $expectedPadding px",
                )
                assertTrue(
                    verticalPadding >= expectedPadding - 1f,
                    "Note vertical padding: $verticalPadding px; expected at least $expectedPadding px",
                )
                note.performMouseInput { moveTo(center) }
                export("note-hover-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun `package checkbox rows clip hover feedback and preserve toggling`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                setContent {
                    SimplioTheme(dark) {
                        PackageTypeCreationDialog(emptyList(), CreatePackageTypeUiState.Idle, {}, {}, {})
                    }
                }
                for (tag in listOf("create-package-no-expiration", "create-package-allow-two")) {
                    val option = onNodeWithTag(tag).assertIsOff()
                    val corner = option.captureToImage().toPixelMap()[0, 0]
                    option.performMouseInput { moveTo(center) }
                    waitForIdle()
                    assertEquals(corner, option.captureToImage().toPixelMap()[0, 0])
                    option.performMouseInput { click() }
                    option.assertIsOn()
                }
                export("package-options-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun `overflowing group list exposes a scrollbar and the complete last card`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                val state = testStudentAttendanceUiState().copy(
                    studentGroups = (1..8).map { StudentGroup(100 + it, "Group $it") },
                )
                setContent {
                    SimplioTheme(dark) { StudentGroupManagementDialog(state, {}, {}, {}) }
                }
                onNodeWithTag("manage-groups-scrollbar").assertIsDisplayed()
                onNodeWithTag("manage-groups-scroll-hint").assertIsDisplayed()
                export("groups-scroll-top-${if (dark) "dark" else "light"}")
                val list = onNodeWithTag("manage-groups-list")
                list.performScrollToNode(hasTestTag("manage-group-108"))
                val last = onNodeWithTag("manage-group-108").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val viewport = list.fetchSemanticsNode().boundsInRoot
                assertTrue(last.bottom <= viewport.bottom + 1f)
                assertTrue(last.top >= viewport.top - 1f)
                export("groups-scroll-bottom-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun `short management window keeps the scrollbar close action and last group reachable`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            val state = testStudentAttendanceUiState().copy(
                studentGroups = (1..8).map { StudentGroup(100 + it, "Group $it") },
            )
            setContent { SimplioTheme(false) { StudentGroupManagementDialog(state, {}, {}, {}) } }
            onNodeWithTag("manage-groups-scrollbar").assertIsDisplayed()
            onNodeWithTag("manage-groups-scroll-hint").assertIsDisplayed()
            onNodeWithText("Close").assertIsDisplayed()
            val list = onNodeWithTag("manage-groups-list")
            list.performScrollToNode(hasTestTag("manage-group-108"))
            val last = onNodeWithTag("manage-group-108").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val viewport = list.fetchSemanticsNode().boundsInRoot
            assertTrue(last.bottom <= viewport.bottom + 1f)
            assertTrue(last.top >= viewport.top - 1f)
        }

    @Test
    fun `history link keeps one feedback style after closing its popup and hovering again`() {
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(width = 1200, height = 1000) {
                setContent {
                    SimplioTheme(dark) {
                        Surface(Modifier.fillMaxSize()) { AttendanceScreenForTest(testStudentAttendanceUiState()) }
                    }
                }
                val link = onNodeWithTag("student-package-history-${AttendanceTestData.student.id}")
                fun edge() = link.captureToImage().toPixelMap().let { it[it.width / 2, 0] }
                val initialEdge = edge()
                link.performMouseInput { click() }
                onNodeWithTag("package-history-menu").assertIsDisplayed()
                onAllNodes(isRoot()).onFirst().performMouseInput { click(Offset(10f, 10f)) }
                onNodeWithTag("package-history-menu").assertDoesNotExist()
                link.performMouseInput { moveTo(center) }
                waitForIdle()
                val hoveredEdge = edge()
                // The parent row may tint slightly; a saturated focus frame must not appear.
                assertTrue(
                    listOf(
                        abs(initialEdge.red - hoveredEdge.red),
                        abs(initialEdge.green - hoveredEdge.green),
                        abs(initialEdge.blue - hoveredEdge.blue),
                    ).all { it < 0.02f },
                    "Focus and hover must not add a frame around the link",
                )
                export("history-link-hover-${if (dark) "dark" else "light"}")
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
