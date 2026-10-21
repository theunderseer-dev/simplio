package com.theunderseer.simplio.feature.attendance.presentation.dialog

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.StudentPackageRenewalTestCallback
import com.theunderseer.simplio.feature.attendance.fixtures.StudentRegistrationTestCallback
import com.theunderseer.simplio.feature.attendance.fixtures.testCompletedPackage
import com.theunderseer.simplio.feature.attendance.fixtures.testPackageRenewalState
import com.theunderseer.simplio.feature.attendance.presentation.screen.AttendanceScreenForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StudentPackageDialogWindowSizeUiTest {
    @Test
    fun `single and duo registration forms fit in the taller initial viewport`() {
        for (packageType in listOf(AttendanceTestData.fourClassPackage, AttendanceTestData.duoSpecialPackage)) {
            runSkikoComposeUiTest(size = Size(800f, 812f)) {
                setContent {
                    StudentRegistrationDialogForTest(
                        packageTypes = listOf(
                            AttendanceTestData.fourClassPackage,
                            AttendanceTestData.duoSpecialPackage,
                        ),
                    )
                }
                if (packageType == AttendanceTestData.duoSpecialPackage) {
                    onNodeWithTag("package-purchase-package").performClick()
                    onNodeWithText(packageType.name).performClick()
                    onNodeWithText("People attending").assertIsDisplayed()
                    onNodeWithText("2 people").assertIsDisplayed()
                }

                onNodeWithText("Scroll for more").assertDoesNotExist()
                onNodeWithTag("registration-full-name").assertIsDisplayed()
                val noteBounds = onNodeWithTag("registration-note").assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot
                val buttonBounds = onNodeWithText("Register").assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(noteBounds.height >= 55f, "The whole note field must be visible without scrolling")
                assertTrue(noteBounds.bottom <= buttonBounds.top, "The note must not be hidden behind the actions")
                onNodeWithText("Cancel").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `short registration viewport shows a hint and permits scrolling to submit a duo note`() =
        runSkikoComposeUiTest(size = Size(800f, 560f)) {
            val callback = StudentRegistrationTestCallback()
            setContent {
                StudentRegistrationDialogForTest(
                    packageTypes = listOf(AttendanceTestData.fourClassPackage, AttendanceTestData.duoSpecialPackage),
                    onRegister = callback::onRegister,
                )
            }
            onNodeWithTag("registration-full-name").performTextInput(AttendanceTestData.student.fullName)
            onNodeWithText("Group A").performClick()
            onNodeWithTag("package-purchase-package").performClick()
            onNodeWithText(AttendanceTestData.duoSpecialPackage.name).performClick()
            onNodeWithText("Scroll for more").assertIsDisplayed()
            onNodeWithText("Register").assertIsDisplayed()
            onNodeWithText("Cancel").assertIsDisplayed()

            onNodeWithTag("registration-note").performScrollTo().assertIsDisplayed().performTextInput("Return change")

            onNodeWithText("Scroll for more").assertDoesNotExist()
            onNodeWithText("Register").assertIsEnabled().performClick()
            val submission = assertNotNull(callback.submission)
            assertEquals("Return change", submission.note)
            assertEquals(AttendanceTestData.duoSpecialPackage, submission.packageType)
            assertEquals(2, submission.visitCount)
        }

    @Test
    fun `short renewal viewport keeps actions visible while scrolling a prefilled duo note`() =
        runSkikoComposeUiTest(size = Size(800f, 480f)) {
            val student = AttendanceTestData.student.copy(note = "Return change")
            val callback = StudentPackageRenewalTestCallback()
            setContent {
                AttendanceScreenForTest(
                    state = testPackageRenewalState(
                        student,
                        testCompletedPackage(student, AttendanceTestData.duoSpecialPackage),
                    ),
                    onRenewStudentPackage = callback::onRenew,
                )
            }
            onNodeWithContentDescription("Renew package for ${student.fullName}").performClick()
            onNodeWithText("Scroll for more").assertIsDisplayed()
            onNodeWithText("Renew").assertIsDisplayed()
            onNodeWithText("Cancel").assertIsDisplayed()

            onNodeWithTag("registration-note").performScrollTo().assertIsDisplayed().performTextReplacement("")

            onNodeWithText("Scroll for more").assertDoesNotExist()
            onNodeWithText("Renew").assertIsEnabled().performClick()
            assertEquals("", assertNotNull(callback.submission).note)
            assertEquals(2, assertNotNull(callback.submission).visitCount)
        }
}
