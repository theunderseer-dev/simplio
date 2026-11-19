package com.theunderseer.simplio.feature.attendance.domain.repository

fun interface PackageTypeDeletionRepository {
    suspend fun delete(packageTypeId: Int)
}
