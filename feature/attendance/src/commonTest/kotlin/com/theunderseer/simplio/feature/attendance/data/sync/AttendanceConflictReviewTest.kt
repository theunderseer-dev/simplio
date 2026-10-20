package com.theunderseer.simplio.feature.attendance.data.sync

import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomConflictStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomPullStore
import com.theunderseer.simplio.feature.attendance.data.local.sync.RoomUploadStore
import com.theunderseer.simplio.feature.attendance.fixtures.ConflictTestDevices
import com.theunderseer.simplio.feature.attendance.fixtures.testUploadTarget
import com.theunderseer.simplio.feature.attendance.fixtures.withConflictDevices
import com.theunderseer.simplio.shared.firebase.FirestoreFailure
import com.theunderseer.simplio.shared.firebase.FirestoreFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class AttendanceConflictReviewTest {
    @Test
    fun `applying a review wakes the paused runner and uploads the chosen value`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val sync = coordinator()
            val job = backgroundScope.launch { sync.run() }
            sync.status.first { it == AttendanceSyncStatus.Paused(UploadProblem.CONFLICT) }
            sync.openReview()
            val ready = assertIs<ConflictReviewState.Ready>(sync.review.value)
            sync.applyReview(ready.review.id, mapOf(ready.review.items.single().key to ConflictChoice.DEVICE))
            device.syncDao().observePendingCount().first { it == 0 }
            assertEquals("APPLIED", device.syncConflictDao().getAll().single().status)
            job.cancelAndJoin()
        }
    }

    @Test
    fun `stale review refreshes both versions and requires a new choice`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val sync = coordinator()
            sync.syncOnce()
            sync.openReview()
            val first = assertIs<ConflictReviewState.Ready>(sync.review.value).review
            remoteNote("Updated cloud note")
            sync.applyReview(first.id, mapOf(first.items.single().key to ConflictChoice.DEVICE))
            val fresh = assertIs<ConflictReviewState.Ready>(sync.review.value)
            assertNotEquals(first.id, fresh.review.id)
            assertNotNull(fresh.message)
            assertEquals(true, fresh.review.items.single().cloudDetails.contains("Note: Updated cloud note"))
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals("SUPERSEDED", device.syncConflictDao().get(first.id)?.status)
            assertEquals(1, device.syncPullDao().pendingCount())
        }
    }

    @Test
    fun `network failure while applying preserves the open review and queued changes`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            var offline = false
            val remote = object : AttendancePullRemote {
                override suspend fun readHead(): AttendanceCloudHead {
                    if (offline) throw FirestoreFailure(FirestoreFailureKind.RETRYABLE)
                    return cloud.readHead()
                }
                override suspend fun readSnapshot(head: AttendanceCloudHead) = cloud.readSnapshot(head)
            }
            val sync = coordinator(remote)
            sync.syncOnce()
            sync.openReview()
            val review = assertIs<ConflictReviewState.Ready>(sync.review.value).review
            offline = true
            sync.applyReview(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD))
            val failed = assertIs<ConflictReviewState.Ready>(sync.review.value)
            assertEquals(review.id, failed.review.id)
            assertEquals(false, failed.isApplying)
            assertNotNull(failed.message)
            assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
            assertEquals(1, device.syncPullDao().pendingCount())
            offline = false
            sync.applyReview(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD))
            assertEquals(ConflictReviewState.Closed, sync.review.value)
            assertEquals("Cloud note", device.studentDao().getById(localStudentId)?.note)
        }
    }

    @Test
    fun `cancellation during head verification leaves the review unapplied`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            var cancelled = false
            val remote = object : AttendancePullRemote {
                override suspend fun readHead(): AttendanceCloudHead {
                    if (cancelled) throw CancellationException("test")
                    return cloud.readHead()
                }
                override suspend fun readSnapshot(head: AttendanceCloudHead) = cloud.readSnapshot(head)
            }
            val sync = coordinator(remote)
            sync.syncOnce()
            sync.openReview()
            val review = assertIs<ConflictReviewState.Ready>(sync.review.value).review
            cancelled = true
            assertFailsWith<CancellationException> {
                sync.applyReview(review.id, mapOf(review.items.single().key to ConflictChoice.CLOUD))
            }
            assertEquals(false, assertIs<ConflictReviewState.Ready>(sync.review.value).isApplying)
            assertEquals("OPEN", device.syncConflictDao().get(review.id)?.status)
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
        }
    }

    @Test
    fun `opening review twice cannot replace the version being selected`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val sync = coordinator()
            sync.syncOnce()
            sync.openReview()
            val first = assertIs<ConflictReviewState.Ready>(sync.review.value).review
            sync.openReview()
            assertEquals(first.id, assertIs<ConflictReviewState.Ready>(sync.review.value).review.id)
            assertEquals(1, device.syncConflictDao().getAll().size)
        }
    }

    @Test
    fun `postponing an in flight review does not reopen the dialog when its download finishes`() = runTest {
        withConflictDevices {
            localNote("Device note")
            remoteNote("Cloud note")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val remote = object : AttendancePullRemote {
                override suspend fun readHead() = cloud.readHead()
                override suspend fun readSnapshot(head: AttendanceCloudHead): AttendanceCloudSnapshot {
                    entered.complete(Unit)
                    release.await()
                    return cloud.readSnapshot(head)
                }
            }
            val sync = coordinator(remote)
            sync.syncOnce()
            val job = launch { sync.openReview() }
            entered.await()
            sync.dismissReview()
            release.complete(Unit)
            job.join()
            assertEquals(ConflictReviewState.Closed, sync.review.value)
            assertEquals("Device note", device.studentDao().getById(localStudentId)?.note)
            assertEquals(1, device.syncPullDao().pendingCount())
        }
    }

    private fun ConflictTestDevices.coordinator(remote: AttendancePullRemote = cloud) = AttendanceSyncCoordinator(
        store = RoomPullStore(device, testUploadTarget),
        remote = remote,
        uploader = AttendanceUploadCoordinator(RoomUploadStore(device), cloud, testUploadTarget),
        conflicts = RoomConflictStore(device, testUploadTarget),
    )
}
