package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName

@Entity(tableName = "student_groups", indices = [Index(value = ["nameKey"], unique = true)])
data class StudentGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''")
    val nameKey: String = normalizedGroupName(name),
)
