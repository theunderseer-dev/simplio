package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity

@Dao
interface PackageTypeDao {
    @Insert
    suspend fun insert(packageType: PackageTypeEntity): Long

    @Query(
        """
        SELECT *
        FROM package_types
        WHERE id = :id
        """,
    )
    suspend fun getById(id: Int): PackageTypeEntity?
}
