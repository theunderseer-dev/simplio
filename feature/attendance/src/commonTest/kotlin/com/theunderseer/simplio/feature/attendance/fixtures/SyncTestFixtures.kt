package com.theunderseer.simplio.feature.attendance.fixtures

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.seed.PackageTypeSeeder
import com.theunderseer.simplio.feature.attendance.data.local.seed.StudentGroupSeeder
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.RegisteredStudent
import com.theunderseer.simplio.feature.attendance.domain.usecase.RegisterStudentUseCase

internal fun testSyncDatabase(): AttendanceDatabase = Room
    .inMemoryDatabaseBuilder<AttendanceDatabase>()
    .setDriver(BundledSQLiteDriver())
    .build()

internal suspend fun AttendanceDatabase.seedSyncDefaults() {
    StudentGroupSeeder(this).seedDefaults()
    PackageTypeSeeder(this).seedDefaults()
}

internal suspend fun AttendanceDatabase.registerSyncStudent(
    recordFirstAttendance: Boolean = true,
    packageType: PackageType = AttendanceTestData.fourClassPackage,
): RegisteredStudent {
    seedSyncDefaults()
    val storedType = packageType.copy(
        id = packageTypeDao().getAll().first { it.name == packageType.name }.id,
    )
    return RegisterStudentUseCase(
        studentRepository = RoomStudentRepository(this),
        studentPackageRepository = RoomStudentPackageRepository(this),
        transactionRunner = RoomTransactionRunner(this),
    )(
        fullName = AttendanceTestData.student.fullName,
        groupId = AttendanceTestData.student.groupId,
        packageType = storedType,
        visitCount = storedType.maxVisitsPerAttendance,
        registeredOn = AttendanceTestData.registrationDate,
        recordFirstAttendance = recordFirstAttendance,
    )
}
