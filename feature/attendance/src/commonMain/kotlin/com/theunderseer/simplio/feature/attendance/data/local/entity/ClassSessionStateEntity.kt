package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Only explicit teacher decisions are persisted; missing rows mean unrecorded. */
@Entity(tableName = "class_session_states")
data class ClassSessionStateEntity(
    @PrimaryKey val sessionId: String,
    val scheduleId: String,
    val dateEpochDay: Long,
    val status: String,
)
