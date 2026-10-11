package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "student_groups")
data class StudentGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
)
