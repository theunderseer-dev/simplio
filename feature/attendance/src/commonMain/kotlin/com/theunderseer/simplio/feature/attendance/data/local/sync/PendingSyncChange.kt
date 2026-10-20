package com.theunderseer.simplio.feature.attendance.data.local.sync

import kotlinx.serialization.Serializable

/** A version of local state awaiting upload, not a network payload. */
@Serializable
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
    val aggregateKey: String = "",
)
