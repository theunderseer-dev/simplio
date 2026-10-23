package com.theunderseer.simplio.feature.attendance.presentation.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentPackageRenewalTestCallback
import com.theunderseer.simplio.feature.attendance.fixtures.testCompletedPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageRenewalState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentPackage
import com.theunderseer.simplio.feature.attendance.presentation.RenewStudentPackageUiState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AttendanceScreenPackageRenewalUiTest {
    private val student = AttendanceTestData.student.copy(note = "Return change")

    @Test
    fun `ordinary check calendar excludes attendance from a previous package`() = runComposeUiTest {
        val old = testCompletedPackage(student, AttendanceTestData.duoSpecialPackage)
        val last = old.attendanceDates.maxOrNull()!!
        setContent {
            AttendanceScreenForTest(
                testStudentAttendanceUiState(
                    student = student,
                    packageType = AttendanceTestData.duoSpecialPackage,
                    startedOn = null,
                    purchasedOn = last,
                    currentDate = last,
                    previousAttendanceDates = old.attendanceDates,
                ),
            )
        }
        onNodeWithContentDescription("Mark Anna Smith present").performClick()
        onNodeWithText("Monday, October 12, 2026", substring = true).assertIsNotEnabled()
        onNodeWithText("No classes to check yet").assertIsDisplayed()
    }

    @Test
    fun `purchase on final attendance date disables first attendance for four five and duo`() {
        for (type in listOf(
            AttendanceTestData.fourClassPackage,
            AttendanceTestData.fiveClassPackage,
            AttendanceTestData.duoSpecialPackage,
        )) {
            runComposeUiTest {
                val old = testCompletedPackage(student, type)
                val date = old.attendanceDates.maxOrNull()!!
                val callback = StudentPackageRenewalTestCallback()
                setContent {
                    AttendanceScreenForTest(
                        testPackageRenewalState(student, old, currentDate = date),
                        onRenewStudentPackage = callback::onRenew,
                    )
                }
                onNodeWithContentDescription("Renew package for Anna Smith").performClick()
                onNodeWithText("Record first attendance").performScrollTo().assertIsOff().assertIsNotEnabled()
                onNodeWithText(
                    "Attendance already recorded for this date. Renew without adding a visit",
                ).assertIsDisplayed()
                onNodeWithText("People attending").assertDoesNotExist()
                onNodeWithText("Valid until", substring = true).assertDoesNotExist()
                onNodeWithText("Renew").assertIsEnabled().performClick()
                val submitted = assertNotNull(callback.submission)
                assertEquals(date, submitted.purchasedOn)
                assertEquals(false, submitted.recordFirstAttendance)
            }
        }
    }

    @Test
    fun `changing renewal dates restores availability and preserves manual checkbox choice`() = runComposeUiTest {
        val old = testCompletedPackage(student, AttendanceTestData.duoSpecialPackage)
        setContent {
            AttendanceScreenForTest(
                testPackageRenewalState(student, old, currentDate = AttendanceTestData.fifthClassDate),
            )
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        fun selectDate(text: String) {
            onNodeWithContentDescription("Purchase date").performScrollTo().performClick()
            onNodeWithText(text, substring = true).performClick()
            onNodeWithText("OK").performClick()
        }
        onNodeWithText("Record first attendance").performScrollTo().assertIsOn().assertIsEnabled()
        selectDate("Monday, October 12, 2026")
        onNodeWithText("Record first attendance").performScrollTo().assertIsOff().assertIsNotEnabled()
        selectDate("Friday, October 16, 2026")
        onNodeWithText("Record first attendance").performScrollTo().assertIsOn().assertIsEnabled().performClick()
        selectDate("Monday, October 12, 2026")
        selectDate("Friday, October 16, 2026")
        onNodeWithText("Record first attendance").performScrollTo().assertIsOff().assertIsEnabled()
    }

    @Test
    fun `completed four five and duo packages show renewal instead of check`() {
        for (type in listOf(
            AttendanceTestData.fourClassPackage,
            AttendanceTestData.fiveClassPackage,
            AttendanceTestData.duoSpecialPackage,
        )) {
            runComposeUiTest {
                setContent {
                    AttendanceScreenForTest(
                        testPackageRenewalState(
                            previousPackage =
                            testCompletedPackage(packageType = type),
                        ),
                    )
                }
                onNodeWithContentDescription("Renew package for Anna Smith").assertIsDisplayed().assertIsEnabled()
                onNodeWithContentDescription("Mark Anna Smith present").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `expired incomplete package offers renewal`() = runComposeUiTest {
        val previous = testStudentPackage(startedOn = LocalDate(2026, 8, 1))
        setContent { AttendanceScreenForTest(testPackageRenewalState(previousPackage = previous)) }
        onNodeWithContentDescription("Renew package for Anna Smith").assertIsDisplayed()
        onNodeWithContentDescription("Mark Anna Smith present").assertDoesNotExist()
    }

    @Test
    fun `renewal opens prefilled note and package without editable student identity`() = runComposeUiTest {
        val callback = StudentPackageRenewalTestCallback()
        setContent {
            AttendanceScreenForTest(testPackageRenewalState(student), onRenewStudentPackage = callback::onRenew)
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        onNodeWithText("Renew package for Anna Smith").assertIsDisplayed()
        onNodeWithTag("registration-full-name").assertDoesNotExist()
        onNodeWithTag("package-purchase-package").assertTextContains("4-class package")
        onNodeWithText("Record first attendance").assertIsOn()
        onNodeWithTag("registration-note").performScrollTo().assertTextContains("Return change")
        onNodeWithText("Renew").performClick()
        val submission = assertNotNull(callback.submission)
        assertEquals(student.id, submission.studentId)
        assertEquals(1, submission.previousPackageId)
        assertEquals("Return change", submission.note)
        assertEquals(true, submission.recordFirstAttendance)
    }

    @Test
    fun `renewal sends changed package purchase date and an explicitly cleared note`() = runComposeUiTest {
        val callback = StudentPackageRenewalTestCallback()
        setContent {
            AttendanceScreenForTest(testPackageRenewalState(student), onRenewStudentPackage = callback::onRenew)
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        onNodeWithContentDescription("Purchase date").performClick()
        onNodeWithText("Friday, October 16, 2026", substring = true).performClick()
        onNodeWithText("OK").performClick()
        onNodeWithTag("package-purchase-package").performClick()
        onNodeWithText("5-class package").performClick()
        onNodeWithTag("registration-note").performScrollTo().performTextReplacement("")
        onNodeWithText("Renew").performClick()
        val submission = assertNotNull(callback.submission)
        assertEquals(AttendanceTestData.fiveClassPackage, submission.packageType)
        assertEquals(AttendanceTestData.fifthClassDate, submission.purchasedOn)
        assertEquals("", submission.note)
    }

    @Test
    fun `duo renewal records two visits and hides preview and people when attendance is unchecked`() =
        runComposeUiTest {
            val callback = StudentPackageRenewalTestCallback()
            setContent {
                AttendanceScreenForTest(testPackageRenewalState(student), onRenewStudentPackage = callback::onRenew)
            }
            onNodeWithContentDescription("Renew package for Anna Smith").performClick()
            onNodeWithTag("package-purchase-package").performClick()
            onNodeWithText("Duo 8 · Special rate").performClick()
            onNodeWithText("2 people").assertIsDisplayed()
            onNodeWithText("Valid until", substring = true).assertIsDisplayed()
            onNodeWithText("Record first attendance").performClick()
            onNodeWithText("People attending").assertDoesNotExist()
            onNodeWithText("Valid until", substring = true).assertDoesNotExist()
            onNodeWithText("Renew").performClick()
            assertEquals(false, assertNotNull(callback.submission).recordFirstAttendance)
            onNodeWithText("Record first attendance").performClick()
            onNodeWithText("Renew").performClick()
            assertEquals(2, assertNotNull(callback.submission).visitCount)
            assertEquals(true, assertNotNull(callback.submission).recordFirstAttendance)
        }

    @Test
    fun `purchase calendar respects schedule and bounds with or without first attendance`() {
        for (recordAttendance in listOf(true, false)) {
            runComposeUiTest {
                setContent { AttendanceScreenForTest(testPackageRenewalState(student)) }
                onNodeWithContentDescription("Renew package for Anna Smith").performClick()
                if (!recordAttendance) onNodeWithText("Record first attendance").performScrollTo().performClick()
                onNodeWithContentDescription("Purchase date").performScrollTo().performClick()
                onNodeWithText("Friday, October 9, 2026", substring = true).assertIsNotEnabled()
                onNodeWithText("Monday, October 12, 2026", substring = true).assertIsEnabled()
                onNodeWithText("Friday, October 16, 2026", substring = true).assertIsEnabled()
                onNodeWithText("Monday, October 19, 2026", substring = true).assertIsEnabled()
                for (date in listOf(
                    "Tuesday, October 13, 2026",
                    "Wednesday, October 14, 2026",
                    "Thursday, October 15, 2026",
                    "Saturday, October 17, 2026",
                    "Sunday, October 18, 2026",
                    "Friday, October 23, 2026",
                )) {
                    onNodeWithText(date, substring = true).assertIsNotEnabled()
                }
            }
        }
    }

    @Test
    fun `non scheduled today defaults renewal to the latest scheduled class`() = runComposeUiTest {
        val callback = StudentPackageRenewalTestCallback()
        setContent {
            AttendanceScreenForTest(
                testPackageRenewalState(student, currentDate = LocalDate(2026, 10, 22)),
                onRenewStudentPackage = callback::onRenew,
            )
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        onNodeWithText("Oct 19").assertIsDisplayed()
        onNodeWithText("Renew").assertIsEnabled().performClick()
        assertEquals(AttendanceTestData.sixthClassDate, assertNotNull(callback.submission).purchasedOn)
    }

    @Test
    fun `renewal waits for the next scheduled date after incomplete package expiration`() = runComposeUiTest {
        val callback = StudentPackageRenewalTestCallback()
        val previous = testStudentPackage(startedOn = LocalDate(2026, 9, 18))
        setContent {
            AttendanceScreenForTest(
                testPackageRenewalState(student, previous, currentDate = LocalDate(2026, 10, 18)),
                onRenewStudentPackage = callback::onRenew,
            )
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        onNodeWithText("No scheduled dates available yet").assertIsDisplayed()
        onNodeWithText("Valid until", substring = true).assertDoesNotExist()
        onNodeWithText("Renew").assertIsNotEnabled()
        onNodeWithText("Record first attendance").performScrollTo().performClick()
        onNodeWithText("Renew").assertIsNotEnabled()
        onNodeWithContentDescription("Purchase date").performScrollTo().performClick()
        onNodeWithText("Friday, October 16, 2026", substring = true).assertIsNotEnabled()
        onNodeWithText("Saturday, October 17, 2026", substring = true).assertIsNotEnabled()
        onNodeWithText("Sunday, October 18, 2026", substring = true).assertIsNotEnabled()
        onNodeWithText("Monday, October 19, 2026", substring = true).assertIsNotEnabled()
        onNodeWithText("OK").assertIsNotEnabled()
        assertNull(callback.submission)
    }

    @Test
    fun `cancelling renewal dispatches no purchase or attendance`() = runComposeUiTest {
        val callback = StudentPackageRenewalTestCallback()
        var attendanceRequests = 0
        setContent {
            AttendanceScreenForTest(
                testPackageRenewalState(student),
                onRenewStudentPackage = callback::onRenew,
                onMarkStudentPresent = { _, _, _ -> attendanceRequests++ },
                onRemoveStudentAttendance = { _, _ -> attendanceRequests++ },
            )
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Renew").assertDoesNotExist()
        assertNull(callback.submission)
        assertEquals(0, attendanceRequests)
    }

    @Test
    fun `saving blocks dismissal error keeps fields and success restores the check action`() = runComposeUiTest {
        var state by mutableStateOf(testPackageRenewalState(student))
        setContent {
            AttendanceScreenForTest(state, onRenewStudentPackage = { id, _, _, _, _, _, _ ->
                state = state.copy(renewStudentPackageState = RenewStudentPackageUiState.Saving(id))
            }, onClearRenewStudentPackageState = {
                state = state.copy(renewStudentPackageState = RenewStudentPackageUiState.Idle)
            })
        }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        onNodeWithTag("registration-note").performScrollTo().performTextReplacement("Reviewed note")
        onNodeWithText("Renew").performClick()
        onNodeWithText("Saving...").assertIsNotEnabled()
        onNodeWithText("Cancel").assertIsNotEnabled()
        runOnIdle {
            state = state.copy(renewStudentPackageState = RenewStudentPackageUiState.Error(student.id, "Try again"))
        }
        onNodeWithTag("registration-note").performScrollTo().assertTextContains("Reviewed note")
        onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
        onNodeWithText("Renew").assertIsEnabled()
        runOnIdle {
            state = testStudentAttendanceUiState(
                student = student.copy(note = "Reviewed note"),
                startedOn = AttendanceTestData.sixthClassDate,
                currentDate = AttendanceTestData.sixthClassDate,
            )
                .copy(renewStudentPackageState = RenewStudentPackageUiState.Success(student.id))
        }
        onNodeWithText("Renew").assertDoesNotExist()
        onNodeWithContentDescription("Mark Anna Smith present").assertIsDisplayed()
        onNodeWithText("1 / 4").assertIsDisplayed()
    }

    @Test
    fun `dialog retains the original package identity when screen data changes`() = runComposeUiTest {
        var state by mutableStateOf(testPackageRenewalState(student))
        val callback = StudentPackageRenewalTestCallback()
        setContent { AttendanceScreenForTest(state, onRenewStudentPackage = callback::onRenew) }
        onNodeWithContentDescription("Renew package for Anna Smith").performClick()
        runOnIdle { state = testPackageRenewalState(student, testCompletedPackage(student, id = 2)) }
        onNodeWithText("Renew").performClick()
        assertEquals(1, assertNotNull(callback.submission).previousPackageId)
    }
}
