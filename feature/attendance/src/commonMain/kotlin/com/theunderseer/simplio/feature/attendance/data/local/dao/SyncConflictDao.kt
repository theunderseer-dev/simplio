package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncConflictReviewEntity

@Dao
interface SyncConflictDao {
    @Insert
    suspend fun insert(review: SyncConflictReviewEntity)

    @Query("SELECT * FROM sync_conflict_reviews WHERE reviewId = :id")
    suspend fun get(id: String): SyncConflictReviewEntity?

    @Query("SELECT * FROM sync_conflict_reviews ORDER BY reviewId")
    suspend fun getAll(): List<SyncConflictReviewEntity>

    @Query("UPDATE sync_conflict_reviews SET status = 'SUPERSEDED' WHERE status = 'OPEN'")
    suspend fun supersedeOpen()

    @Query("UPDATE sync_conflict_reviews SET status = 'APPLIED', choicesJson = :choices WHERE reviewId = :id")
    suspend fun applied(id: String, choices: String)
}
