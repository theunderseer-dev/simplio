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
        ORDER BY id
        """,
    )
    suspend fun getAll(): List<PackageTypeEntity>

    @Query("SELECT * FROM package_types WHERE isArchived = 0 ORDER BY id")
    suspend fun getActive(): List<PackageTypeEntity>

    @Query("UPDATE package_types SET isArchived = 1 WHERE id = :id AND isArchived = 0")
    suspend fun archive(id: Int): Int

    @Query(
        """
        SELECT *
        FROM package_types
        WHERE id = :id
        """,
    )
    suspend fun getById(id: Int): PackageTypeEntity?
}
