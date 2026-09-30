package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class FakeTransactionRunner : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
