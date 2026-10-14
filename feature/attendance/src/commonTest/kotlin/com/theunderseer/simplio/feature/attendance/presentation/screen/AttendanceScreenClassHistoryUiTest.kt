package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.domain.model.Attendance
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData.firstClassDate
import com.theunderseer.simplio.feature.attendance.fixtures.testAttendanceContentUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testClassHistoryEntry
import com.theunderseer.simplio.feature.attendance.fixtures.testStudent
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassHistoryUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenClassHistoryUiTest {
    @Test
    fun `displays class date students from both groups and total visits`() = runComposeUiTest {
        // Given
        val anna = AttendanceTestData.student.copy(
            groupId = DefaultStudentGroups.GROUP_A.id,
        )
        val bob = testStudent(
            id = anna.id + 1,
            fullName = "Bob Lee",
            groupId = DefaultStudentGroups.GROUP_B.id,
        )

        val state = screenState(
            history = ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(
                        date = AttendanceTestData.secondClassDate,
                        students = listOf(
                            ClassHistoryStudent(
                                student = anna,
                                visitCount = 2,
                            ),
                            ClassHistoryStudent(
                                student = bob,
                                visitCount = 1,
                            ),
                        ),
                    ),
                ),
                nextBeforeDate = null,
            ),
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithText("Class History").assertIsDisplayed()
        onNodeWithText("Oct 5, 2026 (Monday)").assertIsDisplayed()
        onNodeWithText(anna.fullName).assertIsDisplayed()
        onNodeWithText(bob.fullName).assertIsDisplayed()
        onNodeWithText(DefaultStudentGroups.GROUP_A.name).assertIsDisplayed()
        onNodeWithText(DefaultStudentGroups.GROUP_B.name).assertIsDisplayed()
        onNodeWithText("3 visits").assertIsDisplayed()
        onNodeWithText("2 visits").assertIsDisplayed()
        onNodeWithText("1 visit").assertIsDisplayed()
        onNodeWithText("Load more").assertDoesNotExist()
    }

    @Test
    fun `displays class without recorded attendance`() = runComposeUiTest {
        // Given
        val state = screenState(
            history = ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(date = AttendanceTestData.secondClassDate),
                ),
                nextBeforeDate = null,
            ),
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithText("Oct 5, 2026 (Monday)").assertIsDisplayed()
        onNodeWithText("0 visits").assertIsDisplayed()
        onNodeWithText("No attendance recorded").assertIsDisplayed()
    }

    @Test
    fun `shows history loading without hiding add student action`() = runComposeUiTest {
        // Given
        val state = screenState(
            history = ClassHistoryUiState.Loading,
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithText("Class History").assertIsDisplayed()
        onNodeWithTag("class-history-loading").assertIsDisplayed()
        onNodeWithText("Add Student").assertIsDisplayed()
    }

    @Test
    fun `requests retry after initial history loading error`() = runComposeUiTest {
        // Given
        var retryRequests = 0
        val state = screenState(
            history = ClassHistoryUiState.Error(
                message = "History unavailable",
            ),
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
                onRetryClassHistory = { retryRequests++ },
            )
        }

        // Then
        onNodeWithText("History unavailable").assertIsDisplayed()
        onNodeWithText("Add Student").assertIsDisplayed()

        // When
        onNodeWithText("Retry").performClick()

        // Then
        assertEquals(1, retryRequests)
    }

    @Test
    fun `scrolls history to load more while add student stays in place`() = runComposeUiTest {
        // Given
        var loadRequests = 0
        val dates = listOf(
            AttendanceTestData.eighthClassDate,
            AttendanceTestData.seventhClassDate,
            AttendanceTestData.sixthClassDate,
            AttendanceTestData.fifthClassDate,
            AttendanceTestData.fourthClassDate,
            AttendanceTestData.thirdClassDate,
            AttendanceTestData.secondClassDate,
            AttendanceTestData.firstClassDate,
        )
        val state = screenState(
            history = ClassHistoryUiState.Content(
                entries = dates.map { date ->
                    testClassHistoryEntry(date = date)
                },
                nextBeforeDate = AttendanceTestData.firstClassDate,
            ),
            currentDate = AttendanceTestData.eighthClassDate,
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
                onLoadNextClassHistoryPage = { loadRequests++ },
            )
        }

        val addStudentBounds = onNodeWithText("Add Student")
            .fetchSemanticsNode()
            .boundsInRoot

        // When
        onNodeWithTag("class-history-list")
            .performScrollToNode(hasText("Load more"))

        onNodeWithText("Load more")
            .assertIsDisplayed()
            .performClick()

        // Then
        assertEquals(1, loadRequests)
        onNodeWithText("Add Student").assertIsDisplayed()
        assertEquals(
            addStudentBounds,
            onNodeWithText("Add Student")
                .fetchSemanticsNode()
                .boundsInRoot,
        )
    }

    @Test
    fun `disables load more while next page is loading`() = runComposeUiTest {
        // Given
        var loadRequests = 0
        val state = screenState(
            history = ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(date = AttendanceTestData.secondClassDate),
                ),
                nextBeforeDate = AttendanceTestData.secondClassDate,
                isLoadingNextPage = true,
            ),
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
                onLoadNextClassHistoryPage = { loadRequests++ },
            )
        }

        // Then
        onNodeWithText("Oct 5, 2026 (Monday)").assertIsDisplayed()
        onNodeWithTag("class-history-next-page-loading")
            .assertIsDisplayed()
        onNodeWithText("Load more").assertIsNotEnabled()

        // When
        onNodeWithText("Load more").performClick()

        // Then
        assertEquals(0, loadRequests)
    }

    @Test
    fun `preserves loaded classes and requests retry after next page error`() = runComposeUiTest {
        // Given
        var retryRequests = 0
        var loadRequests = 0
        val state = screenState(
            history = ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(date = AttendanceTestData.secondClassDate),
                ),
                nextBeforeDate = AttendanceTestData.secondClassDate,
                nextPageErrorMessage = "Older history unavailable",
            ),
        )

        setContent {
            AttendanceScreenForTest(
                state = state,
                onLoadNextClassHistoryPage = { loadRequests++ },
                onRetryClassHistory = { retryRequests++ },
            )
        }

        // Then
        onNodeWithText("Oct 5, 2026 (Monday)").assertIsDisplayed()
        onNodeWithText("Older history unavailable").assertIsDisplayed()

        // When
        onNodeWithText("Retry").performClick()

        // Then
        assertEquals(1, retryRequests)
        assertEquals(0, loadRequests)
    }

    @Test
    fun `displays weekday for Friday class`() = runComposeUiTest {
        // Given
        val state = screenState(
            history = ClassHistoryUiState.Content(
                entries = listOf(
                    testClassHistoryEntry(date = firstClassDate),
                ),
                nextBeforeDate = null,
            ),
        )

        // When
        setContent {
            AttendanceScreenForTest(state = state)
        }

        // Then
        onNodeWithText("Oct 2, 2026 (Friday)").assertIsDisplayed()
    }

    private fun screenState(
        history: ClassHistoryUiState,
        currentDate: LocalDate = AttendanceTestData.currentDate,
    ): AttendanceUiState.Content = testAttendanceContentUiState(
        attendance = Attendance(entries = emptyList()),
        packageTypes = listOf(AttendanceTestData.fourClassPackage),
        currentDate = currentDate,
    ).copy(
        studentGroups = DefaultStudentGroups.ALL,
        classHistoryState = history,
    )
}
