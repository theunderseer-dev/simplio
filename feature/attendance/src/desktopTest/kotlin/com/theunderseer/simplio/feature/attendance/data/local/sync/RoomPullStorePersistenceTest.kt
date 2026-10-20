package com.theunderseer.simplio.feature.attendance.data.local.sync

import androidx.room.useWriterConnection
import com.theunderseer.simplio.feature.attendance.data.local.database.createAttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.fake.FakeAttendanceCloud
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomPullStorePersistenceTest {
    @Test
    fun `reopening preserves pulled graph checkpoint and acknowledged identities`() = runTest {
        val source = testSyncDatabase()
        val directory = Files.createTempDirectory("simplio-pull-reopen-")
        val path = directory.resolve("attendance.db").toString()
        try {
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot()
            val first = createAttendanceDatabase(path)
            val records = try {
                first.seedSyncDefaults()
                RoomPullStore(first, testUploadTarget).apply(snapshot)
                first.syncPullDao().records()
            } finally {
                first.close()
            }
            val reopened = createAttendanceDatabase(path)
            try {
                assertEquals(records, reopened.syncPullDao().records())
                assertEquals(snapshot.head.readTime, reopened.syncUploadDao().getState()?.lastPullReadTime)
                assertEquals(snapshot.head.updateTime, reopened.syncUploadDao().getState()?.headUpdateTime)
                val id = reopened.studentDao().getAll().single().id
                val pack = assertNotNull(RoomStudentPackageRepository(reopened).getLatestPackageForStudent(id))
                assertEquals(1, pack.usedClasses)
                assertNotNull(pack.startedOn)
                assertEquals(0, reopened.syncPullDao().pendingCount())
                assertNull(RoomUploadStore(reopened).prepare(testUploadTarget))
            } finally {
                reopened.close()
            }
        } finally {
            source.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `checkpoint failure rolls back imported rows identities and removed seed queue`() = runTest {
        val source = testSyncDatabase()
        val directory = Files.createTempDirectory("simplio-pull-rollback-")
        val path = directory.resolve("attendance.db").toString()
        val database = createAttendanceDatabase(path)
        try {
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            database.seedSyncDefaults()
            val original = RoomSyncOutbox(database).getPending()
            database.useWriterConnection { connection ->
                connection.usePrepared(
                    """
                    CREATE TRIGGER fail_pull BEFORE INSERT ON sync_upload_state
                    BEGIN SELECT RAISE(ABORT, 'Simulated pull checkpoint failure'); END
                    """.trimIndent(),
                ) { it.step() }
            }
            assertFailsWith<Exception> { RoomPullStore(database, testUploadTarget).apply(cloud.snapshot()) }
            assertEquals(original, RoomSyncOutbox(database).getPending())
            assertEquals(0, database.studentDao().count())
            assertEquals(2, database.studentGroupDao().getAll().size)
            assertNull(database.syncUploadDao().getState())
        } finally {
            database.close()
            source.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `seeding after reopening does not resurrect remote deleted templates`() = runTest {
        val source = testSyncDatabase()
        val directory = Files.createTempDirectory("simplio-pull-catalog-")
        val path = directory.resolve("attendance.db").toString()
        try {
            source.registerSyncStudent()
            val cloud = FakeAttendanceCloud()
            cloud.uploadPending(source)
            val snapshot = cloud.snapshot().let { original ->
                original.copy(
                    documents = original.documents.map { document ->
                        if (document.cloudId in setOf("default-group-b", "default-plan-five_classes")) {
                            document.copy(
                                fields = buildJsonObject {
                                    put("schemaVersion", buildJsonObject { put("integerValue", "1") })
                                    put("isDeleted", buildJsonObject { put("booleanValue", true) })
                                    put("lastMutationId", buildJsonObject { put("stringValue", "remote-delete") })
                                },
                            )
                        } else {
                            document
                        }
                    },
                )
            }
            val first = createAttendanceDatabase(path)
            try {
                first.seedSyncDefaults()
                RoomPullStore(first, testUploadTarget).apply(snapshot)
            } finally {
                first.close()
            }
            val reopened = createAttendanceDatabase(path)
            try {
                reopened.seedSyncDefaults()
                assertEquals(listOf("Group A"), reopened.studentGroupDao().getAll().map { it.name })
                assertEquals(2, reopened.packageTypeDao().getAll().size)
                assertEquals(2, reopened.syncPullDao().records().count { it.isDeleted })
                assertEquals(0, reopened.syncPullDao().pendingCount())
            } finally {
                reopened.close()
            }
        } finally {
            source.close()
            directory.toFile().deleteRecursively()
        }
    }
}
