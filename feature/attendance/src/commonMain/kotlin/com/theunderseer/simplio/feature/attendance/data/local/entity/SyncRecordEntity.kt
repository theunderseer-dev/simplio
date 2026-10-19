package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType

@Entity(
    tableName = "sync_records",
    primaryKeys = ["entityType", "localId"],
    indices = [Index(value = ["entityType", "cloudId"], unique = true)],
)
data class SyncRecordEntity(
    val entityType: SyncEntityType,
    val localId: String,
    val cloudId: String,
    val revision: Long,
    val acknowledgedRevision: Long = 0,
    val isDeleted: Boolean = false,
    val serverUpdateTime: String? = null,
)
