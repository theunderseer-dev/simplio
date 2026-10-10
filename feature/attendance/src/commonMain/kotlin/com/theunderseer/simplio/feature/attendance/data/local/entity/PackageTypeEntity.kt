package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "package_types")
data class PackageTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val totalClasses: Int,
    val validityWeeks: Int,
    val price: Int,

    @ColumnInfo(defaultValue = "1")
    val maxVisitsPerAttendance: Int = 1,
)
