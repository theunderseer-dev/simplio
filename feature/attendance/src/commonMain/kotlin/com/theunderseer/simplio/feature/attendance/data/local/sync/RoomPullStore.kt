package com.theunderseer.simplio.feature.attendance.data.local.sync

import androidx.room.useWriterConnection
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadStateEntity
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudHead
import com.theunderseer.simplio.feature.attendance.data.sync.AttendanceCloudSnapshot
import com.theunderseer.simplio.feature.attendance.data.sync.PullAction
import com.theunderseer.simplio.feature.attendance.data.sync.UploadPreparationFailure
import com.theunderseer.simplio.feature.attendance.data.sync.UploadProblem
import com.theunderseer.simplio.feature.attendance.data.sync.UploadTarget
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

class RoomPullStore(private val database: AttendanceDatabase, private val target: UploadTarget) {
    private val transactions = RoomTransactionRunner(database)
    private val dao = database.syncPullDao()
    private val uploads = database.syncUploadDao()

    fun observeWork(): Flow<Boolean> = uploads.observeWork()
    suspend fun retry() = uploads.retry()

    suspend fun mustRecoverBatch(): Boolean = transactions.run {
        checkTarget()
        uploads.getBatch() != null && !pristineDefaults()
    }

    suspend fun plan(head: AttendanceCloudHead): PullAction = transactions.run {
        validateHead(head)
        val state = checkTarget()
        if (state?.headUpdateTime != null && head.updateTime == null) fail(UploadProblem.CONFLICT)
        if (state != null &&
            state.headUpdateTime == head.updateTime &&
            (head.updateTime != null || state.lastPullReadTime != null)
        ) {
            checkpoint(head, state)
            return@run PullAction.CURRENT
        }
        if (head.updateTime != null && !canReplace()) fail(UploadProblem.CONFLICT)
        PullAction.DOWNLOAD
    }

    /** Recheck dirty state under the write transaction after network access. */
    suspend fun apply(snapshot: AttendanceCloudSnapshot): Boolean = transactions.run {
        val head = snapshot.head
        validateHead(head)
        val state = checkTarget()
        if (state?.headUpdateTime != null && head.updateTime == null) fail(UploadProblem.CONFLICT)
        if (head.updateTime == null) {
            if (snapshot.documents.isNotEmpty()) fail(UploadProblem.INVALID_DATA)
            checkpoint(head, state)
            return@run false
        }
        if (!canReplace()) fail(UploadProblem.CONFLICT)
        val rows = try {
            mapCloudSnapshot(snapshot, dao.records())
        } catch (error: IllegalArgumentException) {
            fail(UploadProblem.INVALID_DATA)
        } catch (error: IllegalStateException) {
            fail(UploadProblem.INVALID_DATA)
        } catch (error: NoSuchElementException) {
            fail(UploadProblem.INVALID_DATA)
        }
        // Only seed defaults can have pending work here. No user-authored batch is ever discarded.
        uploads.getBatch()?.let { uploads.deleteBatch(it.batchId) }
        dao.clearOutbox()
        dao.clearRecords()
        dao.clearAttendance()
        dao.clearPackages()
        dao.clearStudents()
        dao.clearTypes()
        dao.clearGroups()
        dao.insertGroups(rows.groups)
        dao.insertTypes(rows.types)
        dao.insertStudents(rows.students)
        dao.insertPackages(rows.packages)
        dao.insertAttendance(rows.attendance)
        dao.insertRecords(rows.records)
        reserveImportedIds(rows.records)
        checkpoint(head, state)
        true
    }

    private suspend fun canReplace(): Boolean {
        val records = dao.records()
        val clean = dao.pendingCount() == 0 &&
            uploads.getBatch() == null &&
            records.all { it.revision == it.acknowledgedRevision }
        return clean || pristineDefaults()
    }

    /** Match complete untouched templates, not just their well-known cloud IDs. */
    private suspend fun pristineDefaults(): Boolean {
        val records = dao.records()
        if (records.size != DefaultStudentGroups.ALL.size + PackagePlan.entries.size ||
            dao.pendingCount() != records.size ||
            database.studentDao().count() != 0 ||
            dao.packages().isNotEmpty()
        ) {
            return false
        }
        val state = checkTarget()
        if (state?.headUpdateTime != null) return false
        if (records.any { it.revision != 1L || it.acknowledgedRevision != 0L || it.isDeleted }) return false
        val groups = database.studentGroupDao().getAll()
        val types = database.packageTypeDao().getAll()
        if (groups.size != DefaultStudentGroups.ALL.size || types.size != PackagePlan.entries.size) return false
        val valid = records.all { record ->
            when (record.entityType) {
                SyncEntityType.STUDENT_GROUP -> DefaultStudentGroups.ALL.any { group ->
                    record.localId == group.id.toString() &&
                        record.cloudId == (if (group.id == 1) "default-group-a" else "default-group-b") &&
                        groups.any { it.id == group.id && it.name == group.name }
                }
                SyncEntityType.PACKAGE_TYPE -> PackagePlan.entries.any { plan ->
                    record.cloudId == "default-plan-${plan.name.lowercase()}" &&
                        types.any {
                            it.id.toString() == record.localId &&
                                it.name == plan.packageName &&
                                it.totalClasses == plan.totalClasses &&
                                it.validityWeeks == plan.validityWeeks &&
                                it.price == plan.price &&
                                it.maxVisitsPerAttendance == plan.maxVisitsPerAttendance
                        }
                }
                else -> false
            }
        }
        if (!valid) return false
        val pending = database.syncDao().getPending(records.size + 1)
        if (pending.any { it.operation != SyncOperation.UPSERT || it.revision != 1L }) return false
        val batch = uploads.getBatch() ?: return true
        val frozen = RoomUploadStore(database).snapshot(batch)
        return frozen.target == target &&
            frozen.headUpdateTime == null &&
            frozen.documents.map { it.change.mutationId }.toSet() == pending.map { it.mutationId }.toSet()
    }

    private suspend fun reserveImportedIds(records: List<SyncRecordEntity>) {
        val tables = mapOf(
            SyncEntityType.STUDENT_GROUP to "student_groups",
            SyncEntityType.PACKAGE_TYPE to "package_types",
            SyncEntityType.STUDENT to "students",
            SyncEntityType.STUDENT_PACKAGE to "student_packages",
        )
        database.useWriterConnection { connection ->
            tables.forEach { (type, table) ->
                val maximum = records.filter { it.entityType == type }.maxOfOrNull { it.localId.toLong() } ?: 0
                connection.usePrepared("UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = ?") { statement ->
                    statement.bindLong(1, maximum)
                    statement.bindText(2, table)
                    statement.step()
                }
                connection.usePrepared(
                    "INSERT INTO sqlite_sequence (name, seq) SELECT ?, ? " +
                        "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = ?)",
                ) { statement ->
                    statement.bindText(1, table)
                    statement.bindLong(2, maximum)
                    statement.bindText(3, table)
                    statement.step()
                }
            }
        }
    }

    private suspend fun checkTarget(): SyncUploadStateEntity? = uploads.getState().also {
        if (it != null && (it.projectId != target.projectId || it.userId != target.userId)) {
            fail(UploadProblem.TARGET_MISMATCH)
        }
    }

    private suspend fun validateHead(head: AttendanceCloudHead) {
        if (head.target != target) fail(UploadProblem.TARGET_MISMATCH)
        val read = Instant.parse(head.readTime)
        head.updateTime?.let { require(Instant.parse(it) <= read) }
        checkTarget()?.lastPullReadTime?.let {
            if (Instant.parse(it) > read) fail(UploadProblem.CONFLICT)
        }
    }

    private suspend fun checkpoint(head: AttendanceCloudHead, previous: SyncUploadStateEntity?) {
        uploads.saveState(
            (previous ?: SyncUploadStateEntity(projectId = target.projectId, userId = target.userId))
                .copy(headUpdateTime = head.updateTime, lastPullReadTime = head.readTime),
        )
    }

    private fun fail(problem: UploadProblem): Nothing = throw UploadPreparationFailure(problem)
}
