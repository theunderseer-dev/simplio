package com.theunderseer.simplio.feature.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity

/** Used only inside RoomPullStore's transaction; imports never enter the local outbox. */
@Dao
interface SyncPullDao {
    @Query("SELECT * FROM sync_records")
    suspend fun records(): List<SyncRecordEntity>

    @Query("SELECT * FROM student_packages")
    suspend fun packages(): List<StudentPackageEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM attendance")
    suspend fun clearAttendance()

    @Query("DELETE FROM student_packages")
    suspend fun clearPackages()

    @Query("DELETE FROM students")
    suspend fun clearStudents()

    @Query("DELETE FROM package_types")
    suspend fun clearTypes()

    @Query("DELETE FROM student_groups")
    suspend fun clearGroups()

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query("DELETE FROM sync_records")
    suspend fun clearRecords()

    @Insert
    suspend fun insertGroups(rows: List<StudentGroupEntity>)

    @Insert
    suspend fun insertTypes(rows: List<PackageTypeEntity>)

    @Insert
    suspend fun insertStudents(rows: List<StudentEntity>)

    @Insert
    suspend fun insertPackages(rows: List<StudentPackageEntity>)

    @Insert
    suspend fun insertAttendance(rows: List<AttendanceEntity>)

    @Insert
    suspend fun insertRecords(rows: List<SyncRecordEntity>)
}
