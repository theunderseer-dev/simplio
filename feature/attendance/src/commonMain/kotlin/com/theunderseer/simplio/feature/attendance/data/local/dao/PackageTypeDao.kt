package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity

@Dao
interface PackageTypeDao {
    @Query(
        """
        SELECT *
        FROM package_types
        ORDER BY id
        """,
    )
    suspend fun getAll(): List<PackageTypeEntity>

    @Query(
        """
        SELECT *
        FROM package_types
        WHERE id = :id
        """,
    )
    suspend fun getById(id: Int): PackageTypeEntity?
}
