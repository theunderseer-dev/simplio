package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_upload_batch")
data class SyncUploadBatchEntity(
    @PrimaryKey val id: Int = 1,
    val batchId: String,
    val snapshotJson: String,
    val commitBody: String,
    val attempts: Int = 0,
    val blockedReason: String? = null,
)
