package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.data.local.transaction.RoomTransactionRunner
import kotlinx.coroutines.flow.Flow

class RoomSyncOutbox(private val database: AttendanceDatabase) {
    suspend fun getPending(limit: Int = 50): List<PendingSyncChange> {
        require(limit > 0)
        return database.syncDao().getPending(limit)
    }

    fun observePendingCount(): Flow<Int> = database.syncDao().observePendingCount()

    /** Call only after the server has committed this exact mutation. */
    suspend fun acknowledge(change: PendingSyncChange, serverUpdateTime: String) {
        require(serverUpdateTime.isNotBlank())
        RoomTransactionRunner(database).run {
            val dao = database.syncDao()
            val record = dao.getRecord(change.entityType, change.localId) ?: return@run
            require(record.cloudId == change.cloudId && change.revision <= record.revision)
            if (change.revision > record.acknowledgedRevision) {
                dao.saveRecord(
                    record.copy(
                        acknowledgedRevision = change.revision,
                        serverUpdateTime = serverUpdateTime,
                    ),
                )
            }
            dao.deleteAcknowledged(change.queueId, change.mutationId, change.revision)
        }
    }
}
