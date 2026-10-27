package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.ClassHistoryStudent
import com.theunderseer.simplio.feature.attendance.domain.model.ClassSessionStatus
import com.theunderseer.simplio.feature.attendance.domain.model.StudentGroup
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.class_status_cancelled
import com.theunderseer.simplio.feature.attendance.resources.class_status_held
import com.theunderseer.simplio.feature.attendance.resources.class_status_no_attendance
import com.theunderseer.simplio.feature.attendance.resources.class_status_schedule
import com.theunderseer.simplio.feature.attendance.resources.class_status_unrecorded
import com.theunderseer.simplio.feature.attendance.resources.class_track_attendance
import com.theunderseer.simplio.feature.attendance.resources.class_track_schedule
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClassHistorySection(
    state: ClassHistoryUiState,
    studentGroups: List<StudentGroup>,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onTrackAttendance: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("class-history-list"),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        classHistoryItems(
            state = state,
            studentGroups = studentGroups,
            onLoadNextPage = onLoadNextPage,
            onRetry = onRetry,
            onTrackAttendance = onTrackAttendance,
        )
    }
}

internal fun LazyListScope.classHistoryItems(
    state: ClassHistoryUiState,
    studentGroups: List<StudentGroup>,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onTrackAttendance: (String) -> Unit,
) {
    item(key = "history-header", contentType = "history-header") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp)
                .testTag("class-history"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider()

            Text(
                text = "Class History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    when (state) {
        ClassHistoryUiState.Loading -> {
            item(key = "history-loading", contentType = "history-loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("class-history-loading"),
                    )
                }
            }
        }

        is ClassHistoryUiState.Error -> {
            item(key = "history-error", contentType = "history-error") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                    )

                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }

        is ClassHistoryUiState.Content -> {
            val groupNames = studentGroups.associate { group ->
                group.id to group.name
            }

            if (state.entries.isEmpty()) {
                item(key = "history-empty", contentType = "history-empty") {
                    Text(
                        text = "No class history yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(
                items = state.entries.toClassHistoryDays(),
                key = { day -> "history-entry-${day.date.toEpochDays()}" },
                contentType = { "history-entry" },
            ) { day ->
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    ClassHistoryDayCard(
                        day = day,
                        groupNames = groupNames,
                        onTrackAttendance = onTrackAttendance,
                    )
                }
            }

            if (state.nextBeforeDate != null) {
                item(key = "history-pagination", contentType = "history-pagination") {
                    ClassHistoryPagination(
                        state = state,
                        onLoadNextPage = onLoadNextPage,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassHistoryDayCard(
    day: ClassHistoryDayUiState,
    groupNames: Map<Int, String>,
    onTrackAttendance: (String) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("class-history-entry-${day.date.toEpochDays()}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = day.date.toClassHistoryDisplayDate(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = visitCountLabel(day.visitCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            day.blocks.forEach { block ->
                key(block.key) {
                    HorizontalDivider()
                    val timeLabel = block.timeLabel ?: if (day.blocks.size > 1) "Time not recorded" else null
                    timeLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    block.sessions.filter { it.canTrackAttendance }.forEach { entry ->
                        TextButton(
                            onClick = { entry.sessionId?.let(onTrackAttendance) },
                            modifier = Modifier.testTag("track-class-${entry.key}"),
                        ) {
                            Text(
                                if (block.sessions.size > 1 && entry.scheduleName != null) {
                                    stringResource(Res.string.class_track_schedule, entry.scheduleName)
                                } else {
                                    stringResource(Res.string.class_track_attendance)
                                },
                            )
                        }
                    }
                    if (block.sessions.size > 1 && block.sessions.map { it.status }.distinct().size > 1) {
                        block.sessions.filter { it.status != ClassSessionStatus.UNRECORDED }.forEach { entry ->
                            Text(
                                stringResource(
                                    Res.string.class_status_schedule,
                                    entry.scheduleName ?: entry.timeLabel.orEmpty(),
                                    stringResource(
                                        if (entry.status == ClassSessionStatus.CANCELLED) {
                                            Res.string.class_status_cancelled
                                        } else {
                                            Res.string.class_status_held
                                        },
                                    ),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (block.students.isEmpty() &&
                        (
                            block.sessions.map { it.status }.distinct().size == 1 ||
                                block.sessions.any { it.status == ClassSessionStatus.UNRECORDED }
                            )
                    ) {
                        Text(
                            text = stringResource(
                                when {
                                    block.sessions.all {
                                        it.status == ClassSessionStatus.CANCELLED
                                    } -> Res.string.class_status_cancelled
                                    block.sessions.all {
                                        it.status == ClassSessionStatus.HELD
                                    } -> Res.string.class_status_no_attendance
                                    else -> Res.string.class_status_unrecorded
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        block.students.forEach { attendance ->
                            key(attendance.student.id) {
                                ClassHistoryStudentRow(attendance, groupNames)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassHistoryStudentRow(attendance: ClassHistoryStudent, groupNames: Map<Int, String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attendance.student.fullName,
                style = MaterialTheme.typography.bodyMedium,
            )
            groupNames[attendance.student.groupId]?.let { groupName ->
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = visitCountLabel(attendance.visitCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClassHistoryPagination(
    state: ClassHistoryUiState.Content,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
) {
    val errorMessage = state.nextPageErrorMessage

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isLoadingNextPage) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .testTag("class-history-next-page-loading"),
                    strokeWidth = 2.dp,
                )
            }

            TextButton(
                onClick = if (errorMessage == null) {
                    onLoadNextPage
                } else {
                    onRetry
                },
                enabled = !state.isLoadingNextPage,
            ) {
                Text(
                    text = if (errorMessage == null) {
                        "Load more"
                    } else {
                        "Retry"
                    },
                )
            }
        }
    }
}

private fun visitCountLabel(count: Int): String = "$count ${if (count == 1) "visit" else "visits"}"

private fun LocalDate.toClassHistoryDisplayDate(): String {
    val weekday = dayOfWeek.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }

    return "${toDisplayDate()}, $year ($weekday)"
}
