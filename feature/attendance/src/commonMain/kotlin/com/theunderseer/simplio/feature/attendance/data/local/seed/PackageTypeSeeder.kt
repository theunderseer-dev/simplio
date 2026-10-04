package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.dao.PackageTypeDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.PackageTypeEntity
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class PackageTypeSeeder(private val packageTypeDao: PackageTypeDao, private val transactionRunner: TransactionRunner) {
    suspend fun seedDefaults() {
        transactionRunner.run {
            if (packageTypeDao.getAll().isNotEmpty()) {
                return@run
            }

            packageTypeDao.insert(
                PackageTypeEntity(
                    name = "4-class package",
                    totalClasses = 4,
                    validityWeeks = 5,
                    price = 1400,
                ),
            )

            packageTypeDao.insert(
                PackageTypeEntity(
                    name = "5-class package",
                    totalClasses = 5,
                    validityWeeks = 6,
                    price = 1700,
                ),
            )
        }
    }
}
