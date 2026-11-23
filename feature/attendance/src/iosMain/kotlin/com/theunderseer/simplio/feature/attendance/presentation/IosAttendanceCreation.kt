package com.theunderseer.simplio.feature.attendance.presentation

import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreatePackageTypeUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.CreateStudentGroupUseCase
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.datetime.LocalDate

internal class IosAttendanceCreation(
    val addStudent: suspend (IosStudentRegistration) -> RegisterStudentUiState,
    val addPackage: suspend (CreatePackageTypeRequest) -> CreatePackageTypeUiState,
    val addSchedule: suspend (CreateStudentGroupRequest) -> CreateStudentGroupUiState,
)

internal class IosStudentRegistration(
    val name: String,
    val groupId: Int,
    val packageType: PackageType,
    val registeredOn: LocalDate,
    val visitCount: Int,
    val recordFirstAttendance: Boolean,
    val note: String,
)

internal fun CoroutineScope.iosAttendanceCreation(
    registerStudent: RegisterStudentUseCase,
    createPackage: CreatePackageTypeUseCase,
    createSchedule: CreateStudentGroupUseCase,
): IosAttendanceCreation = IosAttendanceCreation(
    addStudent = { request ->
        submitCreation(onFailure = { it.toRegistrationError() }) {
            val registered = registerStudent(
                request.name,
                request.groupId,
                request.packageType,
                request.registeredOn,
                request.visitCount,
                request.recordFirstAttendance,
                request.note,
            )
            RegisterStudentUiState.Success(registered.student.fullName)
        }
    },
    addPackage = { request ->
        submitCreation(onFailure = { CreatePackageTypeUiState.SaveFailed }) { createPackage(request).toUiState() }
    },
    addSchedule = { request ->
        submitCreation(onFailure = { CreateStudentGroupUiState.SaveFailed }) { createSchedule(request).toUiState() }
    },
)

// The account scope owns in-flight writes so closing or recreating a form cannot interrupt a commit.
@Suppress("TooGenericExceptionCaught") // Failed writes become form errors and leave the account session active.
private suspend fun <State> CoroutineScope.submitCreation(
    onFailure: (Exception) -> State,
    save: suspend () -> State,
): State = async {
    try {
        save()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
    }
}.await()
