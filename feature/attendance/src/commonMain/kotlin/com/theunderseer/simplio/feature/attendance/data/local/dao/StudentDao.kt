package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity

@Dao
interface StudentDao {
    @Insert
    suspend fun insert(student: StudentEntity): Long
}
