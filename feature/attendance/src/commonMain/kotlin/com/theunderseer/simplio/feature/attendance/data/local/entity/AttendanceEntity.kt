package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "attendance",
    primaryKeys = ["packageId", "attendedOnEpochDay"],
    foreignKeys = [
        ForeignKey(
            entity = StudentPackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["packageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("packageId"),
    ],
)
data class AttendanceEntity(
    val packageId: Int,
    val attendedOnEpochDay: Long,
    @ColumnInfo(defaultValue = "1")
    val visitCount: Int = 1,
    val sessionId: String? = null,
)
