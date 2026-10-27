package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.ClassAttendanceProblem
import com.theunderseer.simplio.feature.attendance.fake.FakeClassAttendanceRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.ClassAttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testCreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.ClassAttendanceUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelClassStudentActionsTest {
    @Test
    fun `nested package creation selects new template and keeps the attendance draft`() = runViewModelTest {
        val repository = FakeClassAttendanceRepository()
        val vm =
            createViewModel(
                FakeStudentRepository(),
                FakeStudentPackageRepository(),
                classAttendanceRepository = repository,
            )
        observeUiState(vm)
        advanceUntilIdle()
        vm.openClassAttendance(ClassAttendanceTestData.session.id)
        advanceUntilIdle()
        vm.selectClassAttendance(AttendanceTestData.student.id, 1)
        vm.addStudentToClass()
        vm.showClassPackageCreation(true)
        val request = testCreatePackageTypeRequest()
        vm.createPackageType(request)
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        val content = assertIs<ClassAttendanceUiState.Content>(state.classAttendanceState)
        assertEquals(
            state.packageTypes.single {
                it.name == request.name
            }.id,
            content.studentAction?.preferredPackageTypeId,
        )
        assertEquals(false, content.studentAction?.creatingPackage)
        assertEquals(mapOf(AttendanceTestData.student.id to 1), content.visitsByStudent)
        vm.dismissClassStudentAction()
        advanceUntilIdle()
        val returned =
            assertIs<ClassAttendanceUiState.Content>(
                assertIs<AttendanceUiState.Content>(vm.uiState.value).classAttendanceState,
            )
        assertEquals(content.visitsByStudent, returned.visitsByStudent)
    }

    @Test
    fun `stale registration preserves child form and attendance draft without creating a student`() = runViewModelTest {
        val students = FakeStudentRepository()
        val repository = FakeClassAttendanceRepository()
        val vm = createViewModel(students, FakeStudentPackageRepository(), classAttendanceRepository = repository)
        observeUiState(vm)
        advanceUntilIdle()
        vm.openClassAttendance(ClassAttendanceTestData.session.id)
        advanceUntilIdle()
        vm.selectClassAttendance(AttendanceTestData.student.id, 1)
        vm.addStudentToClass()
        repository.sheet = repository.sheet.copy(timeLabel = "Changed time")
        vm.registerStudentInClass(
            ClassAttendanceTestData.companionName,
            AttendanceTestData.student.groupId,
            AttendanceTestData.fourClassPackage,
            1,
            true,
            "",
        )
        advanceUntilIdle()
        val content =
            assertIs<ClassAttendanceUiState.Content>(
                assertIs<AttendanceUiState.Content>(vm.uiState.value).classAttendanceState,
            )
        assertEquals(ClassAttendanceProblem.STALE, content.studentAction?.problem)
        assertEquals(false, content.studentAction?.isSaving)
        assertEquals(mapOf(AttendanceTestData.student.id to 1), content.visitsByStudent)
        assertTrue(students.students.isEmpty())
        assertFalse(content.hasIndependentChanges)
    }
}
