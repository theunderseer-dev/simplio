package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncOperation

@Entity(
    tableName = "sync_outbox",
    foreignKeys = [
        ForeignKey(
            entity = SyncRecordEntity::class,
            parentColumns = ["entityType", "localId"],
            childColumns = ["entityType", "localId"],
        ),
    ],
    indices = [
        Index(value = ["entityType", "localId"], unique = true),
        Index(value = ["mutationId"], unique = true),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val queueId: Long = 0,
    val entityType: SyncEntityType,
    val localId: String,
    val revision: Long,
    val operation: SyncOperation,
    val mutationId: String,
)
