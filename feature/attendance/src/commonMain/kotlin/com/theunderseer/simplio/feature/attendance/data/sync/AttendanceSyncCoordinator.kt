package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
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
    private val conflicts: RoomConflictStore? = null,
) {
    private val cycleMutex = Mutex()
    private val runnerMutex = Mutex()
    private val retries = Channel<Unit>(Channel.CONFLATED)
    private val mutableStatus = MutableStateFlow<AttendanceSyncStatus>(AttendanceSyncStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private var failures = 0
    private val mutableReview = MutableStateFlow<ConflictReviewState>(ConflictReviewState.Closed)
    val review = mutableReview.asStateFlow()
    private var reviewGeneration = 0

    fun dismissReview() {
        if ((mutableReview.value as? ConflictReviewState.Ready)?.isApplying == true) return
        reviewGeneration++
        mutableReview.value = ConflictReviewState.Closed
    }

    suspend fun openReview() = cycleMutex.withLock {
        if (mutableReview.value == ConflictReviewState.Closed || mutableReview.value is ConflictReviewState.Error) {
            loadReview()
        }
    }

    suspend fun applyReview(
        id: String,
        choices: Map<String, ConflictChoice>,
        catalogRenames: Map<String, String> = emptyMap(),
    ) = cycleMutex.withLock {
        val ready = mutableReview.value as? ConflictReviewState.Ready ?: return@withLock
        if (ready.review.id != id || ready.isApplying) return@withLock
        mutableReview.value = ready.copy(isApplying = true, message = null)
        try {
            checkNotNull(conflicts).apply(id, choices, remote.readHead(), catalogRenames)
            mutableReview.value = ConflictReviewState.Closed
            failures = 0
            mutableStatus.value = AttendanceSyncStatus.Syncing
            retries.trySend(Unit)
        } catch (error: CancellationException) {
            mutableReview.value = ready
            throw error
        } catch (error: StaleConflictReview) {
            loadReview("Data changed. Please review the updated versions.")
        } catch (error: Exception) {
            mutableReview.value = ready.copy(message = reviewError(error))
        }
    }

    private suspend fun loadReview(message: String? = null) {
        val generation = reviewGeneration
        mutableReview.value = ConflictReviewState.Loading
        try {
            val head = remote.readHead()
            val result = checkNotNull(conflicts).create(remote.readSnapshot(head))
            if (generation == reviewGeneration) mutableReview.value = ConflictReviewState.Ready(result, message)
        } catch (error: CancellationException) {
            if (generation == reviewGeneration) mutableReview.value = ConflictReviewState.Closed
            throw error
        } catch (error: Exception) {
            if (generation == reviewGeneration) mutableReview.value = ConflictReviewState.Error(reviewError(error))
        }
    }

    private fun reviewError(error: Exception): String = when {
        error is InvalidConflictChoice -> "These versions cannot be combined safely. Change your choices."
        error is FirestoreFailure && error.kind == FirestoreFailureKind.AUTHENTICATION ->
            "Sign in again to review changes."
        error is FirestoreFailure && error.kind == FirestoreFailureKind.PERMISSION -> "Cloud access denied."
        error is IOException || error is FirestoreFailure && error.kind == FirestoreFailureKind.RETRYABLE ->
            "Cannot reach the cloud. Your changes are saved. Try again."
        error is UploadPreparationFailure && error.problem == UploadProblem.TOO_LARGE ->
            "Too much data to combine safely. Your changes are saved."
        else -> "Cannot compare these changes safely. Your changes are saved. Try again."
    }

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
