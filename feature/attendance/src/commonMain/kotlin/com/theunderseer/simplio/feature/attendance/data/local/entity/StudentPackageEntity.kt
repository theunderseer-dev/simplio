package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.theunderseer.simplio.feature.attendance.data.local.mapper.PackageValidityExtensionsConverter

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
    val validityWeeks: Int?,
    val price: Int,
    val purchasedOnEpochDay: Long,
    val startedOnEpochDay: Long? = null,

    @ColumnInfo(defaultValue = "1")
    val maxVisitsPerAttendance: Int = 1,

    @ColumnInfo(defaultValue = "'{}'")
    @TypeConverters(PackageValidityExtensionsConverter::class)
    val validityExtensions: Map<String, Int> = emptyMap(),
)
