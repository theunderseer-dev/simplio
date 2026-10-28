package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity

@Dao
interface StudentGroupDao {
    @Query("SELECT * FROM student_groups ORDER BY id")
    suspend fun getAll(): List<StudentGroupEntity>

    @Query("DELETE FROM student_groups WHERE id = :groupId")
    suspend fun delete(groupId: Int)

    @Insert
    suspend fun insert(group: StudentGroupEntity): Long
}
