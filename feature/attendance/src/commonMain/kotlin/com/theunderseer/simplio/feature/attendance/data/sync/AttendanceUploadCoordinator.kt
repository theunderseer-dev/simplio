package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class AttendanceUploadCoordinator(
    private val store: RoomUploadStore,
    private val remote: AttendanceUploadRemote,
    private val target: UploadTarget,
) {
    private val uploadMutex = Mutex()
    private val runnerMutex = Mutex()
    private val retries = Channel<Unit>(Channel.CONFLATED)
    private val mutableStatus = MutableStateFlow<AttendanceUploadStatus>(AttendanceUploadStatus.Idle)
    val status = mutableStatus.asStateFlow()

    fun retry() {
        retries.trySend(Unit)
    }

    suspend fun run() = runnerMutex.withLock {
        while (currentCoroutineContext().isActive) {
            when (val result = uploadNext()) {
                AttendanceUploadStatus.Idle -> store.observeWork().first { it }
                AttendanceUploadStatus.Uploading -> Unit
                is AttendanceUploadStatus.WaitingToRetry -> {
                    val base = (1_000L shl (result.attempt - 1).coerceIn(0, 6)).coerceAtMost(60_000)
                    delay(base + Random.nextLong(0, base / 5 + 1))
                }
                is AttendanceUploadStatus.Paused -> {
                    retries.receive()
                    store.retry()
                }
            }
        }
    }

    suspend fun uploadNext(): AttendanceUploadStatus = uploadMutex.withLock {
        val result = try {
            uploadNextLocked()
        } catch (error: CancellationException) {
            throw error
        } catch (error: UploadPreparationFailure) {
            AttendanceUploadStatus.Paused(error.problem)
        } catch (error: Exception) {
            AttendanceUploadStatus.Paused(UploadProblem.INVALID_DATA)
        }
        mutableStatus.value = result
        result
    }

    private suspend fun uploadNextLocked(): AttendanceUploadStatus {
        val batch = store.prepare(target) ?: return AttendanceUploadStatus.Idle
        batch.blockedReason?.let { return AttendanceUploadStatus.Paused(UploadProblem.valueOf(it)) }
        val snapshot = store.snapshot(batch)
        mutableStatus.value = AttendanceUploadStatus.Uploading
        store.recordAttempt(batch.batchId)
        val acknowledgement = try {
            remote.sendOrRecover(snapshot, batch.commitBody)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val kind = (error as? FirestoreFailure)?.kind ?: FirestoreFailureKind.RETRYABLE
            val attempts = batch.attempts + 1
            if (kind == FirestoreFailureKind.RETRYABLE ||
                (kind in setOf(FirestoreFailureKind.INTERNAL, FirestoreFailureKind.INVALID_RESPONSE) && attempts < 2)
            ) {
                return AttendanceUploadStatus.WaitingToRetry(attempts)
            }
            val problem = when (kind) {
                FirestoreFailureKind.AUTHENTICATION -> UploadProblem.AUTHENTICATION
                FirestoreFailureKind.PERMISSION -> UploadProblem.PERMISSION
                FirestoreFailureKind.CONFLICT -> UploadProblem.CONFLICT
                FirestoreFailureKind.REJECTED -> UploadProblem.INVALID_DATA
                else -> UploadProblem.SERVICE
            }
            store.block(batch.batchId, problem)
            return AttendanceUploadStatus.Paused(problem)
        }
        try {
            store.acknowledge(batch, acknowledgement)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            store.block(batch.batchId, UploadProblem.INVALID_DATA)
            return AttendanceUploadStatus.Paused(UploadProblem.INVALID_DATA)
        }
        return AttendanceUploadStatus.Uploading
    }
}
