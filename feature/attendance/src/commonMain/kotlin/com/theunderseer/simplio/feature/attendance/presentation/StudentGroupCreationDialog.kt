package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSchedule
import com.theunderseer.simplio.feature.attendance.domain.model.CreateStudentGroupRequest
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.domain.model.WeeklyClassSlot
import com.theunderseer.simplio.feature.attendance.domain.model.isValidGroupName
import com.theunderseer.simplio.feature.attendance.domain.model.normalizedGroupName
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudentGroupCreationDialog(
    groups: List<StudentGroup>,
    schedules: List<ClassSchedule>,
    today: LocalDate,
    state: CreateStudentGroupUiState,
    onCreate: (CreateStudentGroupRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val eligible = schedules.filter { it.slots.groupBy { slot -> slot.dayOfWeek }.all { it.value.size == 1 } }
    var newSchedule by remember { mutableStateOf(eligible.isEmpty()) }
    var selectedId by remember { mutableStateOf(eligible.firstOrNull()?.id) }
    var expanded by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:15") }
    var zone by remember { mutableStateOf(TimeZone.currentSystemDefault().id) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var chooseDate by remember { mutableStateOf<String?>(null) }
    var chooseTime by remember { mutableStateOf<String?>(null) }
    val seriesId = remember { Uuid.random().toHexString() }
    val scroll = rememberScrollState()
    val saving = state == CreateStudentGroupUiState.Saving
    val duplicate = name.isNotBlank() && groups.any { normalizedGroupName(it.name) == normalizedGroupName(name) }
    val invalidName = name.isNotBlank() && !isValidGroupName(name)
    val draft = if (newSchedule && isValidGroupName(name)) {
        runCatching {
            ClassSchedule(
                id = seriesId,
                name = name.trim(),
                timeZoneId = zone.trim(),
                startsOn = startDate,
                endsOn = endDate,
                slots = days.sortedBy { it.ordinal }.map {
                    WeeklyClassSlot(it.name.lowercase(), it, LocalTime.parse(startTime), LocalTime.parse(endTime))
                },
            )
        }.getOrNull()
    } else {
        null
    }
    val selected = eligible.firstOrNull { it.id == selectedId }
    val canSave = !saving &&
        isValidGroupName(name) &&
        !duplicate &&
        (if (newSchedule) draft != null else selected != null)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Column {
                Text("Add Group")
                if (scroll.canScrollForward) Text("Scroll for more", style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scroll).testTag("group-form"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group-name"),
                    label = { Text("Group name") },
                    isError = duplicate || invalidName,
                    supportingText = {
                        if (duplicate) {
                            Text("A group with this name already exists")
                        } else if (invalidName) {
                            Text("Use 1–80 characters")
                        }
                    },
                )
                Text("Weekly schedule", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !newSchedule,
                        onClick = { newSchedule = false },
                        enabled =
                        !saving && eligible.isNotEmpty(),
                        label = { Text("Use existing") },
                    )
                    FilterChip(
                        selected = newSchedule,
                        onClick = { newSchedule = true },
                        enabled = !saving,
                        label = { Text("New schedule") },
                    )
                }
                if (!newSchedule) {
                    Column {
                        OutlinedButton(onClick = { expanded = true }, enabled = !saving) {
                            Text(selected?.name ?: "Choose schedule")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.heightIn(max = 240.dp),
                        ) {
                            eligible.forEach { schedule ->
                                DropdownMenuItem(text = { Text(schedule.name) }, onClick = {
                                    selectedId = schedule.id
                                    expanded = false
                                })
                            }
                        }
                    }
                    selected?.let { schedule ->
                        Text(
                            schedule.slots.sortedBy { it.dayOfWeek.ordinal }.joinToString { slot ->
                                "${slot.dayOfWeek.name.lowercase().replaceFirstChar {
                                    it.uppercase()
                                }} ${slot.startsAt}–${slot.endsAt}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(schedule.timeZoneId, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Groups using this schedule attend the same classes.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                enabled = !saving,
                                onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.testTag("group-day-${day.name}"),
                            )
                        }
                    }
                    if (days.isEmpty()) Text("Choose at least one day", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleTimeField(
                            "Starts at",
                            startTime,
                            !saving,
                            { startTime = it },
                            { chooseTime = "start" },
                            Modifier.weight(1f),
                        )
                        ScheduleTimeField(
                            "Ends at",
                            endTime,
                            !saving,
                            { endTime = it },
                            { chooseTime = "end" },
                            Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = zone,
                        onValueChange = { zone = it },
                        singleLine = true,
                        enabled = !saving,
                        label = { Text("Time zone") },
                        modifier = Modifier.fillMaxWidth().testTag("group-time-zone"),
                        isError = runCatching { TimeZone.of(zone.trim()) }.isFailure,
                        supportingText = { Text("Venue time zone, e.g. Asia/Bangkok") },
                    )
                    TextButton(
                        onClick = { chooseDate = "start" },
                        enabled = !saving,
                        modifier = Modifier.testTag("group-start-date"),
                    ) { Text("Starts on: $startDate") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = endDate != null,
                            enabled = !saving,
                            onCheckedChange = { endDate = if (it) startDate else null },
                        )
                        Text("End date")
                        endDate?.let {
                            TextButton(onClick = { chooseDate = "end" }, enabled = !saving) { Text(it.toString()) }
                        }
                    }
                    if (draft == null && name.isNotBlank() && days.isNotEmpty()) {
                        Text("Check times, time zone and dates", color = MaterialTheme.colorScheme.error)
                    }
                }
                (state as? CreateStudentGroupUiState.Error)?.let {
                    Text(it.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = canSave, onClick = {
                if (canSave) onCreate(CreateStudentGroupRequest(name, if (newSchedule) null else selectedId, draft))
            }) { Text(if (saving) "Saving..." else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
    chooseDate?.let { field ->
        val initial = if (field == "start") startDate else endDate ?: startDate
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            )
        ClassDatePickerDialog(
            title = if (field == "start") "Schedule starts on" else "Schedule ends on",
            state = picker,
            canConfirm = picker.selectedDateMillis != null,
            onDismiss = { chooseDate = null },
            onConfirm = {
                picker.selectedDateMillis?.let {
                    val date = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    if (field == "start") startDate = date else endDate = date
                }
                chooseDate = null
            },
        )
    }
    chooseTime?.let { field ->
        val initial = runCatching {
            LocalTime.parse(
                if (field ==
                    "start"
                ) {
                    startTime
                } else {
                    endTime
                },
            )
        }.getOrDefault(LocalTime(9, 0))
        val picker = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { chooseTime = null },
            title = {
                Text(
                    if (field ==
                        "start"
                    ) {
                        "Start time"
                    } else {
                        "End time"
                    },
                )
            },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(onClick = {
                    val value = LocalTime(picker.hour, picker.minute).toString()
                    if (field == "start") startTime = value else endTime = value
                    chooseTime = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { chooseTime = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduleTimeField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        enabled = enabled,
        modifier = modifier.testTag(label),
        label = { Text(label) },
        supportingText = { Text("HH:mm") },
        trailingIcon = { TextButton(onClick = onChoose, enabled = enabled) { Text("Pick") } },
    )
}
