package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate

@Entity(
    tableName = "student_packages",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("studentId"),
    ],
)
data class StudentPackageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentId: Int,
    val packageName: String,
    val totalClasses: Int,
    val validityWeeks: Int,
    val price: Int,
    val startedOn: LocalDate,
)
