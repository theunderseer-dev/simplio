package com.theunderseer.simplio.feature.attendance.domain.transaction

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
