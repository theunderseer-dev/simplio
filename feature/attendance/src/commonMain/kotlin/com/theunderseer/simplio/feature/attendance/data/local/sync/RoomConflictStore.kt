package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncConflictReviewEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncOutboxEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadStateEntity
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudDocument
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudHead
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictLocalSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.InvalidConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.StaleConflictReview
import com.theunderseer.simplio.feature.attendance.data.sync.SyncConflictReview
import com.theunderseer.simplio.feature.attendance.data.sync.UploadDocument
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomConflictStore(private val database: AttendanceDatabase, private val target: UploadTarget) {
    private val transactions = RoomTransactionRunner(database)
    private val reviews = database.syncConflictDao()

    suspend fun create(snapshot: AttendanceCloudSnapshot): SyncConflictReview = transactions.run {
        validateHead(snapshot.head)
        val local = capture()
        // A different immutable head fences off an old prepared request. It cannot commit later.
        local.frozenSnapshotJson?.let {
            val frozen = Json.decodeFromString<UploadSnapshot>(it)
            if (frozen.target != target) throw UploadPreparationFailure(UploadProblem.TARGET_MISMATCH)
            if (frozen.headUpdateTime == snapshot.head.updateTime) {
                throw UploadPreparationFailure(UploadProblem.CONFLICT)
            }
        }
        validateGraph(snapshot, local.records)
        if (local.documents.isNotEmpty()) validateGraph(snapshot.copy(documents = local.documents), emptyList())
        val localJson = Json.encodeToString(local)
        val cloudJson = Json.encodeToString(snapshot)
        val row = SyncConflictReviewEntity(
            Uuid.random().toHexString(),
            target.projectId,
            target.userId,
            localJson,
            cloudJson,
            status = "OPEN",
        )
        reviews.supersedeOpen()
        reviews.insert(row)
        val plan = ConflictPlan(local, snapshot)
        SyncConflictReview(row.reviewId, plan.items(), plan.pendingGroups.size)
    }

    /** The coordinator has re-read this head online; upload CAS still protects a later remote write. */
    suspend fun apply(
        id: String,
        choices: Map<String, ConflictChoice>,
        currentHead: AttendanceCloudHead,
        catalogRenames: Map<String, String> = emptyMap(),
    ) = transactions.run {
        validateHead(currentHead)
        val row = checkNotNull(reviews.get(id))
        if (row.status != "OPEN" || row.projectId != target.projectId || row.userId != target.userId) {
            throw StaleConflictReview()
        }
        val local = Json.decodeFromString<ConflictLocalSnapshot>(row.localJson)
        val cloud = Json.decodeFromString<AttendanceCloudSnapshot>(row.cloudJson)
        if (Json.encodeToString(capture()) != row.localJson || cloud.head.updateTime != currentHead.updateTime) {
            throw StaleConflictReview()
        }
        val plan = ConflictPlan(local, cloud)
        if (choices.keys != plan.conflictingKeys) throw InvalidConflictChoice()
        val merged = try {
            plan.merge(choices, catalogRenames).copy(head = currentHead)
        } catch (error: IllegalArgumentException) {
            throw InvalidConflictChoice()
        }
        if (merged.documents.size > 50_000 ||
            merged.documents.sumOf {
                it.fields.toString().encodeToByteArray().size.toLong()
            } > 32L * 1024 * 1024
        ) {
            throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
        }
        val rows = try {
            mapCloudSnapshot(merged, local.records)
        } catch (error: IllegalArgumentException) {
            throw InvalidConflictChoice()
        } catch (error: IllegalStateException) {
            throw InvalidConflictChoice()
        } catch (error: NoSuchElementException) {
            throw InvalidConflictChoice()
        }
        val server = cloud.documents.associateBy { it.key }
        val desired = merged.documents.associateBy { it.key }
        val changed = merged.documents.filter { it.content() != server[it.key]?.content() }.associateBy { it.key }
        val recordKeys = rows.records.associateBy { it.entityType to it.cloudId }
        val finalRecords = rows.records.map { record ->
            val key = record.entityType to record.cloudId
            val group = plan.groupFor(key)
            val aggregate = if (group.startsWith("student:")) {
                val student = checkNotNull(recordKeys[SyncEntityType.STUDENT to group.substringAfter(':')])
                "STUDENT:${student.localId}"
            } else {
                record.aggregateKey
            }
            if (key in changed) {
                val revision = record.revision + 1
                check(revision > 0)
                record.copy(
                    revision = revision,
                    acknowledgedRevision = if (key in server) revision - 1 else 0,
                    serverUpdateTime = server[key]?.updateTime,
                    aggregateKey = aggregate,
                )
            } else {
                record.copy(serverUpdateTime = server[key]?.updateTime, aggregateKey = aggregate)
            }
        }
        val pending = finalRecords.filter { it.entityType to it.cloudId in changed }.map { record ->
            PendingSyncChange(
                0, record.entityType, record.localId, record.cloudId, record.revision,
                record.acknowledgedRevision, if (record.isDeleted) SyncOperation.DELETE else SyncOperation.UPSERT,
                Uuid.random().toHexString(), record.serverUpdateTime, record.aggregateKey,
            )
        }
        // Validate complete upload units before replacing any data or abandoning the old frozen request.
        pending.groupBy { it.aggregateKey }.values.forEach { group ->
            if (group.size > 400) throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
            val docs = group.map { change ->
                val document = checkNotNull(desired[change.entityType to change.cloudId])
                val fields = buildJsonObject {
                    document.fields.forEach { (key, value) -> put(key, value) }
                    put("lastMutationId", FirestoreUploadProtocol.string(change.mutationId))
                }
                if (fields.toString().encodeToByteArray().size > 900_000) {
                    throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
                }
                UploadDocument(change, fields)
            }
            val body = FirestoreUploadProtocol.commitBody(
                UploadSnapshot(
                    "review-size-check",
                    target,
                    currentHead.updateTime,
                    docs,
                ),
            )
            if (body.encodeToByteArray().size > 8 * 1024 * 1024) {
                throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
            }
        }
        RoomPullStore(database, target).replaceRows(rows.copy(records = finalRecords))
        pending.forEach {
            database.syncDao().enqueue(
                SyncOutboxEntity(
                    entityType = it.entityType,
                    localId = it.localId,
                    revision = it.revision,
                    operation = it.operation,
                    mutationId = it.mutationId,
                ),
            )
        }
        val state = database.syncUploadDao().getState() ?: SyncUploadStateEntity(
            projectId = target.projectId,
            userId = target.userId,
        )
        database.syncUploadDao().saveState(
            state.copy(
                headUpdateTime = currentHead.updateTime,
                lastPullReadTime = currentHead.readTime,
            ),
        )
        val resolution = buildJsonObject {
            put("choices", Json.encodeToJsonElement(choices.toList().sortedBy { it.first }.toMap()))
            put("groupRenames", Json.encodeToJsonElement(catalogRenames.toList().sortedBy { it.first }.toMap()))
        }
        reviews.applied(id, resolution.toString())
    }

    private suspend fun capture(): ConflictLocalSnapshot {
        val state = database.syncUploadDao().getState()
        if (state != null && (state.projectId != target.projectId || state.userId != target.userId)) {
            throw UploadPreparationFailure(UploadProblem.TARGET_MISMATCH)
        }
        val records = database.syncPullDao().records().sortedWith(
            compareBy<SyncRecordEntity> { it.entityType.ordinal }
                .thenBy { it.localId },
        )
        if (records.size > 50_000) throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
        val pending = database.syncDao().getPending(50_001)
        val queued = pending.associateBy { it.entityType to it.localId }
        val selected = records.map { it.entityType to it.localId }.toSet()
        val uploader = RoomUploadStore(database)
        val documents = records.map { record ->
            val change = queued[record.entityType to record.localId] ?: PendingSyncChange(
                0, record.entityType,
                record.localId, record.cloudId, record.revision, record.acknowledgedRevision,
                if (record.isDeleted) SyncOperation.DELETE else SyncOperation.UPSERT, "local-baseline",
                record.serverUpdateTime, record.aggregateKey,
            )
            AttendanceCloudDocument(
                record.entityType,
                record.cloudId,
                uploader.fields(change, selected),
                record.serverUpdateTime ?: "1970-01-01T00:00:00Z",
            )
        }
        if (documents.sumOf { it.fields.toString().encodeToByteArray().size.toLong() } > 32L * 1024 * 1024) {
            throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
        }
        val frozen = database.syncUploadDao().getBatch()
        return ConflictLocalSnapshot(
            target,
            state?.headUpdateTime,
            records,
            pending,
            documents,
            frozen?.batchId,
            frozen?.snapshotJson,
            frozen?.commitBody,
        )
    }

    private fun validateGraph(snapshot: AttendanceCloudSnapshot, records: List<SyncRecordEntity>) {
        try {
            mapCloudSnapshot(snapshot, records)
        } catch (error: IllegalArgumentException) {
            throw UploadPreparationFailure(UploadProblem.INVALID_DATA)
        } catch (error: IllegalStateException) {
            throw UploadPreparationFailure(UploadProblem.INVALID_DATA)
        } catch (error: NoSuchElementException) {
            throw UploadPreparationFailure(UploadProblem.INVALID_DATA)
        }
    }

    private suspend fun validateHead(head: AttendanceCloudHead) {
        if (head.target != target) throw UploadPreparationFailure(UploadProblem.TARGET_MISMATCH)
        if (head.updateTime == null) throw UploadPreparationFailure(UploadProblem.CONFLICT)
        require(Instant.parse(checkNotNull(head.updateTime)) <= Instant.parse(head.readTime))
        database.syncUploadDao().getState()?.lastPullReadTime?.let {
            if (Instant.parse(it) > Instant.parse(head.readTime)) throw StaleConflictReview()
        }
    }
}
