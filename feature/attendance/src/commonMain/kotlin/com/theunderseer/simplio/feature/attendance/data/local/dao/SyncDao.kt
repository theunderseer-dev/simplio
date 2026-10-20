package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncOutboxEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.sync.PendingSyncChange
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_records WHERE entityType = :entityType AND localId = :localId")
    suspend fun getRecord(entityType: SyncEntityType, localId: String): SyncRecordEntity?

    @Upsert
    suspend fun saveRecord(record: SyncRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(change: SyncOutboxEntity)

    @Query(
        """
        SELECT pending.queueId, pending.entityType, pending.localId, pending.revision,
            pending.operation, pending.mutationId, record.cloudId,
            record.acknowledgedRevision, record.serverUpdateTime, record.aggregateKey
        FROM sync_outbox AS pending
        INNER JOIN sync_records AS record
            ON record.entityType = pending.entityType AND record.localId = pending.localId
        ORDER BY pending.queueId
        LIMIT :limit
        """,
    )
    suspend fun getPending(limit: Int): List<PendingSyncChange>

    @Query(
        """
        SELECT pending.queueId, pending.entityType, pending.localId, pending.revision,
            pending.operation, pending.mutationId, record.cloudId,
            record.acknowledgedRevision, record.serverUpdateTime, record.aggregateKey
        FROM sync_outbox AS pending
        INNER JOIN sync_records AS record
            ON record.entityType = pending.entityType AND record.localId = pending.localId
        WHERE pending.entityType IN ('STUDENT_GROUP', 'PACKAGE_TYPE')
        ORDER BY pending.queueId
        LIMIT :limit
        """,
    )
    suspend fun getPendingCatalog(limit: Int): List<PendingSyncChange>

    @Query(
        """
        SELECT pending.queueId, pending.entityType, pending.localId, pending.revision,
            pending.operation, pending.mutationId, record.cloudId,
            record.acknowledgedRevision, record.serverUpdateTime, record.aggregateKey
        FROM sync_outbox AS pending
        INNER JOIN sync_records AS record
            ON record.entityType = pending.entityType AND record.localId = pending.localId
        WHERE record.aggregateKey = :aggregateKey
        ORDER BY pending.queueId
        LIMIT :limit
        """,
    )
    suspend fun getPendingAggregate(aggregateKey: String, limit: Int): List<PendingSyncChange>

    @Query(
        """
        SELECT record.aggregateKey FROM sync_outbox AS pending
        INNER JOIN sync_records AS record
            ON record.entityType = pending.entityType AND record.localId = pending.localId
        ORDER BY pending.queueId LIMIT 1
        """,
    )
    suspend fun getNextAggregateKey(): String?

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query(
        """
        DELETE FROM sync_outbox
        WHERE queueId = :queueId AND mutationId = :mutationId AND revision = :revision
        """,
    )
    suspend fun deleteAcknowledged(queueId: Long, mutationId: String, revision: Long)
}
