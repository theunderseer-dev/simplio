package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testClassAttendanceCallbacks
import com.theunderseer.simplio.feature.attendance.fixtures.testClassAttendanceSheet
import com.theunderseer.simplio.feature.attendance.fixtures.testStudentAttendanceUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ClassStudentWorkflowUiTest {
    @Test
    fun `creating a package returns to registration with name note and attendance choice preserved`() =
        runComposeUiTest {
            val sheet = testClassAttendanceSheet().let { it.copy(groupIds = setOf(it.rows.single().student.groupId)) }
            val newPackage = AttendanceTestData.duoSpecialPackage.copy(validityWeeks = null)
            var registeredName: String? = null
            setContent {
                var content by remember {
                    mutableStateOf(
                        ClassAttendanceUiState.Content(
                            sheet,
                            studentAction = ClassStudentAction(ClassStudentActionKind.REGISTER, null),
                        ),
                    )
                }
                var catalog by remember { mutableStateOf(listOf(AttendanceTestData.fourClassPackage)) }
                val callbacks = testClassAttendanceCallbacks().copy(
                    showPackageCreation = { show ->
                        content =
                            content.copy(studentAction = content.studentAction?.copy(creatingPackage = show))
                    },
                    register = { name, _, pack, _, first, note ->
                        registeredName = name
                        assertEquals(newPackage, pack)
                        assertEquals(false, first)
                        assertEquals("Keep this note", note)
                    },
                )
                ClassAttendanceWorkflow(
                    testStudentAttendanceUiState().copy(
                        packageTypes = catalog,
                        studentGroups = AttendanceTestData.studentGroups,
                        classAttendanceState = content,
                    ),
                    callbacks,
                    { request ->
                        assertEquals(newPackage.name, request.name)
                        assertEquals(newPackage.totalClasses, request.totalClasses)
                        assertEquals(newPackage.maxVisitsPerAttendance, request.maxVisitsPerAttendance)
                        assertEquals(newPackage.price, request.price)
                        assertEquals(newPackage.validityWeeks, request.validityWeeks)
                        catalog = catalog + newPackage
                        content =
                            content.copy(
                                studentAction = content.studentAction?.copy(
                                    creatingPackage = false,
                                    preferredPackageTypeId = newPackage.id,
                                ),
                            )
                    },
                    {},
                )
            }
            onNodeWithTag("registration-full-name").performTextInput(AttendanceTestData.student.fullName)
            onNodeWithTag("registration-note").performTextInput("Keep this note")
            onNodeWithText("Record first attendance").performScrollTo().performClick()
            onNodeWithText("Registration date").performClick()
            onNodeWithText("Select registration date").assertDoesNotExist()
            onNodeWithText("Add package").performScrollTo().performClick()
            onNodeWithTag("create-package-name").performTextInput(newPackage.name)
            onNodeWithTag("create-package-classes").performTextInput(newPackage.totalClasses.toString())
            onNodeWithTag("create-package-price").performTextInput(newPackage.price.toString())
            onNodeWithTag("create-package-no-expiration").performScrollTo().performClick()
            onNodeWithTag("create-package-allow-two").performScrollTo().performClick()
            onNodeWithTag("create-package-confirm").performClick()
            onNodeWithTag("registration-full-name").assertTextContains(AttendanceTestData.student.fullName)
            onNodeWithTag("registration-note").assertTextContains("Keep this note")
            onNodeWithTag("package-purchase-package").assertTextContains(newPackage.name)
            onNodeWithText("Record first attendance").assertIsOff()
            onNodeWithText("Register").performClick()
            assertEquals(AttendanceTestData.student.fullName, registeredName)
        }

    @Test
    fun `renew replaces checkbox only for an exhausted package without a visit on this class`() = runComposeUiTest {
        val sheet = testClassAttendanceSheet()
        var renewed: Int? = null
        setContent {
            ClassAttendanceDialog(
                ClassAttendanceUiState.Content(
                    sheet.copy(
                        rows = sheet.rows.map {
                            it.copy(canRecord = false, canRenew = true)
                        },
                    ),
                ),
                {},
                {},
                { _, _ -> },
                { _, _ -> },
                {},
                { renewed = it },
            )
        }
        onNodeWithTag("class-attendance-student-1").assertDoesNotExist()
        onNodeWithTag("class-renew-student-1").performClick()
        assertEquals(sheet.rows.single().student.id, renewed)
    }
}
