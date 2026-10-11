package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = StudentGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
        ),
    ],
    indices = [
        Index("groupId"),
    ],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fullName: String,
    val groupId: Int,
)
