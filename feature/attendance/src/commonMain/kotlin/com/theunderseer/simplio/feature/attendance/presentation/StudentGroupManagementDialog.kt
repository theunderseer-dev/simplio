package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.repository.DeleteStudentGroupResult
import com.theunderseer.simplio.feature.attendance.domain.schedule.DefaultClassSchedule
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_close
import com.theunderseer.simplio.feature.attendance.resources.class_saving
import com.theunderseer.simplio.feature.attendance.resources.group_delete
import com.theunderseer.simplio.feature.attendance.resources.group_delete_action
import com.theunderseer.simplio.feature.attendance.resources.group_delete_confirmation
import com.theunderseer.simplio.feature.attendance.resources.group_delete_failed
import com.theunderseer.simplio.feature.attendance.resources.group_delete_missing
import com.theunderseer.simplio.feature.attendance.resources.group_delete_not_empty
import com.theunderseer.simplio.feature.attendance.resources.group_delete_schedule_hint
import com.theunderseer.simplio.feature.attendance.resources.group_delete_title
import com.theunderseer.simplio.feature.attendance.resources.group_empty
import com.theunderseer.simplio.feature.attendance.resources.group_manage
import com.theunderseer.simplio.feature.attendance.resources.group_options
import com.theunderseer.simplio.feature.attendance.resources.group_schedule_slot
import com.theunderseer.simplio.feature.attendance.resources.group_student_count
import com.theunderseer.simplio.feature.attendance.resources.ic_close
import com.theunderseer.simplio.feature.attendance.resources.ic_more_vert
import com.theunderseer.simplio.feature.attendance.resources.package_cancel
import com.theunderseer.simplio.feature.attendance.resources.weekday_friday
import com.theunderseer.simplio.feature.attendance.resources.weekday_monday
import com.theunderseer.simplio.feature.attendance.resources.weekday_saturday
import com.theunderseer.simplio.feature.attendance.resources.weekday_sunday
import com.theunderseer.simplio.feature.attendance.resources.weekday_thursday
import com.theunderseer.simplio.feature.attendance.resources.weekday_tuesday
import com.theunderseer.simplio.feature.attendance.resources.weekday_wednesday
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StudentGroupOptions(onManage: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.group_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.group_manage)) },
                onClick = {
                    expanded = false
                    onManage()
                },
            )
        }
    }
}

@Composable
internal fun StudentGroupManagementDialog(
    state: AttendanceUiState.Content,
    onDelete: (Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    var requestedId by remember { mutableStateOf<Int?>(null) }
    val deletion = state.deleteStudentGroupState
    val saving = deletion is DeleteStudentGroupUiState.Saving
    LaunchedEffect(deletion) {
        if (deletion is DeleteStudentGroupUiState.Deleted) {
            requestedId = null
            onClear()
        }
    }
    val group = state.studentGroups.firstOrNull { it.id == requestedId }
    val counts = state.attendance.entries.groupingBy { it.student.groupId }.eachCount()
    val error = deletion as? DeleteStudentGroupUiState.Error
    val errorText = error?.let {
        stringResource(
            when (it.reason) {
                DeleteStudentGroupResult.NOT_EMPTY -> Res.string.group_delete_not_empty
                DeleteStudentGroupResult.NOT_FOUND -> Res.string.group_delete_missing
                else -> Res.string.group_delete_failed
            },
        )
    }
    if (group != null) {
        val notEmpty = (counts[group.id] ?: 0) > 0 || error?.reason == DeleteStudentGroupResult.NOT_EMPTY
        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    requestedId = null
                    onClear()
                }
            },
            title = { Text(stringResource(Res.string.group_delete_title, group.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.group_delete_confirmation))
                    Text(
                        stringResource(Res.string.group_delete_schedule_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (notEmpty) {
                        Text(stringResource(Res.string.group_delete_not_empty), color = MaterialTheme.colorScheme.error)
                    } else {
                        errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(group.id) },
                    enabled = !saving && !notEmpty,
                    modifier = Modifier.testTag("confirm-group-delete"),
                ) {
                    Text(stringResource(if (saving) Res.string.class_saving else Res.string.group_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    requestedId = null
                    onClear()
                }, enabled = !saving) {
                    Text(stringResource(Res.string.package_cancel))
                }
            },
        )
    } else {
        AlertDialog(
            modifier = Modifier.widthIn(max = 640.dp).testTag("manage-groups-dialog"),
            onDismissRequest = { if (!saving) onClose() },
            title = { Text(stringResource(Res.string.group_manage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.studentGroups.isEmpty()) item { Text(stringResource(Res.string.group_empty)) }
                        items(state.studentGroups, key = { it.id }) { item ->
                            val count = counts[item.id] ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name)
                                    Text(
                                        pluralStringResource(Res.plurals.group_student_count, count, count),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val schedules =
                                        state.groupSchedules[item.id]
                                            ?: state.classSchedules.filter { it.id == DefaultClassSchedule.ID }
                                    schedules.forEach { schedule ->
                                        schedule.slots.forEach { slot ->
                                            val weekday = listOf(
                                                Res.string.weekday_monday,
                                                Res.string.weekday_tuesday,
                                                Res.string.weekday_wednesday,
                                                Res.string.weekday_thursday,
                                                Res.string.weekday_friday,
                                                Res.string.weekday_saturday,
                                                Res.string.weekday_sunday,
                                            )[slot.dayOfWeek.ordinal]
                                            Text(
                                                stringResource(
                                                    Res.string.group_schedule_slot,
                                                    stringResource(weekday),
                                                    slot.startsAt.toString(),
                                                    slot.endsAt.toString(),
                                                    schedule.timeZoneId,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                    if (count >
                                        0
                                    ) {
                                        Text(
                                            stringResource(Res.string.group_delete_not_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        onClear()
                                        requestedId = item.id
                                    },
                                    enabled = !saving && count == 0,
                                    modifier = Modifier.testTag("delete-group-${item.id}"),
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.ic_close),
                                        stringResource(Res.string.group_delete_action, item.name),
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = onClose, enabled = !saving) { Text(stringResource(Res.string.class_close)) }
            },
        )
    }
}
