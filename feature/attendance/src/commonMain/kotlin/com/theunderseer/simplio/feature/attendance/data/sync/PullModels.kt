package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.sync.SyncEntityType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** All documents share the server read time captured with the account head. */
@Serializable
data class AttendanceCloudHead(val target: UploadTarget, val updateTime: String?, val readTime: String)

@Serializable
data class AttendanceCloudDocument(
    val type: SyncEntityType,
    val cloudId: String,
    val fields: JsonObject,
    val updateTime: String,
)

@Serializable
data class AttendanceCloudSnapshot(val head: AttendanceCloudHead, val documents: List<AttendanceCloudDocument>)

interface AttendancePullRemote {
    suspend fun readHead(): AttendanceCloudHead
    suspend fun readSnapshot(head: AttendanceCloudHead): AttendanceCloudSnapshot
}

enum class PullAction { CURRENT, DOWNLOAD }

sealed interface AttendanceSyncStatus {
    data object Idle : AttendanceSyncStatus
    data object Syncing : AttendanceSyncStatus
    data class WaitingToRetry(val attempt: Int) : AttendanceSyncStatus
    data class Paused(val problem: UploadProblem) : AttendanceSyncStatus
}
