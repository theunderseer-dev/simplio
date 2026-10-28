package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.InvalidConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.StaleConflictReview
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerAdditionalSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withConflictDevices
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomConflictStoreTest {
    @Test
    fun `review stores both versions without changing business rows or pending work`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val pending = RoomSyncOutbox(device).getPending()
            val review = store.create(cloud.snapshot())
            val item = review.items.single()
            assertEquals("Anna Smith", item.label)
            assertTrue(item.deviceDetails.contains("Note: Device note"))
            assertTrue(item.cloudDetails.contains("Note: Cloud note"))
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals(pending, RoomSyncOutbox(device).getPending())
            val archive = device.syncConflictDao().getAll().single()
            assertEquals("OPEN", archive.status)
            assertTrue(archive.localJson.contains("Device note"))
            assertTrue(archive.cloudJson.contains("Cloud note"))
        }
    }

    @Test
    fun `cloud selection imports note and archives the discarded local version`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val review = store.create(cloud.snapshot())
            store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
            assertEquals("Cloud note", device.studentDao().getById(localStudentId)?.note)
            assertEquals(0, device.syncPullDao().pendingCount())
            assertNull(RoomUploadStore(device).prepare(testUploadTarget))
            val archive = assertNotNull(device.syncConflictDao().get(review.id))
            assertEquals("APPLIED", archive.status)
            assertTrue(archive.localJson.contains("Device note"))
            assertTrue(checkNotNull(archive.choicesJson).contains("CLOUD"))
        }
    }

    @Test
    fun `device selection rebases note while retaining unrelated remote student`() = runTest {
        withConflictDevices {
            localNote("Keep device note")
            remoteNote("Cloud note")
            val bob = source.registerAdditionalSyncStudent("Bob Lee")
            RoomStudentRepository(source).updateNote(bob.student.id, "Remote Bob note")
            cloud.uploadPending(source)
            val review = store.create(cloud.snapshot())
            store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE), cloud.readHead())
            assertEquals("Keep device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals("Remote Bob note", device.studentDao().getAll().single { it.fullName == "Bob Lee" }.note)
            assertEquals(1, device.syncPullDao().pendingCount())
            cloud.uploadPending(device)
            RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
            assertEquals("Keep device note", source.studentDao().getById(registered.student.id)?.note)
            assertEquals("Remote Bob note", source.studentDao().getById(bob.student.id)?.note)
        }
    }

    @Test
    fun `independent local registration and remote note combine without a forced version choice`() = runTest {
        withConflictDevices {
            val bob = device.registerAdditionalSyncStudent("Bob Lee")
            remoteNote("Remote Anna note")
            val review = store.create(cloud.snapshot())
            assertEquals(emptyList(), review.items)
            store.apply(review.id, emptyMap(), cloud.readHead())
            assertEquals("Remote Anna note", device.studentDao().getById(localStudentId)?.note)
            assertEquals("Bob Lee", device.studentDao().getById(bob.student.id)?.fullName)
            assertEquals(3, device.syncPullDao().pendingCount())
            cloud.uploadPending(device)
            assertEquals(0, device.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `same first attendance on two devices uses one visit and no duplicate upload`() = runTest {
        withConflictDevices(recordFirstAttendance = false) {
            RoomStudentPackageRepository(device).recordAttendance(localPackageId, AttendanceTestData.secondClassDate, 1)
            RoomStudentPackageRepository(source).recordAttendance(
                registered.studentPackage.id,
                AttendanceTestData.secondClassDate,
                1,
            )
            cloud.uploadPending(source)
            val review = store.create(cloud.snapshot())
            assertEquals(emptyList(), review.items)
            store.apply(review.id, emptyMap(), cloud.readHead())
            val pack = assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(localStudentId))
            assertEquals(1, pack.usedClasses)
            assertEquals(AttendanceTestData.secondClassDate, pack.startedOn)
            assertEquals(0, device.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `mark versus removal keeps a complete chosen attendance history`() = runTest {
        ConflictChoice.entries.forEach { choice ->
            withConflictDevices {
                RoomStudentPackageRepository(
                    device,
                ).recordAttendance(localPackageId, AttendanceTestData.secondClassDate, 1)
                RoomStudentPackageRepository(source).removeAttendance(
                    registered.studentPackage.id,
                    AttendanceTestData.firstClassDate,
                )
                cloud.uploadPending(source)
                val review = store.create(cloud.snapshot())
                store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                val pack =
                    assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(localStudentId))
                assertEquals(if (choice == ConflictChoice.DEVICE) 2 else 0, pack.usedClasses)
                if (choice == ConflictChoice.CLOUD) {
                    assertNull(pack.startedOn)
                    assertNull(pack.expiresOn)
                }
                cloud.uploadPending(device)
            }
        }
    }

    @Test
    fun `device version fences out remote renewal and uploads all affected children as one aggregate`() = runTest {
        withConflictDevices {
            RoomStudentPackageRepository(device).recordAttendance(localPackageId, AttendanceTestData.secondClassDate, 1)
            val renewed = RoomStudentPackageRepository(source).create(
                registered.student.id,
                registered.studentPackage.packageType,
                AttendanceTestData.thirdClassDate,
                InitialAttendance(AttendanceTestData.thirdClassDate, 1),
            )
            cloud.uploadPending(source)
            val review = store.create(cloud.snapshot())
            assertTrue(review.items.single().cloudDetails.count { it.startsWith("Package:") } == 2)
            store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE), cloud.readHead())
            assertEquals(1, device.studentPackageDao().getForStudent(localStudentId).size)
            assertEquals(
                2,
                RoomStudentPackageRepository(device).getLatestPackageForStudent(localStudentId)?.usedClasses,
            )
            val pending = RoomSyncOutbox(device).getPending()
            assertEquals(1, pending.map { it.aggregateKey }.distinct().size)
            assertEquals(2, pending.count { it.operation == SyncOperation.DELETE })
            cloud.uploadPending(device)
            RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
            assertNull(source.studentPackageDao().getById(renewed.id))
            assertEquals(
                2,
                RoomStudentPackageRepository(source).getLatestPackageForStudent(registered.student.id)?.usedClasses,
            )
        }
    }

    @Test
    fun `cloud renewal discards only conflicting local attendance and keeps the purchased package`() = runTest {
        withConflictDevices {
            RoomStudentPackageRepository(device).recordAttendance(localPackageId, AttendanceTestData.secondClassDate, 1)
            val renewed = RoomStudentPackageRepository(source).create(
                registered.student.id,
                registered.studentPackage.packageType,
                AttendanceTestData.thirdClassDate,
                null,
            )
            cloud.uploadPending(source)
            val review = store.create(cloud.snapshot())
            store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
            val latest = assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(localStudentId))
            assertEquals(renewed.purchasedOn, latest.purchasedOn)
            assertNull(latest.startedOn)
            assertEquals(0, device.syncPullDao().pendingCount())
            assertEquals(1, device.attendanceDao().getForPackage(localPackageId).size)
        }
    }

    @Test
    fun `deletion versus remote note either restores or deletes the whole student graph`() = runTest {
        ConflictChoice.entries.forEach { choice ->
            withConflictDevices {
                RoomStudentRepository(device).delete(localStudentId)
                remoteNote("Cloud note")
                val review = store.create(cloud.snapshot())
                store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                assertEquals(if (choice == ConflictChoice.CLOUD) 1 else 0, device.studentDao().count())
                cloud.uploadPending(device)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                assertEquals(if (choice == ConflictChoice.CLOUD) 1 else 0, source.studentDao().count())
            }
        }
    }

    @Test
    fun `editing locally after review invalidates the decision without losing the newer note`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val review = store.create(cloud.snapshot())
            localNote("Newer device note")
            val pending = RoomSyncOutbox(device).getPending()
            assertFailsWith<StaleConflictReview> {
                store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
            }
            assertEquals("Newer device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals(pending, RoomSyncOutbox(device).getPending())
            assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
        }
    }

    @Test
    fun `remote update after review invalidates the decision and checkpoint`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val review = store.create(cloud.snapshot())
            val checkpoint = device.syncUploadDao().getState()
            remoteNote("Newer cloud note")
            assertFailsWith<StaleConflictReview> {
                store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE), cloud.readHead())
            }
            assertEquals(checkpoint, device.syncUploadDao().getState())
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
        }
    }

    @Test
    fun `a fenced frozen request is archived before cloud selection removes it`() = runTest {
        withConflictDevices {
            localNote("Device note")
            val frozen = assertNotNull(RoomUploadStore(device).prepare(testUploadTarget))
            remoteNote("Cloud note")
            val review = store.create(cloud.snapshot())
            store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
            assertNull(device.syncUploadDao().getBatch())
            val archive = assertNotNull(device.syncConflictDao().get(review.id))
            assertTrue(archive.localJson.contains(frozen.batchId))
            assertTrue(archive.localJson.contains("Device note"))
            assertEquals("APPLIED", archive.status)
        }
    }

    @Test
    fun `a frozen request with a still current base must be recovered before review`() = runTest {
        withConflictDevices {
            localNote("Device note")
            val frozen = RoomUploadStore(device).prepare(testUploadTarget)
            val error = assertFailsWith<UploadPreparationFailure> { store.create(cloud.snapshot()) }
            assertEquals(UploadProblem.CONFLICT, error.problem)
            assertEquals(frozen, device.syncUploadDao().getBatch())
            assertEquals(emptyList(), device.syncConflictDao().getAll())
        }
    }

    @Test
    fun `missing choices and mismatched accounts never apply a review`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val snapshot = cloud.snapshot()
            val review = store.create(snapshot)
            assertFailsWith<InvalidConflictChoice> { store.apply(review.id, emptyMap(), cloud.readHead()) }
            val error = assertFailsWith<UploadPreparationFailure> {
                store.apply(
                    review.id,
                    mapOf(review.items.single().key to ConflictChoice.DEVICE),
                    snapshot.head.copy(target = testUploadTarget.copy(userId = "someone-else")),
                )
            }
            assertEquals(UploadProblem.TARGET_MISMATCH, error.problem)
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals(1, device.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `invalid cloud graph cannot create a review or replace local data`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val snapshot = cloud.snapshot()
            val error = assertFailsWith<UploadPreparationFailure> {
                store.create(
                    snapshot.copy(
                        documents = snapshot.documents.filter {
                            it.type !=
                                SyncEntityType.STUDENT_GROUP
                        },
                    ),
                )
            }
            assertEquals(UploadProblem.INVALID_DATA, error.problem)
            assertEquals(emptyList(), device.syncConflictDao().getAll())
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
        }
    }

    @Test
    fun `two conflicting students can keep different versions without replacing the account`() = runTest {
        withConflictDevices {
            val bob = source.registerAdditionalSyncStudent("Bob Lee")
            cloud.uploadPending(source)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            val localBob = device.studentDao().getAll().single { it.fullName == "Bob Lee" }
            localNote("Device Anna")
            RoomStudentRepository(device).updateNote(localBob.id, "Device Bob")
            remoteNote("Cloud Anna")
            RoomStudentRepository(source).updateNote(bob.student.id, "Cloud Bob")
            cloud.uploadPending(source)
            val review = store.create(cloud.snapshot())
            assertEquals(2, review.items.size)
            store.apply(
                review.id,
                review.items.associate {
                    it.key to if (it.label == "Anna Smith") ConflictChoice.DEVICE else ConflictChoice.CLOUD
                },
                cloud.readHead(),
            )
            assertEquals("Device Anna", device.studentDao().getById(localStudentId)?.note)
            assertEquals("Cloud Bob", device.studentDao().getById(localBob.id)?.note)
            assertEquals(1, device.syncPullDao().pendingCount())
            cloud.uploadPending(device)
        }
    }

    @Test
    fun `deleted catalog dependency requires a compatible explicit choice`() = runTest {
        withConflictDevices {
            device.registerAdditionalSyncStudent("Bob Lee")
            val original = cloud.snapshot()
            val snapshot = original.copy(
                documents = original.documents.map { document ->
                    when {
                        document.type == SyncEntityType.STUDENT_GROUP && document.cloudId == "default-group-a" ->
                            document.copy(fields = tombstoneFields(), updateTime = original.head.readTime)
                        document.type == SyncEntityType.STUDENT -> document.copy(
                            fields =
                            buildJsonObject {
                                document.fields.forEach { (key, value) -> put(key, value) }
                                put("groupId", FirestoreUploadProtocol.string("default-group-b"))
                            },
                            updateTime = original.head.readTime,
                        )
                        else -> document
                    }
                },
            )
            val review = store.create(snapshot)
            assertEquals(listOf("Group A"), review.items.map { it.label })
            assertFalse(review.items.single().cloudChoiceAllowed)
            val bad = mapOf(review.items.single().key to ConflictChoice.CLOUD)
            val before = RoomSyncOutbox(device).getPending()
            assertFailsWith<InvalidConflictChoice> { store.apply(review.id, bad, snapshot.head) }
            assertEquals(before, RoomSyncOutbox(device).getPending())
            assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
            store.apply(review.id, review.items.associate { it.key to ConflictChoice.DEVICE }, snapshot.head)
            assertEquals(2, device.studentDao().count())
            assertEquals("Group A", device.studentGroupDao().getAll().single { it.id == 1 }.name)
            assertEquals(5, device.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `remote deletion versus local attendance either restores the student or accepts deletion`() = runTest {
        ConflictChoice.entries.forEach { choice ->
            withConflictDevices {
                RoomStudentPackageRepository(
                    device,
                ).recordAttendance(localPackageId, AttendanceTestData.secondClassDate, 1)
                RoomStudentRepository(source).delete(registered.student.id)
                cloud.uploadPending(source)
                val review = store.create(cloud.snapshot())
                store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                if (choice == ConflictChoice.DEVICE) {
                    assertEquals(
                        2,
                        RoomStudentPackageRepository(device).getLatestPackageForStudent(localStudentId)?.usedClasses,
                    )
                } else {
                    assertEquals(0, device.studentDao().count())
                }
                cloud.uploadPending(device)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                assertEquals(if (choice == ConflictChoice.DEVICE) 1 else 0, source.studentDao().count())
            }
        }
    }

    @Test
    fun `device selection rejects a restored package after its tombstone lost the owner`() = runTest {
        withConflictDevices {
            RoomStudentPackageRepository(device).recordAttendance(localPackageId, AttendanceTestData.secondClassDate, 1)
            RoomStudentPackageRepository(source).create(
                registered.student.id,
                registered.studentPackage.packageType,
                AttendanceTestData.thirdClassDate,
                InitialAttendance(AttendanceTestData.thirdClassDate, 1),
            )
            cloud.uploadPending(source)
            val renewedSnapshot = cloud.snapshot()
            val renewedPackage = renewedSnapshot.documents.single {
                it.type == SyncEntityType.STUDENT_PACKAGE &&
                    it.integer("purchasedOnEpochDay") ==
                    AttendanceTestData.thirdClassDate.toEpochDays()
            }
            val renewedIds = renewedSnapshot.documents.filter {
                it.key == renewedPackage.key ||
                    it.type == SyncEntityType.ATTENDANCE &&
                    it.string("packageId") == renewedPackage.cloudId
            }.associateBy { it.key }
            val initial = store.create(renewedSnapshot)
            store.apply(initial.id, mapOf(initial.items.single().key to ConflictChoice.DEVICE), cloud.readHead())
            cloud.uploadPending(device)
            RoomPullStore(device, testUploadTarget).apply(cloud.snapshot())
            assertEquals(
                "legacy-deletions",
                device.syncPullDao().records().single {
                    it.entityType == SyncEntityType.STUDENT_PACKAGE && it.cloudId == renewedPackage.cloudId
                }.aggregateKey,
            )
            localNote("Keep my current package")
            val current = cloud.snapshot()
            val restored = current.copy(
                head = current.head.copy(updateTime = current.head.readTime),
                documents = current.documents.map { document ->
                    renewedIds[document.key]?.copy(updateTime = current.head.readTime) ?: document
                },
            )
            val review = store.create(restored)
            store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE), restored.head)
            assertEquals(1, device.studentPackageDao().getForStudent(localStudentId).size)
            val pending = RoomSyncOutbox(device).getPending()
            assertEquals(2, pending.count { it.operation == SyncOperation.DELETE })
            assertEquals(1, pending.map { it.aggregateKey }.distinct().size)
        }
    }

    @Test
    fun `independent renewals require choosing one complete package version`() = runTest {
        ConflictChoice.entries.forEach { choice ->
            withConflictDevices {
                val localType = assertNotNull(
                    RoomStudentPackageRepository(device)
                        .getLatestPackageForStudent(localStudentId),
                ).packageType
                RoomStudentPackageRepository(device).create(
                    localStudentId,
                    localType,
                    AttendanceTestData.thirdClassDate,
                    null,
                )
                val remoteType = AttendanceTestData.fiveClassPackage.copy(
                    id = source.packageTypeDao().getAll()
                        .single { it.name == AttendanceTestData.fiveClassPackage.name }.id,
                )
                RoomStudentPackageRepository(source).create(
                    registered.student.id,
                    remoteType,
                    AttendanceTestData.thirdClassDate,
                    null,
                )
                cloud.uploadPending(source)
                val review = store.create(cloud.snapshot())
                assertEquals(1, review.items.size)
                store.apply(review.id, mapOf(review.items.single().key to choice), cloud.readHead())
                assertEquals(2, device.studentPackageDao().getForStudent(localStudentId).size)
                val latest =
                    assertNotNull(RoomStudentPackageRepository(device).getLatestPackageForStudent(localStudentId))
                assertEquals(if (choice == ConflictChoice.DEVICE) 4 else 5, latest.packageType.totalClasses)
                assertNull(latest.startedOn)
                assertNull(latest.expiresOn)
                cloud.uploadPending(device)
                RoomPullStore(source, testUploadTarget).apply(cloud.snapshot())
                assertEquals(2, source.studentPackageDao().getForStudent(registered.student.id).size)
            }
        }
    }

    @Test
    fun `oversized device selection keeps the archive and queue so the cloud version can still be selected`() =
        runTest {
            withConflictDevices {
                localNote("x".repeat(900_000))
                remoteNote("Cloud note")
                val review = store.create(cloud.snapshot())
                val pending = RoomSyncOutbox(device).getPending()
                val error = assertFailsWith<UploadPreparationFailure> {
                    store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.DEVICE), cloud.readHead())
                }
                assertEquals(UploadProblem.TOO_LARGE, error.problem)
                assertEquals(pending, RoomSyncOutbox(device).getPending())
                assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
                store.apply(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD), cloud.readHead())
                assertEquals("Cloud note", device.studentDao().getById(localStudentId)?.note)
                assertEquals(0, device.syncPullDao().pendingCount())
                assertEquals("APPLIED", device.syncConflictDao().get(review.id)?.status)
            }
        }
}
