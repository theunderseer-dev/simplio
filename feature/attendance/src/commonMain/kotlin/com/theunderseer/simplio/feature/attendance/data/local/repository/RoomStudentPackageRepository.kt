package com.theunderseer.simplio.feature.attendance.data.local.repository

import com.theunderseer.simplio.feature.attendance.data.local.dao.AttendanceDao
import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentPackageDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.AttendanceEntity
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentPackageEntity
import com.theunderseer.simplio.feature.attendance.data.local.mapper.toDomain
import com.theunderseer.simplio.feature.attendance.domain.model.PackageType
import com.theunderseer.simplio.feature.attendance.domain.model.StudentPackage
import com.theunderseer.simplio.feature.attendance.domain.repository.StudentPackageRepository
import kotlinx.datetime.LocalDate

class RoomStudentPackageRepository(
    private val studentPackageDao: StudentPackageDao,
    private val attendanceDao: AttendanceDao,
) : StudentPackageRepository {
    override suspend fun create(
        studentId: Int,
        packageType: PackageType,
        startedOn: LocalDate,
    ): StudentPackage {
        val entity =
            StudentPackageEntity(
                studentId = studentId,
                packageTypeId = packageType.id,
                packageName = packageType.name,
                totalClasses = packageType.totalClasses,
                validityWeeks = packageType.validityWeeks,
                price = packageType.price,
                startedOnEpochDay = startedOn.toEpochDays(),
            )

        val id = studentPackageDao.insert(entity).toInt()
        val firstAttendance =
            AttendanceEntity(
                packageId = id,
                attendedOnEpochDay = startedOn.toEpochDays(),
            )

        attendanceDao.insert(firstAttendance)

        return entity
            .copy(id = id)
            .toDomain(attendance = listOf(firstAttendance))
    }

    override suspend fun getLatestPackageForStudent(studentId: Int): StudentPackage? {
        val entity =
            studentPackageDao.getLatestForStudent(studentId)
                ?: return null

        val attendance =
            attendanceDao.getForPackage(entity.id)

        return entity.toDomain(
            attendance = attendance,
        )
    }

    override suspend fun recordAttendance(
        packageId: Int,
        attendedOn: LocalDate,
    ) {
        attendanceDao.insert(
            AttendanceEntity(
                packageId = packageId,
                attendedOnEpochDay = attendedOn.toEpochDays(),
            ),
        )
    }
}
