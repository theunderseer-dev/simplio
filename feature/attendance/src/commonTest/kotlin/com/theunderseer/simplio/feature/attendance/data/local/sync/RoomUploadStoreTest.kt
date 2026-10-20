package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncOutboxEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.fixtures.acknowledgeCatalog
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.testServerVersion
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RoomUploadStoreTest {
    @Test
    fun `uploads catalog before complete student aggregate`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            val catalog = assertNotNull(store.prepare(testUploadTarget))
            assertEquals(5, store.snapshot(catalog).documents.size)
            store.acknowledge(catalog, testUploadAcknowledgement(store.snapshot(catalog)))
            val batch = assertNotNull(store.prepare(testUploadTarget))
            val changes = store.snapshot(batch).documents.map { it.change }
            assertEquals(
                setOf(SyncEntityType.STUDENT, SyncEntityType.STUDENT_PACKAGE, SyncEntityType.ATTENDANCE),
                changes.map { it.entityType }.toSet(),
            )
            assertEquals(setOf("STUDENT:${student.id}"), changes.map { it.aggregateKey }.toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun `prepared request stays immutable when local note changes`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            store.acknowledgeCatalog()
            val batch = assertNotNull(store.prepare(testUploadTarget))
            RoomStudentRepository(database).updateNote(student.id, "new note")
            assertEquals(batch, store.prepare(testUploadTarget))
            assertFalse(batch.commitBody.contains("new note"))
            assertFalse(batch.snapshotJson.contains("id-token"))
            assertEquals(3, RoomSyncOutbox(database).getPending().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `payload uses cloud references and excludes derived fields`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            val store = RoomUploadStore(database)
            store.acknowledgeCatalog()
            val documents = store.snapshot(assertNotNull(store.prepare(testUploadTarget))).documents
            val student = documents.single { it.change.entityType == SyncEntityType.STUDENT }
            val pack = documents.single { it.change.entityType == SyncEntityType.STUDENT_PACKAGE }
            val attendance = documents.single { it.change.entityType == SyncEntityType.ATTENDANCE }
            assertEquals(
                student.change.cloudId,
                pack.fields.getValue("studentId").jsonObject
                    .getValue("stringValue").jsonPrimitive.content,
            )
            assertEquals(
                pack.change.cloudId,
                attendance.fields.getValue("packageId").jsonObject
                    .getValue("stringValue").jsonPrimitive.content,
            )
            assertEquals(
                "4",
                pack.fields.getValue("totalClasses").jsonObject.getValue("integerValue").jsonPrimitive.content,
            )
            assertNotNull(pack.fields["purchasedOnEpochDay"])
            listOf("startedOnEpochDay", "expiresOn", "usedClasses", "remainingClasses").forEach {
                assertNull(pack.fields[it])
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `acknowledging old revision preserves newer pending edit`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            store.acknowledgeCatalog()
            val batch = assertNotNull(store.prepare(testUploadTarget))
            RoomStudentRepository(database).updateNote(student.id, "new note")
            store.acknowledge(batch, testUploadAcknowledgement(store.snapshot(batch)))
            val pending = RoomSyncOutbox(database).getPending()
            assertEquals(1, pending.size)
            assertEquals(SyncEntityType.STUDENT, pending.single().entityType)
            assertEquals(testServerVersion, pending.single().serverUpdateTime)
            assertEquals(testServerVersion, database.syncUploadDao().getState()?.headUpdateTime)
            assertNull(database.syncUploadDao().getBatch())
            val next = assertNotNull(store.prepare(testUploadTarget))
            assertEquals(true, next.commitBody.contains("new note"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `deletion waits for frozen request then uploads tombstones`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            store.acknowledgeCatalog()
            val batch = assertNotNull(store.prepare(testUploadTarget))
            RoomStudentRepository(database).delete(student.id)
            assertEquals(batch, store.prepare(testUploadTarget))
            store.acknowledge(batch, testUploadAcknowledgement(store.snapshot(batch)))
            val documents = store.snapshot(assertNotNull(store.prepare(testUploadTarget))).documents
            assertEquals(3, documents.size)
            documents.forEach {
                assertEquals(SyncOperation.DELETE, it.change.operation)
                assertEquals(setOf("schemaVersion", "isDeleted", "lastMutationId"), it.fields.keys)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `database cannot upload to another account or project`() = runTest {
        val database = testSyncDatabase()
        try {
            database.registerSyncStudent()
            val store = RoomUploadStore(database)
            val original = store.prepare(testUploadTarget)
            listOf(
                testUploadTarget.copy(userId = "other"),
                testUploadTarget.copy(projectId = "other-project"),
            ).forEach {
                val error = assertFailsWith<UploadPreparationFailure> { store.prepare(it) }
                assertEquals(UploadProblem.TARGET_MISMATCH, error.problem)
            }
            assertEquals(original, store.prepare(testUploadTarget))
        } finally {
            database.close()
        }
    }

    @Test
    fun `oversized aggregate is paused without splitting it`() = runTest {
        val database = testSyncDatabase()
        try {
            repeat(401) { index ->
                val id = "1:$index"
                database.syncDao().saveRecord(
                    SyncRecordEntity(
                        SyncEntityType.ATTENDANCE,
                        id,
                        "cloud-$index",
                        1,
                        isDeleted = true,
                        aggregateKey = "STUDENT:1",
                    ),
                )
                database.syncDao().enqueue(
                    SyncOutboxEntity(
                        entityType = SyncEntityType.ATTENDANCE,
                        localId = id,
                        revision = 1,
                        operation = SyncOperation.DELETE,
                        mutationId = "mutation-$index",
                    ),
                )
            }
            val error = assertFailsWith<UploadPreparationFailure> {
                RoomUploadStore(database).prepare(testUploadTarget)
            }
            assertEquals(UploadProblem.TOO_LARGE, error.problem)
            assertNull(database.syncUploadDao().getBatch())
            assertEquals(401, RoomSyncOutbox(database).getPending(500).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `oversized document remains local without preparing request`() = runTest {
        val database = testSyncDatabase()
        try {
            val student = database.registerSyncStudent().student
            val store = RoomUploadStore(database)
            store.acknowledgeCatalog()
            RoomStudentRepository(database).updateNote(student.id, "x".repeat(910_000))
            val error = assertFailsWith<UploadPreparationFailure> { store.prepare(testUploadTarget) }
            assertEquals(UploadProblem.TOO_LARGE, error.problem)
            assertNull(database.syncUploadDao().getBatch())
            assertEquals(3, RoomSyncOutbox(database).getPending().size)
        } finally {
            database.close()
        }
    }
}
