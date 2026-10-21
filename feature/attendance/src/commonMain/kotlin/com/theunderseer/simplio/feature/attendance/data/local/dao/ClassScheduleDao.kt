package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassScheduleEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassScheduleSlotEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupScheduleEntity

@Dao
interface ClassScheduleDao {
    @Query("SELECT * FROM class_schedules ORDER BY id")
    suspend fun schedules(): List<ClassScheduleEntity>

    @Query("SELECT * FROM class_schedule_slots ORDER BY scheduleId, dayOfWeek, startMinute, id")
    suspend fun slots(): List<ClassScheduleSlotEntity>

    @Query("SELECT * FROM student_group_schedules ORDER BY groupId, scheduleId")
    suspend fun groupSchedules(): List<StudentGroupScheduleEntity>

    @Upsert
    suspend fun save(schedule: ClassScheduleEntity)

    @Insert
    suspend fun insertSlots(slots: List<ClassScheduleSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attach(link: StudentGroupScheduleEntity)

    @Query("DELETE FROM class_schedule_slots WHERE scheduleId = :scheduleId")
    suspend fun deleteSlots(scheduleId: String)

    @Query("DELETE FROM student_group_schedules WHERE groupId = :groupId")
    suspend fun detachGroup(groupId: Int)

    @Query("DELETE FROM student_group_schedules")
    suspend fun clearLinks()

    @Query("DELETE FROM class_schedules")
    suspend fun clearSchedules()
}
