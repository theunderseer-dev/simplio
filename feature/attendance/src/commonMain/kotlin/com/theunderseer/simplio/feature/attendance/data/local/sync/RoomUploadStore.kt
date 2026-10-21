package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadBatchEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadStateEntity
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.data.remote.FirestoreUploadProtocol
import com.theunderseer.simplio.feature.attendance.data.sync.UploadAcknowledgement
import com.theunderseer.simplio.feature.attendance.data.sync.UploadDocument
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.data.sync.UploadSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant
import kotlin.uuid.Uuid

class RoomUploadStore(private val database: AttendanceDatabase) {
    private val transactions = RoomTransactionRunner(database)
    private val uploadDao = database.syncUploadDao()

    suspend fun prepare(target: UploadTarget): SyncUploadBatchEntity? = transactions.run {
        val state = uploadDao.getState()
        if (state != null && (state.projectId != target.projectId || state.userId != target.userId)) {
            throw UploadPreparationFailure(UploadProblem.TARGET_MISMATCH)
        }
        uploadDao.getBatch()?.let { return@run it }
        val syncDao = database.syncDao()
        val pending = syncDao.getPendingCatalog(MAX_DOCUMENTS).ifEmpty {
            val key = syncDao.getNextAggregateKey() ?: return@run null
            check(key.isNotBlank())
            syncDao.getPendingAggregate(key, MAX_DOCUMENTS + 1)
        }
        if (pending.size > MAX_DOCUMENTS) throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
        val selected = pending.map { it.entityType to it.localId }.toSet()
        val documents = pending.map { change ->
            val fields = fields(change, selected)
            if (fields.toString().encodeToByteArray().size > 900_000) {
                throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
            }
            UploadDocument(change, fields)
        }
        val snapshot = UploadSnapshot(
            Uuid.random().toHexString(),
            target,
            state?.headUpdateTime,
            documents,
            protocolVersion = FirestoreUploadProtocol.CURRENT_VERSION,
        )
        val body = FirestoreUploadProtocol.commitBody(snapshot)
        if (body.encodeToByteArray().size > 8 * 1024 * 1024) {
            throw UploadPreparationFailure(UploadProblem.TOO_LARGE)
        }
        val batch = SyncUploadBatchEntity(
            batchId = snapshot.batchId,
            snapshotJson = Json.encodeToString(snapshot),
            commitBody = body,
        )
        if (state == null) {
            uploadDao.saveState(SyncUploadStateEntity(projectId = target.projectId, userId = target.userId))
        }
        uploadDao.insertBatch(batch)
        batch
    }

    fun snapshot(batch: SyncUploadBatchEntity): UploadSnapshot = Json.decodeFromString(batch.snapshotJson)

    suspend fun acknowledge(batch: SyncUploadBatchEntity, result: UploadAcknowledgement) = transactions.run {
        val current = checkNotNull(uploadDao.getBatch())
        check(current.batchId == batch.batchId)
        val snapshot = snapshot(current)
        require(result.documentUpdateTimes.size == snapshot.documents.size)
        result.documentUpdateTimes.forEach { Instant.parse(it) }
        Instant.parse(result.headUpdateTime)
        val state = checkNotNull(uploadDao.getState())
        check(state.userId == snapshot.target.userId && state.projectId == snapshot.target.projectId)
        val outbox = RoomSyncOutbox(database)
        snapshot.documents.zip(result.documentUpdateTimes).forEach { (document, version) ->
            outbox.acknowledge(document.change, version)
        }
        uploadDao.saveState(state.copy(headUpdateTime = result.headUpdateTime))
        uploadDao.deleteBatch(batch.batchId)
    }

    suspend fun recordAttempt(batchId: String) = uploadDao.recordAttempt(batchId)
    suspend fun block(batchId: String, problem: UploadProblem) = uploadDao.block(batchId, problem.name)
    suspend fun retry() = uploadDao.retry()
    fun observeWork(): Flow<Boolean> = uploadDao.observeWork()

    internal suspend fun fields(change: PendingSyncChange, selected: Set<Pair<SyncEntityType, String>>): JsonObject {
        suspend fun parent(type: SyncEntityType, id: Int): String {
            val record = checkNotNull(database.syncDao().getRecord(type, id.toString()))
            check(!record.isDeleted && (record.acknowledgedRevision > 0 || type to id.toString() in selected))
            return record.cloudId
        }
        val businessFields = if (change.operation == SyncOperation.DELETE) {
            JsonObject(emptyMap())
        } else {
            when (change.entityType) {
                SyncEntityType.STUDENT_GROUP -> {
                    val row = database.studentGroupDao().getAll().single { it.id.toString() == change.localId }
                    val scheduleIds = database.classScheduleDao().groupSchedules()
                        .filter { it.groupId == row.id }.map { it.scheduleId }.sorted()
                    // Missing refs in v1 documents mean the unchanged compatibility schedule.
                    // Explicit custom refs always have a synchronized parent in this or an earlier batch.
                    val explicit = scheduleIds != listOf(DefaultClassSchedule.ID) && scheduleIds.isNotEmpty()
                    if (explicit) {
                        scheduleIds.forEach { id ->
                            if (id != DefaultClassSchedule.ID) {
                                val record =
                                    checkNotNull(database.syncDao().getRecord(SyncEntityType.CLASS_SCHEDULE, id))
                                check(
                                    !record.isDeleted &&
                                        (
                                            record.acknowledgedRevision > 0 ||
                                                SyncEntityType.CLASS_SCHEDULE to id in selected
                                            ),
                                )
                            }
                        }
                    }
                    buildJsonObject {
                        put("name", FirestoreUploadProtocol.string(row.name))
                        if (explicit) put("scheduleIds", scheduleReferences(scheduleIds))
                    }
                }
                SyncEntityType.CLASS_SCHEDULE -> scheduleFields(database, change.localId)
                SyncEntityType.PACKAGE_TYPE -> {
                    val row = database.packageTypeDao().getAll().single { it.id.toString() == change.localId }
                    buildJsonObject {
                        put("name", FirestoreUploadProtocol.string(row.name))
                        put("totalClasses", FirestoreUploadProtocol.integer(row.totalClasses))
                        put("validityWeeks", FirestoreUploadProtocol.integer(row.validityWeeks))
                        put("price", FirestoreUploadProtocol.integer(row.price))
                        put("maxVisitsPerAttendance", FirestoreUploadProtocol.integer(row.maxVisitsPerAttendance))
                    }
                }
                SyncEntityType.STUDENT -> {
                    val row = checkNotNull(database.studentDao().getById(change.localId.toInt()))
                    val group = parent(SyncEntityType.STUDENT_GROUP, row.groupId)
                    buildJsonObject {
                        put("fullName", FirestoreUploadProtocol.string(row.fullName))
                        put("note", FirestoreUploadProtocol.string(row.note))
                        put("groupId", FirestoreUploadProtocol.string(group))
                    }
                }
                SyncEntityType.STUDENT_PACKAGE -> {
                    val row = checkNotNull(database.studentPackageDao().getById(change.localId.toInt()))
                    val student = parent(SyncEntityType.STUDENT, row.studentId)
                    val type = parent(SyncEntityType.PACKAGE_TYPE, row.packageTypeId)
                    buildJsonObject {
                        put("studentId", FirestoreUploadProtocol.string(student))
                        put("packageTypeId", FirestoreUploadProtocol.string(type))
                        put("packageName", FirestoreUploadProtocol.string(row.packageName))
                        put("totalClasses", FirestoreUploadProtocol.integer(row.totalClasses))
                        put("validityWeeks", FirestoreUploadProtocol.integer(row.validityWeeks))
                        put("price", FirestoreUploadProtocol.integer(row.price))
                        put("maxVisitsPerAttendance", FirestoreUploadProtocol.integer(row.maxVisitsPerAttendance))
                        put("purchasedOnEpochDay", FirestoreUploadProtocol.integer(row.purchasedOnEpochDay))
                    }
                }
                SyncEntityType.ATTENDANCE -> {
                    val (packageId, epochDay) = change.localId.split(':')
                    val row = database.attendanceDao().getForPackage(packageId.toInt())
                        .single { it.attendedOnEpochDay == epochDay.toLong() }
                    val packageCloudId = parent(SyncEntityType.STUDENT_PACKAGE, row.packageId)
                    buildJsonObject {
                        put("packageId", FirestoreUploadProtocol.string(packageCloudId))
                        put("attendedOnEpochDay", FirestoreUploadProtocol.integer(row.attendedOnEpochDay))
                        put("visitCount", FirestoreUploadProtocol.integer(row.visitCount))
                    }
                }
            }
        }
        return buildJsonObject {
            businessFields.forEach { (key, value) -> put(key, value) }
            put(
                "schemaVersion",
                FirestoreUploadProtocol.integer(
                    if (change.entityType == SyncEntityType.CLASS_SCHEDULE || "scheduleIds" in businessFields) 2 else 1,
                ),
            )
            put("isDeleted", FirestoreUploadProtocol.boolean(change.operation == SyncOperation.DELETE))
            put("lastMutationId", FirestoreUploadProtocol.string(change.mutationId))
        }
    }

    private companion object {
        const val MAX_DOCUMENTS = 400
    }
}
