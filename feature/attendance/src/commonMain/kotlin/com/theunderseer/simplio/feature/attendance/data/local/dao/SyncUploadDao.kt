package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadBatchEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncUploadStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncUploadDao {
    @Query("SELECT * FROM sync_upload_state WHERE id = 1")
    suspend fun getState(): SyncUploadStateEntity?

    @Upsert
    suspend fun saveState(state: SyncUploadStateEntity)

    @Query("SELECT * FROM sync_upload_batch WHERE id = 1")
    suspend fun getBatch(): SyncUploadBatchEntity?

    @Insert
    suspend fun insertBatch(batch: SyncUploadBatchEntity)

    @Query("UPDATE sync_upload_batch SET attempts = attempts + 1 WHERE batchId = :batchId")
    suspend fun recordAttempt(batchId: String)

    @Query("UPDATE sync_upload_batch SET blockedReason = :reason WHERE batchId = :batchId")
    suspend fun block(batchId: String, reason: String)

    @Query("UPDATE sync_upload_batch SET blockedReason = NULL, attempts = 0 WHERE id = 1")
    suspend fun retry()

    @Query("DELETE FROM sync_upload_batch WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox) OR EXISTS(SELECT 1 FROM sync_upload_batch)")
    fun observeWork(): Flow<Boolean>
}
