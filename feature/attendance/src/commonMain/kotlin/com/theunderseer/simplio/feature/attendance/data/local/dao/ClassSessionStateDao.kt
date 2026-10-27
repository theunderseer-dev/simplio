package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.theunderseer.simplio.feature.attendance.data.local.entity.ClassSessionStateEntity

@Dao
interface ClassSessionStateDao {
    @Query("SELECT * FROM class_session_states")
    suspend fun getAll(): List<ClassSessionStateEntity>

    @Query("SELECT * FROM class_session_states WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): ClassSessionStateEntity?

    @Upsert
    suspend fun save(row: ClassSessionStateEntity)

    @Query("DELETE FROM class_session_states")
    suspend fun clear()
}
