package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_upload_state")
data class SyncUploadStateEntity(
    @PrimaryKey val id: Int = 1,
    val projectId: String,
    val userId: String,
    val headUpdateTime: String? = null,
)
