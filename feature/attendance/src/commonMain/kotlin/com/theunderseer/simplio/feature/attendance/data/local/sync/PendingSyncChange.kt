package com.theunderseer.simplio.feature.attendance.data.local.sync

/** A version of local state awaiting upload, not a network payload. */
data class PendingSyncChange(
    val queueId: Long,
    val entityType: SyncEntityType,
    val localId: String,
    val cloudId: String,
    val revision: Long,
    val acknowledgedRevision: Long,
    val operation: SyncOperation,
    val mutationId: String,
    val serverUpdateTime: String?,
)
