package com.theunderseer.simplio.feature.attendance.presentation.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictReviewState
import com.theunderseer.simplio.feature.attendance.data.sync.SyncConflictItem
import com.theunderseer.simplio.feature.attendance.data.sync.SyncConflictReview
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SyncConflictReviewDialogUiTest {
    private val anna = SyncConflictItem("anna", "Anna Smith", listOf("Note: Device Anna"), listOf("Note: Cloud Anna"))
    private val bob = SyncConflictItem("bob", "Bob Lee", listOf("Note: Device Bob"), listOf("Note: Cloud Bob"))
    private val review = SyncConflictReview("review-1", listOf(anna, bob), 2)

    @Test
    fun `each student requires an explicit selection before apply`() = runComposeUiTest {
        var submitted: Map<String, ConflictChoice>? = null
        setContent {
            MaterialTheme {
                SyncConflictReviewDialog(ConflictReviewState.Ready(review), {
                }, {}, { _, choices -> submitted = choices })
            }
        }
        onNodeWithText("Note: Device Anna").assertIsDisplayed()
        onNodeWithText("Note: Cloud Anna").assertIsDisplayed()
        onNodeWithText("Apply and sync").assertIsNotEnabled()
        onNodeWithTag("device-anna").performClick()
        onNodeWithText("Apply and sync").assertIsNotEnabled()
        onNodeWithTag("sync-conflict-list").performScrollToNode(hasTestTag("cloud-bob"))
        onNodeWithTag("cloud-bob").performClick()
        onNodeWithText("Apply and sync").assertIsEnabled().performClick()
        assertEquals(mapOf("anna" to ConflictChoice.DEVICE, "bob" to ConflictChoice.CLOUD), submitted)
    }

    @Test
    fun `a refreshed review clears selections from the previous versions`() = runComposeUiTest {
        var state by mutableStateOf(ConflictReviewState.Ready(review.copy(items = listOf(anna))))
        setContent { MaterialTheme { SyncConflictReviewDialog(state, {}, {}, { _, _ -> }) } }
        onNodeWithTag("device-anna").performClick()
        onNodeWithText("Apply and sync").assertIsEnabled()
        runOnIdle { state = state.copy(review = state.review.copy(id = "review-2"), message = "Data changed.") }
        onNodeWithTag("device-anna").assertIsNotSelected()
        onNodeWithText("Apply and sync").assertIsNotEnabled()
        onNodeWithText("Data changed.").assertIsDisplayed()
    }

    @Test
    fun `applying prevents duplicate submissions and dismissal`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SyncConflictReviewDialog(
                    ConflictReviewState.Ready(review.copy(items = listOf(anna)), isApplying = true),
                    {},
                    {},
                    { _, _ -> },
                )
            }
        }
        onNodeWithTag("device-anna").assertIsNotEnabled()
        onNodeWithTag("cloud-anna").assertIsNotEnabled()
        onNodeWithText("Applying…").assertIsNotEnabled()
        onNodeWithText("Later").assertIsNotEnabled()
    }

    @Test
    fun `failed review can be retried or postponed`() = runComposeUiTest {
        var retries = 0
        var dismissals = 0
        setContent {
            MaterialTheme {
                SyncConflictReviewDialog(
                    ConflictReviewState.Error("Cannot reach the cloud."),
                    { dismissals++ },
                    { retries++ },
                    { _, _ -> },
                )
            }
        }
        onNodeWithText("Cannot reach the cloud.").assertIsDisplayed()
        onNodeWithText("Try again").performClick()
        onNodeWithText("Later").performClick()
        assertEquals(1, retries)
        assertEquals(1, dismissals)
    }

    @Test
    fun `independent changes can be combined without selecting an unrelated version`() = runComposeUiTest {
        var appliedId: String? = null
        setContent {
            MaterialTheme {
                SyncConflictReviewDialog(
                    ConflictReviewState.Ready(review.copy(items = emptyList())),
                    {},
                    {},
                    { id, _ -> appliedId = id },
                )
            }
        }
        onNodeWithText("No overlapping changes. Your changes can be combined.").assertIsDisplayed()
        onNodeWithText("Apply and sync").assertIsEnabled().performClick()
        assertEquals(review.id, appliedId)
    }

    @Test
    fun `duplicate group conflict requires valid new name and submits it with device choice`() = runComposeUiTest {
        val item = SyncConflictItem(
            "group:new",
            "Private",
            listOf("Group: Private"),
            listOf("Group: PRIVATE"),
            requiresGroupRename = true,
            unavailableGroupNames = listOf("Private"),
        )
        val ready = ConflictReviewState.Ready(SyncConflictReview("rename", listOf(item), 1))
        var result: Map<String, String>? = null
        setContent {
            MaterialTheme {
                SyncConflictReviewDialog(ready, {}, {}, { _, _ -> }, onApplyRenamed = { _, choices, names ->
                    assertEquals(mapOf(item.key to ConflictChoice.DEVICE), choices)
                    result = names
                })
            }
        }
        onNodeWithText("Apply and sync").assertIsNotEnabled()
        onNodeWithTag("sync-conflict-list").performScrollToNode(hasTestTag("rename-${item.key}"))
        onNodeWithTag("rename-${item.key}").performTextInput("Private 2")
        onNodeWithText("Apply and sync").assertIsEnabled().performClick()
        assertEquals(mapOf(item.key to "Private 2"), result)
    }
}
