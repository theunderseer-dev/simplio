package com.theunderseer.simplio.feature.attendance.data.local.seed

import com.theunderseer.simplio.feature.attendance.data.local.dao.StudentGroupDao
import com.theunderseer.simplio.feature.attendance.data.local.entity.StudentGroupEntity
import com.theunderseer.simplio.feature.attendance.domain.model.DefaultStudentGroups
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class StudentGroupSeeder(
    private val studentGroupDao: StudentGroupDao,
    private val transactionRunner: TransactionRunner,
) {
    suspend fun seedDefaults() {
        transactionRunner.run {
            val existingIds =
                studentGroupDao.getAll()
                    .map { it.id }
                    .toSet()

            defaultStudentGroups
                .filterNot { it.id in existingIds }
                .forEach { group ->
                    studentGroupDao.insert(group)
                }
        }
    }

    private val defaultStudentGroups =
        DefaultStudentGroups.ALL.map { group ->
            StudentGroupEntity(
                id = group.id,
                name = group.name,
            )
        }
}
