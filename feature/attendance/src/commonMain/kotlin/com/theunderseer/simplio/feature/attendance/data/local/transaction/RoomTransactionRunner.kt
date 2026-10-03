package com.theunderseer.simplio.feature.attendance.data.local.transaction

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.theunderseer.simplio.feature.attendance.data.local.database.AttendanceDatabase
import com.theunderseer.simplio.feature.attendance.domain.transaction.TransactionRunner

class RoomTransactionRunner(private val database: AttendanceDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block()
        }
    }
}
