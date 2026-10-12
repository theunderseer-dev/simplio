package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.dao.PackageTypeDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.model.PackagePlan
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
        PackagePlan.entries.map { plan ->
            PackageTypeEntity(
                name = plan.packageName,
                totalClasses = plan.totalClasses,
                validityWeeks = plan.validityWeeks,
                price = plan.price,
                maxVisitsPerAttendance = plan.maxVisitsPerAttendance,
            )
        }
}
