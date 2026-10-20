package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudHead
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.AttendancePullRemote
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceUploadRemote
import com.theunderseer.simplio.feature.attendance.data.sync.UploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import kotlin.time.Instant

internal class FakeAttendanceCloud(val target: UploadTarget = testUploadTarget) :
    AttendancePullRemote,
    AttendanceUploadRemote {
    private val documents = mutableMapOf<Pair<SyncEntityType, String>, AttendanceCloudDocument>()
    private val reads = mutableMapOf<String, AttendanceCloudSnapshot>()
    private var clock = 0L
    private var version: String? = null
    var uploads = 0
        private set
    var downloads = 0
        private set
    var headReads = 0
        private set

    override suspend fun readHead(): AttendanceCloudHead {
        headReads++
        val head = AttendanceCloudHead(target, version, tick())
        reads[head.readTime] = AttendanceCloudSnapshot(head, documents.values.toList())
        return head
    }

    override suspend fun readSnapshot(head: AttendanceCloudHead): AttendanceCloudSnapshot {
        downloads++
        return checkNotNull(reads[head.readTime])
    }

    override suspend fun sendOrRecover(snapshot: UploadSnapshot, commitBody: String): UploadAcknowledgement {
        check(snapshot.target == target)
        if (snapshot.headUpdateTime != version ||
            snapshot.documents.any {
                documents[it.change.entityType to it.change.cloudId]?.updateTime != it.change.serverUpdateTime
            }
        ) {
            throw FirestoreFailure(FirestoreFailureKind.CONFLICT)
        }
        val next = tick()
        snapshot.documents.forEach {
            documents[it.change.entityType to it.change.cloudId] = AttendanceCloudDocument(
                it.change.entityType,
                it.change.cloudId,
                it.fields,
                next,
            )
        }
        uploads++
        version = next
        return UploadAcknowledgement(List(snapshot.documents.size) { next }, next)
    }

    suspend fun uploadPending(database: AttendanceDatabase) {
        val store = RoomUploadStore(database)
        while (true) {
            val batch = store.prepare(target) ?: return
            val snapshot = store.snapshot(batch)
            store.acknowledge(batch, sendOrRecover(snapshot, batch.commitBody))
        }
    }

    suspend fun snapshot(): AttendanceCloudSnapshot = readSnapshot(readHead())

    private fun tick(): String = Instant.fromEpochSeconds(1_792_454_400, ++clock * 1_000).toString()
}
