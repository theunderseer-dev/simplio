package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "student_packages",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PackageTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["packageTypeId"],
        ),
    ],
    indices = [
        Index("studentId"),
        Index("packageTypeId"),
    ],
)
data class StudentPackageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentId: Int,
    val packageTypeId: Int,
    // snapshot of purchase terms
    val packageName: String,
    val totalClasses: Int,
    val validityWeeks: Int,
    val price: Int,
    val startedOnEpochDay: Long,
)
