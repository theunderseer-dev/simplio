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
import com.theunderseer.simplio.feature.attendance.data.sync.SyncClassSessionSummary
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSessionStatus
import com.theunderseer.simplio.feature.attendance.domain.model.MAX_GROUP_NAME_LENGTH
import com.theunderseer.simplio.feature.attendance.domain.model.MAX_PACKAGE_NAME_LENGTH
import com.theunderseer.simplio.feature.attendance.domain.model.isValidGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.isValidPackageName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_conflict_status
import com.theunderseer.simplio.feature.attendance.resources.class_status_cancelled
import com.theunderseer.simplio.feature.attendance.resources.class_status_held
import com.theunderseer.simplio.feature.attendance.resources.class_summary_schedule
import com.theunderseer.simplio.feature.attendance.resources.group_name_conflict
import com.theunderseer.simplio.feature.attendance.resources.group_name_invalid
import com.theunderseer.simplio.feature.attendance.resources.package_duplicate_name
import com.theunderseer.simplio.feature.attendance.resources.package_name_conflict
import com.theunderseer.simplio.feature.attendance.resources.package_rename
import com.theunderseer.simplio.shared.presentation.input.ObserveInputFeedback
import com.theunderseer.simplio.shared.presentation.input.ValidatedInputState
import com.theunderseer.simplio.shared.presentation.input.ValidatedOutlinedTextField
import com.theunderseer.simplio.shared.validation.InputPolicy
import org.jetbrains.compose.resources.stringResource

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
    val renameInputs = remember(ready?.review?.id) {
        ready?.review?.items.orEmpty().filter { it.requiresRename }.associate { item ->
            item.key to ValidatedInputState(
                "",
                InputPolicy.SingleLine(
                    if (item.requiresPackageRename) MAX_PACKAGE_NAME_LENGTH else MAX_GROUP_NAME_LENGTH,
                ),
            )
        }
    }
    renameInputs.values.forEach { ObserveInputFeedback(it) }
    val renames = renameInputs.mapValues { it.value.text }
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
                                        if (item.requiresRename) {
                                            Text(
                                                if (item.requiresPackageRename) {
                                                    stringResource(Res.string.package_name_conflict)
                                                } else {
                                                    stringResource(Res.string.group_name_conflict)
                                                },
                                            )
                                            Text("This device", style = MaterialTheme.typography.titleSmall)
                                            item.deviceDetails.forEach {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                            item.deviceClasses.forEach { ClassSessionConflictDetail(it) }
                                            Text("Cloud", style = MaterialTheme.typography.titleSmall)
                                            item.cloudDetails.forEach {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                            item.cloudClasses.forEach { ClassSessionConflictDetail(it) }
                                            val value = renames[item.key].orEmpty()
                                            val duplicate = value.isNotBlank() &&
                                                item.unavailableNames.any {
                                                    normalizedGroupName(it) == normalizedGroupName(value)
                                                }
                                            ValidatedOutlinedTextField(
                                                input = renameInputs.getValue(item.key),
                                                enabled = !applying,
                                                label = {
                                                    Text(
                                                        if (item.requiresPackageRename) {
                                                            stringResource(
                                                                Res.string.package_rename,
                                                            )
                                                        } else {
                                                            "New group name"
                                                        },
                                                    )
                                                },
                                                isError =
                                                duplicate ||
                                                    (
                                                        value.isNotEmpty() &&
                                                            !(
                                                                if (item.requiresPackageRename) {
                                                                    isValidPackageName(value)
                                                                } else {
                                                                    isValidGroupName(value)
                                                                }
                                                                )
                                                        ),
                                                supportingText = {
                                                    if (duplicate) {
                                                        Text(
                                                            if (item.requiresPackageRename) {
                                                                stringResource(Res.string.package_duplicate_name)
                                                            } else {
                                                                "A group with this name already exists"
                                                            },
                                                        )
                                                    } else if (value.isNotEmpty() &&
                                                        !(
                                                            if (item.requiresPackageRename) {
                                                                isValidPackageName(value)
                                                            } else {
                                                                isValidGroupName(value)
                                                            }
                                                            )
                                                    ) {
                                                        Text(stringResource(Res.string.group_name_invalid))
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("rename-${item.key}"),
                                            )
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                ConflictVersion(
                                                    "This device",
                                                    item.deviceDetails,
                                                    item.deviceClasses,
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
                                                    item.cloudClasses,
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
                                val resolved = choices + ready.review.items.filter { it.requiresRename }
                                    .associate { it.key to ConflictChoice.DEVICE }
                                if (renames.isEmpty()) {
                                    onApply(ready.review.id, resolved)
                                } else {
                                    onApplyRenamed?.invoke(ready.review.id, resolved, renames)
                                }
                            },
                            enabled = !applying &&
                                renameInputs.values.none { it.rejectedEdit } &&
                                ready.review.items.all { item ->
                                    if (!item.requiresRename) {
                                        item.key in choices
                                    } else {
                                        val name = renames[item.key].orEmpty()
                                        onApplyRenamed != null &&
                                            (
                                                if (item.requiresPackageRename) {
                                                    isValidPackageName(
                                                        name,
                                                    )
                                                } else {
                                                    isValidGroupName(name)
                                                }
                                                ) &&
                                            item.unavailableNames.none {
                                                normalizedGroupName(it) ==
                                                    normalizedGroupName(name)
                                            } &&
                                            ready.review.items.count {
                                                it.requiresPackageRename == item.requiresPackageRename &&
                                                    normalizedGroupName(renames[it.key].orEmpty()) ==
                                                    normalizedGroupName(name)
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
    classes: List<SyncClassSessionSummary>,
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
        classes.forEach { ClassSessionConflictDetail(it) }
    }
}

@Composable
private fun ClassSessionConflictDetail(value: SyncClassSessionSummary) {
    Text(
        stringResource(
            Res.string.class_conflict_status,
            value.date.toString(),
            stringResource(
                if (value.status == ClassSessionStatus.CANCELLED) {
                    Res.string.class_status_cancelled
                } else {
                    Res.string.class_status_held
                },
            ),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        stringResource(Res.string.class_summary_schedule, value.scheduleName, value.timeLabel),
        style = MaterialTheme.typography.bodySmall,
    )
}
