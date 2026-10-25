package com.theunderseer.simplio.feature.attendance.presentation.viewmodel

import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.domain.model.PackageTypeValidation
import com.theunderseer.simplio.feature.attendance.domain.repository.PackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreatePackageTypeUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakePackageTypeRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeStudentRepository
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.testCreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.presentation.AttendanceUiState
import com.theunderseer.simplio.feature.attendance.presentation.CreatePackageTypeUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelPackageCreationTest {
    @Test
    fun `save refreshes package choices and duplicate keeps content visible`() = runViewModelTest {
        val catalog = FakePackageTypeRepository(listOf(AttendanceTestData.fourClassPackage))
        val vm = createViewModel(FakeStudentRepository(), FakeStudentPackageRepository(), packageCatalog = catalog)
        observeUiState(vm)
        advanceUntilIdle()
        val request = testCreatePackageTypeRequest()
        vm.createPackageType(request)
        vm.createPackageType(request)
        advanceUntilIdle()
        val saved = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        assertEquals(2, saved.packageTypes.size)
        assertEquals(CreatePackageTypeUiState.Success(request.name), saved.createPackageTypeState)
        vm.clearCreatePackageTypeState()
        vm.createPackageType(request)
        advanceUntilIdle()
        val duplicate = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        assertEquals(saved.packageTypes, duplicate.packageTypes)
        assertEquals(CreatePackageTypeUiState.DuplicateName, duplicate.createPackageTypeState)
    }

    @Test
    fun `invalid request keeps catalog unchanged and can be corrected`() = runViewModelTest {
        val vm = createViewModel(FakeStudentRepository(), FakeStudentPackageRepository())
        observeUiState(vm)
        advanceUntilIdle()
        vm.createPackageType(testCreatePackageTypeRequest().copy(totalClasses = 0))
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        assertEquals(1, state.packageTypes.size)
        assertEquals(CreatePackageTypeUiState.Invalid(PackageTypeValidation.CLASSES), state.createPackageTypeState)
        vm.clearCreatePackageTypeState()
        vm.createPackageType(testCreatePackageTypeRequest())
        advanceUntilIdle()
        assertIs<CreatePackageTypeUiState.Success>(
            assertIs<AttendanceUiState.Content>(vm.uiState.value).createPackageTypeState,
        )
    }

    @Test
    fun `storage failure does not replace attendance with full screen error`() = runViewModelTest {
        val useCase = CreatePackageTypeUseCase(object : PackageTypeCreationRepository {
            override suspend fun create(request: CreatePackageTypeRequest): CreatePackageTypeResult =
                error("Unavailable")
        })
        val vm = createViewModel(
            FakeStudentRepository(),
            FakeStudentPackageRepository(),
            createPackageTypeUseCase = useCase,
        )
        observeUiState(vm)
        advanceUntilIdle()
        vm.createPackageType(testCreatePackageTypeRequest())
        advanceUntilIdle()
        val state = assertIs<AttendanceUiState.Content>(vm.uiState.value)
        assertEquals(1, state.packageTypes.size)
        assertEquals(CreatePackageTypeUiState.SaveFailed, state.createPackageTypeState)
    }
}
