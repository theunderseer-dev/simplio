package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Keeps both versions and the old frozen request, even after a decision was applied locally. */
@Entity(tableName = "sync_conflict_reviews")
data class SyncConflictReviewEntity(
    @PrimaryKey val reviewId: String,
    val projectId: String,
    val userId: String,
    val localJson: String,
    val cloudJson: String,
    val status: String,
    val choicesJson: String? = null,
)
