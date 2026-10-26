package com.theunderseer.simplio.feature.attendance.presentation.sync

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictReviewState
import com.theunderseer.simplio.feature.attendance.fixtures.testCatalogRenameReview
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SyncRenameInputValidationUiTest {
    @Test
    fun `group and package rename reject oversized edits and allow a corrected distinct name`() {
        for (packageConflict in listOf(false, true)) {
            runComposeUiTest {
                val review = testCatalogRenameReview(packageConflict)
                val item = review.items.single()
                val newName = "${item.label} 2"
                var submitted: Map<String, String>? = null
                setContent {
                    SyncConflictReviewDialog(
                        ConflictReviewState.Ready(review),
                        {},
                        {},
                        { _, _ -> },
                        onApplyRenamed = { _, _, names -> submitted = names },
                    )
                }
                onNodeWithTag("sync-conflict-list").performScrollToNode(hasTestTag("rename-${item.key}"))
                onNodeWithTag("rename-${item.key}").performTextInput(newName)
                onNodeWithText("Apply and sync").assertIsEnabled()
                onNodeWithTag("rename-${item.key}").performTextReplacement("x".repeat(81))
                onNodeWithTag("rename-${item.key}").assertTextContains(newName)
                onNodeWithText("Use up to 80 characters on one line. Value was not changed.").assertIsDisplayed()
                onNodeWithText("Apply and sync").assertIsNotEnabled()
                onNodeWithTag("rename-${item.key}").performTextReplacement(newName)
                onNodeWithText("Apply and sync").assertIsEnabled().performClick()
                assertEquals(mapOf(item.key to newName), submitted)
            }
        }
    }
}
