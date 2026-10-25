package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomPackageTypeCreationRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.domain.model.CreatePackageTypeResult
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testCreatePackageTypeRequest
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestoreCustomPackageEmulatorTest {
    @Test
    fun `nullable package template and activated purchase round trip through real Firestore`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, device ->
                source.seedSyncDefaults()
                val type = assertIs<CreatePackageTypeResult.Success>(
                    RoomPackageTypeCreationRepository(source).create(testCreatePackageTypeRequest()),
                ).packageType
                source.registerSyncStudent(packageType = type)
                val writer = RoomUploadStore(source)
                val remote = FirestoreAttendanceUploadRemote(client)
                while (true) {
                    val batch = writer.prepare(target) ?: break
                    writer.acknowledge(batch, remote.sendOrRecover(writer.snapshot(batch), batch.commitBody))
                }
                val reader = FirestoreAttendancePullRemote(client)
                RoomPullStore(device, target).apply(reader.readSnapshot(reader.readHead()))
                val student = device.studentDao().getAll().single()
                val pack = assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(student.id))
                assertEquals(type.name, pack.packageType.name)
                assertNull(pack.packageType.validityWeeks)
                assertNull(pack.expiresOn)
                assertEquals(1, pack.usedClasses)
                assertNotNull(pack.startedOn)
                assertNull(RoomUploadStore(device).prepare(target))
            }
        }
    }
}
