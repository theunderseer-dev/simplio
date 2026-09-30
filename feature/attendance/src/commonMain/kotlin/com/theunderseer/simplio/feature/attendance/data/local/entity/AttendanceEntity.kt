package com.theunderseer.simplio.feature.attendance.data.local.entity

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
)
