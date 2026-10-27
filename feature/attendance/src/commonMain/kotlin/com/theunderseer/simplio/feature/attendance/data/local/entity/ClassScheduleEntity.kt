package com.theunderseer.simplio.feature.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "class_schedules")
data class ClassScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val timeZoneId: String,
    val startsOnEpochDay: Long?,
    val endsOnEpochDay: Long?,
    val archivedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "class_schedule_slots",
    primaryKeys = ["scheduleId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = ClassScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ClassScheduleSlotEntity(
    val scheduleId: String,
    val id: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(
    tableName = "student_group_schedules",
    primaryKeys = ["groupId", "scheduleId"],
    foreignKeys = [
        ForeignKey(
            entity = StudentGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClassScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId")],
)
data class StudentGroupScheduleEntity(val groupId: Int, val scheduleId: String)
