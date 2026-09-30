package com.theunderseer.simplio.feature.attendance.fake

import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class FakeTransactionRunner : TransactionRunner {
    var runCount: Int = 0
        private set

    override suspend fun <T> run(block: suspend () -> T): T {
        runCount++
        return block()
    }
}
