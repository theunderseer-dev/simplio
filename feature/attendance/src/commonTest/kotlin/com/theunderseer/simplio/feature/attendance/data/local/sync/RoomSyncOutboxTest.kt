package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentPackageRepository
import com.theunderseer.simplio.feature.attendance.data.local.repository.RoomStudentRepository
import com.theunderseer.simplio.feature.attendance.domain.model.InitialAttendance
import com.theunderseer.simplio.feature.attendance.fixtures.AttendanceTestData
import com.theunderseer.simplio.feature.attendance.fixtures.registerSyncStudent
import com.theunderseer.simplio.feature.attendance.fixtures.seedSyncDefaults
import com.theunderseer.simplio.feature.attendance.fixtures.testSyncDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomSyncOutboxTest {
    private lateinit var database: AttendanceDatabase
    private lateinit var outbox: RoomSyncOutbox

    @BeforeTest
    fun setUp() {
        database = testSyncDatabase()
        outbox = RoomSyncOutbox(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `registration queues identities for all related rows`() = runTest {
        val student = database.registerSyncStudent()
        val pending = outbox.getPending()

        assertEquals(8, pending.size)
        assertEquals(SyncEntityType.entries.toSet(), pending.map { it.entityType }.toSet())
        assertEquals(8, pending.map { it.mutationId }.toSet().size)
        assertTrue(pending.all { it.operation == SyncOperation.UPSERT })
        val parent = pending.single { it.entityType == SyncEntityType.STUDENT_PACKAGE }
        val attendance = pending.single { it.entityType == SyncEntityType.ATTENDANCE }
        assertEquals(
            "${student.studentPackage.id}:${AttendanceTestData.firstClassDate.toEpochDays()}",
            attendance.localId,
        )
        assertEquals("${parent.cloudId}_${AttendanceTestData.firstClassDate.toEpochDays()}", attendance.cloudId)
    }

    @Test
    fun `registration without activation queues no attendance`() = runTest {
        val registered = database.registerSyncStudent(recordFirstAttendance = false)

        assertEquals(7, outbox.getPending().size)
        assertTrue(outbox.getPending().none { it.entityType == SyncEntityType.ATTENDANCE })
        assertNull(registered.studentPackage.startedOn)
    }

    @Test
    fun `note changes coalesce and keep the same cloud identity`() = runTest {
        val id = database.registerSyncStudent().student.id
        val original = studentChange()
        val repository = RoomStudentRepository(database)

        repository.updateNote(id, "First note")
        repository.updateNote(id, "Second note")
        val latest = studentChange()

        assertEquals(8, outbox.getPending().size)
        assertEquals(original.cloudId, latest.cloudId)
        assertEquals(original.revision + 2, latest.revision)
        assertNotEquals(original.mutationId, latest.mutationId)
        assertEquals(latest, studentChange())
        assertEquals("Second note", repository.getAll().single().note)
    }

    @Test
    fun `unchanged note missing student and missing attendance do not enqueue changes`() = runTest {
        val registered = database.registerSyncStudent()
        val original = outbox.getPending()
        val students = RoomStudentRepository(database)

        assertTrue(students.updateNote(registered.student.id, ""))
        assertFalse(students.updateNote(999, "Unknown"))
        students.delete(999)
        RoomStudentPackageRepository(database).removeAttendance(
            registered.studentPackage.id,
            AttendanceTestData.secondClassDate,
        )

        assertEquals(original, outbox.getPending())
    }

    @Test
    fun `duplicate attendance preserves visit count and pending mutation`() = runTest {
        val registered = database.registerSyncStudent()
        val original = outbox.getPending()
        val packages = RoomStudentPackageRepository(database)

        packages.recordAttendance(registered.studentPackage.id, AttendanceTestData.firstClassDate, 2)

        assertEquals(original, outbox.getPending())
        assertEquals(1, packages.getLatestPackageForStudent(registered.student.id)?.usedClasses)
    }

    @Test
    fun `old acknowledgement keeps newer edit and advances its server base`() = runTest {
        val id = database.registerSyncStudent().student.id
        val sent = studentChange()
        RoomStudentRepository(database).updateNote(id, "Edited while sending")
        val latest = studentChange()

        outbox.acknowledge(sent, "server-version-1")

        val pending = studentChange()
        assertEquals(latest.mutationId, pending.mutationId)
        assertEquals(latest.revision, pending.revision)
        assertEquals(sent.revision, pending.acknowledgedRevision)
        assertEquals("server-version-1", pending.serverUpdateTime)
    }

    @Test
    fun `late acknowledgements do not regress the server base`() = runTest {
        val id = database.registerSyncStudent().student.id
        val first = studentChange()
        RoomStudentRepository(database).updateNote(id, "Second")
        val second = studentChange()
        outbox.acknowledge(second, "server-version-2")
        outbox.acknowledge(first, "server-version-1")
        outbox.acknowledge(second, "server-version-2")

        val record = assertNotNull(database.syncDao().getRecord(SyncEntityType.STUDENT, id.toString()))
        assertEquals(second.revision, record.acknowledgedRevision)
        assertEquals("server-version-2", record.serverUpdateTime)
        assertTrue(outbox.getPending().none { it.entityType == SyncEntityType.STUDENT })
    }

    @Test
    fun `deleting student retains tombstones for cascaded packages and attendance`() = runTest {
        val registered = database.registerSyncStudent()
        RoomStudentPackageRepository(database).create(
            studentId = registered.student.id,
            packageType = registered.studentPackage.packageType,
            purchasedOn = AttendanceTestData.secondClassDate,
            initialAttendance = InitialAttendance(AttendanceTestData.secondClassDate, 1),
        )
        val originals = outbox.getPending().associateBy { it.entityType to it.localId }

        RoomStudentRepository(database).delete(registered.student.id)

        assertTrue(database.studentDao().getAll().isEmpty())
        assertTrue(database.studentPackageDao().getForStudent(registered.student.id).isEmpty())
        assertTrue(database.attendanceDao().getForPackage(registered.studentPackage.id).isEmpty())
        val deletions = outbox.getPending().filter { it.operation == SyncOperation.DELETE }
        assertEquals(5, deletions.size)
        assertEquals(
            setOf(SyncEntityType.STUDENT, SyncEntityType.STUDENT_PACKAGE, SyncEntityType.ATTENDANCE),
            deletions.map { it.entityType }.toSet(),
        )
        for (deletion in deletions) {
            assertEquals(originals.getValue(deletion.entityType to deletion.localId).cloudId, deletion.cloudId)
            outbox.acknowledge(deletion, "deleted-version")
            assertTrue(assertNotNull(database.syncDao().getRecord(deletion.entityType, deletion.localId)).isDeleted)
        }
        assertEquals(5, outbox.getPending().size)
    }

    @Test
    fun `removing and recording first attendance reuses its cloud id without changing package revision`() = runTest {
        val registered = database.registerSyncStudent()
        val packages = RoomStudentPackageRepository(database)
        val original = outbox.getPending().single { it.entityType == SyncEntityType.ATTENDANCE }
        val parent = outbox.getPending().single { it.entityType == SyncEntityType.STUDENT_PACKAGE }

        packages.removeAttendance(registered.studentPackage.id, AttendanceTestData.firstClassDate)
        assertNull(packages.getLatestPackageForStudent(registered.student.id)?.startedOn)
        val removed = outbox.getPending().single { it.entityType == SyncEntityType.ATTENDANCE }
        assertEquals(SyncOperation.DELETE, removed.operation)
        assertEquals(original.cloudId, removed.cloudId)

        packages.recordAttendance(registered.studentPackage.id, AttendanceTestData.firstClassDate, 1)
        val restored = outbox.getPending().single { it.entityType == SyncEntityType.ATTENDANCE }
        assertEquals(SyncOperation.UPSERT, restored.operation)
        assertEquals(original.cloudId, restored.cloudId)
        assertEquals(removed.revision + 1, restored.revision)
        assertEquals(parent, outbox.getPending().single { it.entityType == SyncEntityType.STUDENT_PACKAGE })
        assertEquals(
            AttendanceTestData.firstClassDate,
            packages.getLatestPackageForStudent(registered.student.id)?.startedOn,
        )
    }

    @Test
    fun `default identities match on two devices and repeated seeding is unchanged`() = runTest {
        database.seedSyncDefaults()
        val original = outbox.getPending()
        database.seedSyncDefaults()
        assertEquals(original, outbox.getPending())

        val secondDatabase = testSyncDatabase()
        try {
            secondDatabase.seedSyncDefaults()
            val second = RoomSyncOutbox(secondDatabase).getPending()
            assertEquals(original.map { it.cloudId }.toSet(), second.map { it.cloudId }.toSet())
            assertEquals(5, second.size)
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun `independent students receive different cloud ids despite identical local ids`() = runTest {
        database.registerSyncStudent()
        val first = studentChange()
        val secondDatabase = testSyncDatabase()
        try {
            secondDatabase.registerSyncStudent()
            val second = RoomSyncOutbox(secondDatabase).getPending().single { it.entityType == SyncEntityType.STUDENT }
            assertEquals(first.localId, second.localId)
            assertNotEquals(first.cloudId, second.cloudId)
        } finally {
            secondDatabase.close()
        }
    }

    @Test
    fun `pending count flow observes enqueue and acknowledgement`() = runTest {
        assertEquals(0, outbox.observePendingCount().first())
        val waiting = async { outbox.observePendingCount().first { it == 8 } }
        database.registerSyncStudent()
        assertEquals(8, waiting.await())
        for (change in outbox.getPending()) outbox.acknowledge(change, "committed")
        assertEquals(0, outbox.observePendingCount().first())
    }

    @Test
    fun `concurrent local edits serialize revisions without losing the latest pending state`() = runTest {
        val id = database.registerSyncStudent().student.id
        val original = studentChange()
        val repository = RoomStudentRepository(database)

        (1..10).map { index -> async { repository.updateNote(id, "Note $index") } }.awaitAll()

        val latest = studentChange()
        assertEquals(original.revision + 10, latest.revision)
        assertEquals(original.cloudId, latest.cloudId)
        assertEquals(8, outbox.getPending().size)
        assertTrue(repository.getAll().single().note.startsWith("Note "))
    }

    private suspend fun studentChange(): PendingSyncChange =
        outbox.getPending().single { it.entityType == SyncEntityType.STUDENT }
}
