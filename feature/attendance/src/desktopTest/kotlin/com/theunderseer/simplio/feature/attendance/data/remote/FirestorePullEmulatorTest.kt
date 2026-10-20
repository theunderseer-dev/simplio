package com.theunderseer.simplio.feature.attendance.data.remote

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncCoordinator
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceSyncStatus
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadCoordinator
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.withFirestoreEmulator
import com.theunderseer.simplio.feature.attendance.fixtures.withSyncDatabases
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestorePullEmulatorTest {
    @Test
    fun `two databases exchange notes and attendance deletions without echo uploads`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { first, second ->
                val registered = first.registerSyncStudent()
                val remote = FirestoreAttendancePullRemote(client)
                fun coordinator(database: AttendanceDatabase) = AttendanceSyncCoordinator(
                    RoomPullStore(database, target),
                    remote,
                    AttendanceUploadCoordinator(
                        RoomUploadStore(database),
                        FirestoreAttendanceUploadRemote(client),
                        target,
                    ),
                )
                val firstSync = coordinator(first)
                repeat(4) { firstSync.syncOnce() }
                second.seedSyncDefaults()
                val secondSync = coordinator(second)
                assertEquals(AttendanceSyncStatus.Idle, secondSync.syncOnce())
                val localId = second.studentDao().getAll().single().id
                RoomStudentRepository(second).updateNote(localId, "Edited on second device")
                assertEquals(AttendanceSyncStatus.Syncing, secondSync.syncOnce())
                assertEquals(AttendanceSyncStatus.Idle, firstSync.syncOnce())
                assertEquals("Edited on second device", first.studentDao().getById(registered.student.id)?.note)
                RoomStudentPackageRepository(first).removeAttendance(
                    registered.studentPackage.id,
                    AttendanceTestData.firstClassDate,
                )
                assertEquals(AttendanceSyncStatus.Syncing, firstSync.syncOnce())
                assertEquals(AttendanceSyncStatus.Idle, secondSync.syncOnce())
                val pack = assertNotNull(RoomStudentPackageRepository(second).getLatestPackageForStudent(localId))
                assertNull(pack.startedOn)
                assertNull(pack.expiresOn)
                assertEquals(0, pack.usedClasses)
                assertNull(RoomUploadStore(second).prepare(target))
            }
        }
    }

    @Test
    fun `all collections are read at captured head time despite concurrent remote write`() = runTest {
        withFirestoreEmulator { client, target ->
            withSyncDatabases { source, destination ->
                val student = source.registerSyncStudent().student
                val uploader = AttendanceUploadCoordinator(
                    RoomUploadStore(source),
                    FirestoreAttendanceUploadRemote(client),
                    target,
                )
                repeat(4) { uploader.uploadNext() }
                val remote = FirestoreAttendancePullRemote(client)
                val original = remote.readHead()
                RoomStudentRepository(source).updateNote(student.id, "Changed after captured head")
                uploader.uploadNext()
                val captured = remote.readSnapshot(original)
                RoomPullStore(destination, target).apply(captured)
                val localId = destination.studentDao().getAll().single().id
                assertEquals("", destination.studentDao().getById(localId)?.note)
                val latest = remote.readHead()
                RoomPullStore(destination, target).apply(remote.readSnapshot(latest))
                assertEquals("Changed after captured head", destination.studentDao().getById(localId)?.note)
            }
        }
    }

    @Test
    fun `snapshot follows document cursor beyond five hundred documents`() = runTest {
        withFirestoreEmulator { client, target ->
            val writes = (1..501).map { index ->
                buildJsonObject {
                    put(
                        "update",
                        buildJsonObject {
                            put("name", client.documentName("studentGroups", "group-$index"))
                            put(
                                "fields",
                                buildJsonObject {
                                    put("schemaVersion", buildJsonObject { put("integerValue", "1") })
                                    put("lastMutationId", buildJsonObject { put("stringValue", "mutation-$index") })
                                    put("isDeleted", buildJsonObject { put("booleanValue", false) })
                                    put("name", buildJsonObject { put("stringValue", "Group $index") })
                                },
                            )
                        },
                    )
                    put("currentDocument", buildJsonObject { put("exists", false) })
                }
            }
            writes.chunked(250).forEach { batch ->
                client.commit(buildJsonObject { put("writes", JsonArray(batch)) }.toString())
            }
            client.commit(
                buildJsonObject {
                    put(
                        "writes",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put(
                                        "update",
                                        buildJsonObject {
                                            put(
                                                "name",
                                                client.documentName(
                                                    FirestoreUploadProtocol.HEAD_COLLECTION,
                                                    FirestoreUploadProtocol.HEAD_ID,
                                                ),
                                            )
                                            put(
                                                "fields",
                                                buildJsonObject {
                                                    put("protocolVersion", buildJsonObject { put("integerValue", "1") })
                                                    put(
                                                        "batchId",
                                                        buildJsonObject {
                                                            put("stringValue", "pagination-fixture")
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            ),
                        ),
                    )
                }.toString(),
            )
            val remote = FirestoreAttendancePullRemote(client)
            val snapshot = remote.readSnapshot(remote.readHead())
            assertEquals(501, snapshot.documents.size)
            assertEquals(501, snapshot.documents.map { it.cloudId }.toSet().size)
            assertEquals(true, snapshot.documents.all { it.type == SyncEntityType.STUDENT_GROUP })
            assertEquals(
                (1..501).map { "Group $it" }.toSet(),
                snapshot.documents.map {
                    it.fields.getValue("name").jsonObject.getValue("stringValue").jsonPrimitive.content
                }.toSet(),
            )
        }
    }
}
