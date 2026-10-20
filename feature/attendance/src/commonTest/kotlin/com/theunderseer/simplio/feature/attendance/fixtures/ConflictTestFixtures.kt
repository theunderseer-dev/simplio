package com.theunderseer.simplio.feature.attendance.fixtures

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud

internal class ConflictTestDevices(
    val source: AttendanceDatabase,
    val device: AttendanceDatabase,
    val cloud: FakeAttendanceCloud,
    val registered: RegisteredStudent,
    val localStudentId: Int,
    val localPackageId: Int,
) {
    val store = RoomConflictStore(device, testUploadTarget)
    suspend fun localNote(value: String) = RoomStudentRepository(device).updateNote(localStudentId, value)
    suspend fun remoteNote(value: String) {
        RoomStudentRepository(source).updateNote(registered.student.id, value)
        cloud.uploadPending(source)
    }
}

internal suspend fun withConflictDevices(
    recordFirstAttendance: Boolean = true,
    block: suspend ConflictTestDevices.() -> Unit,
) = withSyncDatabases { source, device ->
    val registered = source.registerSyncStudent(recordFirstAttendance)
    val cloud = FakeAttendanceCloud()
    cloud.uploadPending(source)
    RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
    val id = device.studentDao().getAll().single().id
    val pack = checkNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(id))
    ConflictTestDevices(source, device, cloud, registered, id, pack.id).block()
}

internal suspend fun AttendanceDatabase.registerAdditionalSyncStudent(fullName: String): RegisteredStudent {
    val type = AttendanceTestData.fourClassPackage.copy(
        id = packageTypeDao().getAll().first { it.name == AttendanceTestData.fourClassPackage.name }.id,
    )
    return RegisterStudentUseCase(
        RoomStudentRepository(this),
        RoomStudentPackageRepository(this),
        RoomTransactionRunner(this),
    )(
        fullName = fullName,
        groupId = AttendanceTestData.student.groupId,
        packageType = type,
        registeredOn = AttendanceTestData.registrationDate,
    )
}
