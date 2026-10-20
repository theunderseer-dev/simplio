package com.theunderseer.simplio.feature.attendance.data.local.sync

import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.SyncRecordEntity

internal data class ImportedAttendanceRows(
    val groups: List<StudentGroupEntity>,
    val types: List<PackageTypeEntity>,
    val students: List<StudentEntity>,
    val packages: List<StudentPackageEntity>,
    val attendance: List<AttendanceEntity>,
    val records: List<SyncRecordEntity>,
)
