package com.theunderseer.simplio.feature.attendance.domain.repository

interface StudentGroupDeletionRepository {
    suspend fun delete(groupId: Int): DeleteStudentGroupResult
}

enum class DeleteStudentGroupResult { DELETED, NOT_FOUND, NOT_EMPTY }
