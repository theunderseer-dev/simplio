package com.theunderseer.simplio.feature.attendance.data.local.sync

enum class SyncEntityType {
    STUDENT_GROUP,
    PACKAGE_TYPE,
    STUDENT,
    STUDENT_PACKAGE,
    ATTENDANCE,
}

enum class SyncOperation { UPSERT, DELETE }
