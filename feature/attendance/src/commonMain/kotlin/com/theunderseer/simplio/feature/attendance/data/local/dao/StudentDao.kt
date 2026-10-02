package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity

@Dao
interface StudentDao {
    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Query("SELECT * FROM students ORDER BY id")
    suspend fun getAll(): List<StudentEntity>

    @Query(
        """
    SELECT *
    FROM students
    WHERE id = :id
    """,
    )
    suspend fun getById(id: Int): StudentEntity?

    @Query("SELECT COUNT(*) FROM students")
    suspend fun count(): Int
}
