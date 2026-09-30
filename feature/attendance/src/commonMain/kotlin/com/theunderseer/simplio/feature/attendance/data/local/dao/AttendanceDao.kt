package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attendance: AttendanceEntity): Long

    @Query(
        """
        SELECT *
        FROM attendance
        WHERE packageId = :packageId
        ORDER BY attendedOnEpochDay
        """,
    )
    suspend fun getForPackage(packageId: Int): List<AttendanceEntity>
}
