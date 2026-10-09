package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.dao.PackageTypeDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class PackageTypeSeeder(private val packageTypeDao: PackageTypeDao, private val transactionRunner: TransactionRunner) {
    suspend fun seedDefaults() {
        transactionRunner.run {
            val existingNames =
                packageTypeDao.getAll()
                    .map { it.name }
                    .toSet()

            defaultPackageTypes
                .filterNot { it.name in existingNames }
                .forEach { packageType ->
                    packageTypeDao.insert(packageType)
                }
        }
    }

    private val defaultPackageTypes =
        listOf(
            PackageTypeEntity(
                name = "4-class package",
                totalClasses = 4,
                validityWeeks = 4,
                price = 1400,
            ),
            PackageTypeEntity(
                name = "5-class package",
                totalClasses = 5,
                validityWeeks = 5,
                price = 1700,
            ),
            PackageTypeEntity(
                name = "Duo 8 · Special rate",
                totalClasses = 8,
                validityWeeks = 4,
                price = 2400,
                maxVisitsPerAttendance = 2,
            ),
        )
}
