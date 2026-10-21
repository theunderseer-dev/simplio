package com.theunderseer.simplio.feature.attendance.data.local.sync

import kotlinx.serialization.Serializable

@Serializable
enum class SyncEntityType {
    STUDENT_GROUP,
    PACKAGE_TYPE,
    STUDENT,
    STUDENT_PACKAGE,
    ATTENDANCE,
    CLASS_SCHEDULE,
}

@Serializable
enum class SyncOperation { UPSERT, DELETE }
