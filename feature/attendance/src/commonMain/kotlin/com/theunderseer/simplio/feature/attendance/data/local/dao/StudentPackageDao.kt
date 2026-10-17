package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity

@Dao
interface StudentPackageDao {
    @Insert
    suspend fun insert(studentPackage: StudentPackageEntity): Long

    @Query(
        """
        SELECT *
        FROM student_packages
        WHERE studentId = :studentId
        ORDER BY purchasedOnEpochDay DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestForStudent(studentId: Int): StudentPackageEntity?
}
