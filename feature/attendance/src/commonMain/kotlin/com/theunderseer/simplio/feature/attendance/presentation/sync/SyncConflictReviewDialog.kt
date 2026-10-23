package com.theunderseer.simplio.feature.attendance.presentation.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictChoice
import com.theunderseer.simplio.feature.attendance.data.sync.ConflictReviewState
import com.theunderseer.simplio.feature.attendance.domain.model.isValidGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName

@Composable
fun SyncConflictReviewDialog(
    state: ConflictReviewState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onApply: (String, Map<String, ConflictChoice>) -> Unit,
    onApplyRenamed: ((String, Map<String, ConflictChoice>, Map<String, String>) -> Unit)? = null,
) {
    if (state == ConflictReviewState.Closed) return
    val ready = state as? ConflictReviewState.Ready
    val applying = ready?.isApplying == true
    var choices by remember(ready?.review?.id) { mutableStateOf(emptyMap<String, ConflictChoice>()) }
    var renames by remember(ready?.review?.id) { mutableStateOf(emptyMap<String, String>()) }
    Dialog(
        onDismissRequest = { if (!applying) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !applying,
            dismissOnClickOutside = !applying,
        ),
    ) {
        OutlinedCard(modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(0.95f)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Review sync changes", style = MaterialTheme.typography.titleLarge)
                when (state) {
                    ConflictReviewState.Loading -> CircularProgressIndicator()
                    is ConflictReviewState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Try again") }
                    }
                    is ConflictReviewState.Ready -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false).heightIn(max = 520.dp)
                                .testTag("sync-conflict-list"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Text(
                                    "Choose a complete version for each student, including packages and attendance. " +
                                        "Both versions are saved on this device.",
                                )
                                if (state.review.items.isEmpty()) {
                                    Text("No overlapping changes. Your changes can be combined.")
                                } else {
                                    Text("Other changes will be combined.")
                                }
                            }
                            items(state.review.items, key = { it.key }) { item ->
                                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(item.label, style = MaterialTheme.typography.titleMedium)
                                        if (item.requiresGroupRename) {
                                            Text(
                                                "Another group already uses this name. Rename this device's group to keep both.",
                                            )
                                            Text("This device", style = MaterialTheme.typography.titleSmall)
                                            item.deviceDetails.forEach {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text("Cloud", style = MaterialTheme.typography.titleSmall)
                                            item.cloudDetails.forEach {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                            val value = renames[item.key].orEmpty()
                                            val duplicate = value.isNotBlank() &&
                                                item.unavailableGroupNames.any {
                                                    normalizedGroupName(it) == normalizedGroupName(value)
                                                }
                                            OutlinedTextField(
                                                value = value,
                                                onValueChange = { renames = renames + (item.key to it) },
                                                enabled = !applying,
                                                singleLine = true,
                                                label = {
                                                    Text("New group name")
                                                },
                                                isError = duplicate,
                                                supportingText = {
                                                    if (duplicate) Text("A group with this name already exists")
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("rename-${item.key}"),
                                            )
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                ConflictVersion(
                                                    "This device",
                                                    item.deviceDetails,
                                                    choices[item.key] == ConflictChoice.DEVICE,
                                                    !applying,
                                                    "device-${item.key}",
                                                    Modifier.weight(1f),
                                                ) {
                                                    choices = choices + (item.key to ConflictChoice.DEVICE)
                                                }
                                                ConflictVersion(
                                                    "Cloud",
                                                    item.cloudDetails,
                                                    choices[item.key] == ConflictChoice.CLOUD,
                                                    !applying,
                                                    "cloud-${item.key}",
                                                    Modifier.weight(1f),
                                                ) {
                                                    choices = choices + (item.key to ConflictChoice.CLOUD)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    ConflictReviewState.Closed -> Unit
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !applying) { Text("Later") }
                    if (ready != null) {
                        TextButton(
                            onClick = {
                                val resolved = choices + ready.review.items.filter { it.requiresGroupRename }
                                    .associate { it.key to ConflictChoice.DEVICE }
                                if (renames.isEmpty()) {
                                    onApply(ready.review.id, resolved)
                                } else {
                                    onApplyRenamed?.invoke(ready.review.id, resolved, renames)
                                }
                            },
                            enabled = !applying &&
                                ready.review.items.all { item ->
                                    if (!item.requiresGroupRename) {
                                        item.key in choices
                                    } else {
                                        val name = renames[item.key].orEmpty()
                                        onApplyRenamed != null &&
                                            isValidGroupName(name) &&
                                            item.unavailableGroupNames.none {
                                                normalizedGroupName(it) ==
                                                    normalizedGroupName(name)
                                            } &&
                                            renames.values.count {
                                                normalizedGroupName(it) == normalizedGroupName(name)
                                            } ==
                                            1
                                    }
                                },
                        ) {
                            Text(if (applying) "Applying…" else "Apply and sync")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictVersion(
    title: String,
    details: List<String>,
    selected: Boolean,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().testTag(
                tag,
            ).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
