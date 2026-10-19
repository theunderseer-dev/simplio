package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity

@Dao
interface StudentPackageDao {
    @Query("SELECT * FROM student_packages WHERE id = :id")
    suspend fun getById(id: Int): StudentPackageEntity?

    @Query("SELECT * FROM student_packages WHERE studentId = :studentId ORDER BY id")
    suspend fun getForStudent(studentId: Int): List<StudentPackageEntity>

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

    @Query(
        """
        UPDATE student_packages
        SET startedOnEpochDay = :startedOnEpochDay
        WHERE id = :packageId
          AND startedOnEpochDay IS NULL
        """,
    )
    suspend fun activateIfNeeded(packageId: Int, startedOnEpochDay: Long)

    @Query(
        """
        UPDATE student_packages
        SET startedOnEpochDay = (
            SELECT MIN(attendedOnEpochDay)
            FROM attendance
            WHERE packageId = :packageId
        )
        WHERE id = :packageId
        """,
    )
    suspend fun updateStartedOnFromAttendance(packageId: Int)
}
