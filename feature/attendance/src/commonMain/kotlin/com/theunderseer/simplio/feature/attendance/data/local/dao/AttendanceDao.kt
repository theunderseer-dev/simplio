package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.model.ClassAttendanceRow

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

    @Query(
        """
        DELETE FROM attendance
        WHERE packageId = :packageId
          AND attendedOnEpochDay = :attendedOnEpochDay
        """,
    )
    suspend fun deleteForPackageOnDate(packageId: Int, attendedOnEpochDay: Long)

    @Query(
        """
        SELECT MIN(attendedOnEpochDay)
        FROM attendance
        """,
    )
    suspend fun getFirstAttendanceEpochDay(): Long?

    @Query(
        """
    SELECT
        attendance.attendedOnEpochDay,
        attendance.sessionId,
        students.id AS studentId,
        students.fullName,
        students.groupId,
        SUM(attendance.visitCount) AS visitCount
    FROM attendance
    INNER JOIN student_packages
        ON student_packages.id = attendance.packageId
    INNER JOIN students
        ON students.id = student_packages.studentId
    WHERE attendance.attendedOnEpochDay IN (
        SELECT DISTINCT attendedOnEpochDay
        FROM attendance
        WHERE :beforeEpochDay IS NULL
            OR attendedOnEpochDay < :beforeEpochDay
        ORDER BY attendedOnEpochDay DESC
        LIMIT :limit
    )
    GROUP BY
        attendance.attendedOnEpochDay,
        attendance.sessionId,
        students.id,
        students.fullName,
        students.groupId
    ORDER BY
        attendance.attendedOnEpochDay DESC,
        students.fullName COLLATE NOCASE,
        students.id
    """,
    )
    suspend fun getClassHistoryPage(beforeEpochDay: Long?, limit: Int): List<ClassAttendanceRow>
}
