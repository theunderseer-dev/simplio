package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlin.random.Random

/** A single owner serializes receipt recovery, pulls and uploads for an account database. */
class AttendanceSyncCoordinator(
    private val store: RoomPullStore,
    private val remote: AttendancePullRemote,
    private val uploader: AttendanceUploadCoordinator,
    private val pollMillis: Long = 60_000,
) {
    private val cycleMutex = Mutex()
    private val runnerMutex = Mutex()
    private val retries = Channel<Unit>(Channel.CONFLATED)
    private val mutableStatus = MutableStateFlow<AttendanceSyncStatus>(AttendanceSyncStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private var failures = 0

    init {
        require(pollMillis > 0)
    }

    fun retry() {
        retries.trySend(Unit)
    }

    suspend fun run() = runnerMutex.withLock {
        while (currentCoroutineContext().isActive) {
            when (val result = syncOnce()) {
                AttendanceSyncStatus.Idle -> withTimeoutOrNull(pollMillis) { store.observeWork().first { it } }
                AttendanceSyncStatus.Syncing -> Unit
                is AttendanceSyncStatus.WaitingToRetry -> {
                    val base = (1_000L shl (result.attempt - 1).coerceIn(0, 6)).coerceAtMost(60_000)
                    delay(base + Random.nextLong(0, base / 5 + 1))
                }
                is AttendanceSyncStatus.Paused -> {
                    retries.receive()
                    store.retry()
                    failures = 0
                }
            }
        }
    }

    suspend fun syncOnce(): AttendanceSyncStatus = cycleMutex.withLock {
        mutableStatus.value = AttendanceSyncStatus.Syncing
        val result = try {
            val upload = if (store.mustRecoverBatch()) {
                uploader.uploadNext()
            } else {
                val head = remote.readHead()
                if (store.plan(head) == PullAction.DOWNLOAD) {
                    store.apply(remote.readSnapshot(head))
                }
                uploader.uploadNext()
            }
            when (upload) {
                AttendanceUploadStatus.Idle -> AttendanceSyncStatus.Idle
                AttendanceUploadStatus.Uploading -> AttendanceSyncStatus.Syncing
                is AttendanceUploadStatus.WaitingToRetry -> AttendanceSyncStatus.WaitingToRetry(upload.attempt)
                is AttendanceUploadStatus.Paused -> AttendanceSyncStatus.Paused(upload.problem)
            }.also { failures = 0 }
        } catch (error: CancellationException) {
            throw error
        } catch (error: UploadPreparationFailure) {
            AttendanceSyncStatus.Paused(error.problem)
        } catch (error: FirestoreFailure) {
            failures++
            when (error.kind) {
                FirestoreFailureKind.AUTHENTICATION -> AttendanceSyncStatus.Paused(UploadProblem.AUTHENTICATION)
                FirestoreFailureKind.PERMISSION -> AttendanceSyncStatus.Paused(UploadProblem.PERMISSION)
                FirestoreFailureKind.CONFLICT -> AttendanceSyncStatus.Paused(UploadProblem.CONFLICT)
                FirestoreFailureKind.REJECTED -> AttendanceSyncStatus.Paused(UploadProblem.INVALID_DATA)
                FirestoreFailureKind.RETRYABLE -> AttendanceSyncStatus.WaitingToRetry(failures)
                else -> if (failures < 2) {
                    AttendanceSyncStatus.WaitingToRetry(failures)
                } else {
                    AttendanceSyncStatus.Paused(UploadProblem.SERVICE)
                }
            }
        } catch (error: IllegalArgumentException) {
            AttendanceSyncStatus.Paused(UploadProblem.INVALID_DATA)
        } catch (error: IllegalStateException) {
            AttendanceSyncStatus.Paused(UploadProblem.INVALID_DATA)
        } catch (error: IOException) {
            failures++
            AttendanceSyncStatus.WaitingToRetry(failures)
        } catch (error: Exception) {
            failures++
            if (failures < 2) {
                AttendanceSyncStatus.WaitingToRetry(failures)
            } else {
                AttendanceSyncStatus.Paused(UploadProblem.SERVICE)
            }
        }
        mutableStatus.value = result
        result
    }
}
