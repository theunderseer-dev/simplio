package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentGroupDeletionRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.DeleteStudentGroupUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelGroupDeletionTest {
    @Test
    fun `duplicate request is ignored and occupied group error preserves attendance content`() = runViewModelTest {
        var calls = 0
        val repository = object : StudentGroupDeletionRepository {
            override suspend fun delete(groupId: Int): DeleteStudentGroupResult {
                calls++
                return DeleteStudentGroupResult.NOT_EMPTY
            }
        }
        val vm =
            createViewModel(
                FakeStudentRepository(),
                FakeStudentPackageRepository(),
                groupDeletionRepository = repository,
            )
        observeUiState(vm)
        advanceUntilIdle()
        val id = AttendanceTestData.student.groupId
        vm.deleteStudentGroup(id)
        vm.deleteStudentGroup(id)
        advanceUntilIdle()
        assertEquals(1, calls)
        val state = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        assertEquals(
            DeleteStudentGroupUiState.Error(id, DeleteStudentGroupResult.NOT_EMPTY),
            state.deleteStudentGroupState,
        )
        vm.clearDeleteStudentGroupState()
        advanceUntilIdle()
        assertEquals(
            DeleteStudentGroupUiState.Idle,
            assertIs<AttendanceUiState.Content>(vm.uiState.value).deleteStudentGroupState,
        )
    }
}
