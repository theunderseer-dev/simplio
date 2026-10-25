package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity
import com.theunderseer.simplio.feature.attendance.data.local.sync.PendingSyncChange
import kotlinx.serialization.Serializable

@Serializable
internal data class ConflictLocalSnapshot(
    val target: UploadTarget,
    val headUpdateTime: String?,
    val records: List<SyncRecordEntity>,
    val pending: List<PendingSyncChange>,
    val documents: List<AttendanceCloudDocument>,
    val frozenBatchId: String?,
    val frozenSnapshotJson: String?,
    val frozenCommitBody: String?,
)

@Serializable
enum class ConflictChoice { DEVICE, CLOUD }

/** A complete student/package/attendance aggregate or one catalog entry. */
data class SyncConflictItem(
    val key: String,
    val label: String,
    val deviceDetails: List<String>,
    val cloudDetails: List<String>,
    val requiresGroupRename: Boolean = false,
    val unavailableGroupNames: List<String> = emptyList(),
    val requiresPackageRename: Boolean = false,
    val unavailablePackageNames: List<String> = emptyList(),
) {
    val requiresRename: Boolean get() = requiresGroupRename || requiresPackageRename
    val unavailableNames: List<String>
        get() = if (requiresPackageRename) unavailablePackageNames else unavailableGroupNames
}

data class SyncConflictReview(val id: String, val items: List<SyncConflictItem>, val localGroupCount: Int)

sealed interface ConflictReviewState {
    data object Closed : ConflictReviewState
    data object Loading : ConflictReviewState
    data class Ready(val review: SyncConflictReview, val message: String? = null, val isApplying: Boolean = false) :
        ConflictReviewState
    data class Error(val message: String) : ConflictReviewState
}

class StaleConflictReview : Exception("Data changed while reviewing")
class InvalidConflictChoice : Exception("Selected versions do not form valid attendance data")
