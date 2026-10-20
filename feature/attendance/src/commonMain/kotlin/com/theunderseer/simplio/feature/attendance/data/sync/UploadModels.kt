package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.sync.PendingSyncChange
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UploadTarget(val projectId: String, val userId: String) {
    init {
        require(projectId.matches(Regex("[a-zA-Z0-9-]+")))
        require(userId.isNotBlank() && '/' !in userId && userId != "." && userId != "..")
    }
}

@Serializable
data class UploadDocument(val change: PendingSyncChange, val fields: JsonObject)

@Serializable
data class UploadSnapshot(
    val batchId: String,
    val target: UploadTarget,
    val headUpdateTime: String?,
    val documents: List<UploadDocument>,
)

data class UploadAcknowledgement(val documentUpdateTimes: List<String>, val headUpdateTime: String)

fun interface AttendanceUploadRemote {
    suspend fun sendOrRecover(snapshot: UploadSnapshot, commitBody: String): UploadAcknowledgement
}

enum class UploadProblem {
    AUTHENTICATION,
    PERMISSION,
    CONFLICT,
    INVALID_DATA,
    TOO_LARGE,
    TARGET_MISMATCH,
    SERVICE,
}

sealed interface AttendanceUploadStatus {
    data object Idle : AttendanceUploadStatus
    data object Uploading : AttendanceUploadStatus
    data class WaitingToRetry(val attempt: Int) : AttendanceUploadStatus
    data class Paused(val problem: UploadProblem) : AttendanceUploadStatus
}

class UploadPreparationFailure(val problem: UploadProblem) : Exception("Upload preparation failed: $problem")
