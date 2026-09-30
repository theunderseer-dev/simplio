package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "attendance",
    primaryKeys = ["packageId", "attendedOn"],
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
    val attendedOn: LocalDate,
)
