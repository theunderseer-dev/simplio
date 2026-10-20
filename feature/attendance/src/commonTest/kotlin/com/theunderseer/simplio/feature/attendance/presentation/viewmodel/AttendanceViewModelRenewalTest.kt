package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testCompletedPackage
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.RenewStudentPackageUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelRenewalTest {
    private val student = AttendanceTestData.student.copy(note = "Return change")
    private val previous = testCompletedPackage(student)
    private val date = AttendanceTestData.sixthClassDate

    @Test
    fun `renewing without attendance refreshes the row and reviewed note`() = runViewModelTest {
        val students = FakeStudentRepository().also { it.seed(student) }
        val packages = FakeStudentPackageRepository(listOf(previous))
        val model = createViewModel(students, packages, currentDates = flowOf(date))
        observeUiState(model)
        advanceUntilIdle()
        model.renewStudentPackage(
            student.id,
            previous.id,
            AttendanceTestData.fiveClassPackage,
            date,
            1,
            false,
            "Updated note",
        )
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(model.uiState.value)
        assertEquals(RenewStudentPackageUiState.Success(student.id), state.renewStudentPackageState)
        val row = state.rows.single()
        assertFalse(row.requiresRenewal)
        assertEquals("Updated note", row.registeredStudent.student.note)
        assertEquals(0, row.registeredStudent.studentPackage.usedClasses)
        assertNull(row.registeredStudent.studentPackage.expiresOn)
        assertEquals(previous, packages.packages.first())
    }

    @Test
    fun `duplicate confirmation while saving creates only one package`() = runViewModelTest {
        val students = FakeStudentRepository().also { it.seed(student) }
        val packages = FakeStudentPackageRepository(listOf(previous))
        val model = createViewModel(students, packages, currentDates = flowOf(date))
        observeUiState(model)
        advanceUntilIdle()
        model.renewStudentPackage(student.id, previous.id, AttendanceTestData.fourClassPackage, date, 1, true, "First")
        model.renewStudentPackage(student.id, previous.id, AttendanceTestData.fourClassPackage, date, 1, true, "Second")
        advanceUntilIdle()
        assertEquals(2, packages.packages.size)
        assertEquals("First", students.students.single().note)
    }

    @Test
    fun `stale renewal is an inline error without hiding attendance content`() = runViewModelTest {
        val students = FakeStudentRepository().also { it.seed(student) }
        val packages = FakeStudentPackageRepository(listOf(previous))
        val model = createViewModel(students, packages, currentDates = flowOf(date))
        observeUiState(model)
        advanceUntilIdle()
        model.renewStudentPackage(
            student.id,
            previous.id + 1,
            AttendanceTestData.fourClassPackage,
            date,
            1,
            true,
            "Changed",
        )
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(model.uiState.value)
        assertEquals(student, state.attendance.entries.single().student)
        assertEquals(student.id, assertIs<RenewStudentPackageUiState.Error>(state.renewStudentPackageState).studentId)
        assertEquals(1, packages.packages.size)
        model.clearRenewStudentPackageState()
        advanceUntilIdle()
        assertEquals(
            RenewStudentPackageUiState.Idle,
            assertIs<AttendanceUiState.Content>(model.uiState.value).renewStudentPackageState,
        )
    }

    @Test
    fun `removing the new first attendance preserves the renewed purchase and previous package`() = runViewModelTest {
        val students = FakeStudentRepository().also { it.seed(student) }
        val packages = FakeStudentPackageRepository(listOf(previous))
        val model = createViewModel(students, packages, currentDates = flowOf(date))
        observeUiState(model)
        advanceUntilIdle()
        model.renewStudentPackage(
            student.id,
            previous.id,
            AttendanceTestData.fourClassPackage,
            date,
            1,
            true,
            student.note,
        )
        advanceUntilIdle()
        val renewedId = packages.packages.last().id
        model.removeStudentAttendance(student.id, date)
        advanceUntilIdle()
        val pack = assertIs<AttendanceUiState.Content>(model.uiState.value).attendance.entries.single().studentPackage
        assertEquals(renewedId, pack.id)
        assertEquals(date, pack.purchasedOn)
        assertEquals(0, pack.usedClasses)
        assertNull(pack.startedOn)
        assertEquals(2, packages.packages.size)
        assertEquals(previous, packages.packages.first())
    }
}
