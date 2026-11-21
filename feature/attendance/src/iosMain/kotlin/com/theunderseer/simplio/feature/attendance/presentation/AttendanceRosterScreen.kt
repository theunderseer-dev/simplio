package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.resources.Res
import com.theunderseer.simplio.feature.attendance.resources.ic_check
import com.theunderseer.simplio.feature.attendance.resources.ic_refresh
import com.theunderseer.simplio.feature.attendance.resources.ios_mark_student_present
import com.theunderseer.simplio.feature.attendance.resources.ios_remove_student_attendance
import com.theunderseer.simplio.feature.attendance.resources.ios_renew_student_package
import com.theunderseer.simplio.feature.attendance.resources.package_no_expiration
import com.theunderseer.simplio.feature.attendance.resources.roster_check
import com.theunderseer.simplio.feature.attendance.resources.roster_checked
import com.theunderseer.simplio.feature.attendance.resources.roster_empty
import com.theunderseer.simplio.feature.attendance.resources.roster_expires_on
import com.theunderseer.simplio.feature.attendance.resources.roster_loading
import com.theunderseer.simplio.feature.attendance.resources.roster_not_started
import com.theunderseer.simplio.feature.attendance.resources.roster_refresh
import com.theunderseer.simplio.feature.attendance.resources.roster_renew
import com.theunderseer.simplio.feature.attendance.resources.roster_retry
import com.theunderseer.simplio.feature.attendance.resources.roster_sign_out
import com.theunderseer.simplio.feature.attendance.resources.roster_title
import com.theunderseer.simplio.feature.attendance.resources.roster_visits_used
import com.theunderseer.simplio.shared.presentation.components.SimplioActionTone
import com.theunderseer.simplio.shared.presentation.components.SimplioIconButton
import com.theunderseer.simplio.shared.presentation.theme.positive
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun AttendanceRosterScreen(
    state: AttendanceRosterState,
    now: Instant,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onEdit: suspend (AttendanceRosterEdit) -> String?,
    syncNotice: @Composable () -> Unit = {},
) {
    var dialog by remember { mutableStateOf<StudentAttendanceUiState?>(null) }
    val showTrigger = state is AttendanceRosterState.Content
    var showQuickActions by remember(showTrigger) { mutableStateOf(false) }
    val reduceMotion = rememberIosReduceMotion()
    val contentSemantics = if (showQuickActions) Modifier.clearAndSetSemantics {} else Modifier
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().then(contentSemantics)) {
            syncNotice()
            AttendanceRosterHeader(showTrigger, onRefresh, onSignOut)
            HorizontalDivider()
            when (state) {
                AttendanceRosterState.Loading -> RosterStatus {
                    val loading = stringResource(Res.string.roster_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                }
                is AttendanceRosterState.Error -> RosterStatus {
                    Text(stringResource(state.message))
                    TextButton(onClick = onRefresh) { Text(stringResource(Res.string.roster_retry)) }
                }
                is AttendanceRosterState.Content -> {
                    AttendanceRosterRows(state.roster, now, reduceMotion) { row -> dialog = row }
                    dialog?.let { selected ->
                        AttendanceRosterActionDialog(selected, state.roster, now, onEdit) { dialog = null }
                    }
                }
            }
        }
        if (showTrigger) {
            IosAttendanceQuickActions(
                expanded = showQuickActions,
                reduceMotion = reduceMotion,
                onExpandedChange = { showQuickActions = it },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterHeader(canRefresh: Boolean, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.roster_title),
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        TextButton(onClick = onRefresh, enabled = canRefresh) {
            Text(stringResource(Res.string.roster_refresh))
        }
        TextButton(onClick = onSignOut) { Text(stringResource(Res.string.roster_sign_out)) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterRows(
    roster: AttendanceRoster,
    now: Instant,
    reduceMotion: Boolean,
    onAction: (StudentAttendanceUiState) -> Unit,
) {
    val rows = remember(roster, now) { roster.toRows(now) }
    if (rows.isEmpty()) {
        RosterStatus { Text(stringResource(Res.string.roster_empty)) }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = quickActionListClearance)) {
            items(rows, key = { it.registeredStudent.student.id }) { row ->
                AttendanceRosterRow(row, reduceMotion, onAction = { onAction(row) })
                HorizontalDivider()
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RosterStatus(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterRow(row: StudentAttendanceUiState, reduceMotion: Boolean, onAction: () -> Unit) {
    val student = row.registeredStudent.student
    val purchase = row.registeredStudent.studentPackage
    val total = purchase.packageType.totalClasses
    val used = purchase.usedClasses
    val visits = stringResource(Res.string.roster_visits_used, used, total)
    val expiryDate = purchase.expiresOn
    val expiration = when {
        purchase.packageType.validityWeeks == null -> stringResource(Res.string.package_no_expiration)
        expiryDate == null -> stringResource(Res.string.roster_not_started)
        else -> stringResource(Res.string.roster_expires_on, expiryDate.toDisplayDate())
    }
    val textColor = if (row.requiresRenewal) MaterialTheme.colorScheme.positive else Color.Unspecified
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(student.fullName, color = textColor, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$used/$total",
                modifier = Modifier.clearAndSetSemantics { contentDescription = visits },
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(expiration, color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        AttendanceRosterAction(row, reduceMotion, onAction)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterAction(row: StudentAttendanceUiState, reduceMotion: Boolean, onAction: () -> Unit) {
    val checked = !row.requiresRenewal && row.checkState == AttendanceCheckState.Checked
    val (description, status) = when {
        row.requiresRenewal -> Res.string.ios_renew_student_package to Res.string.roster_renew
        checked -> Res.string.ios_remove_student_attendance to Res.string.roster_checked
        else -> Res.string.ios_mark_student_present to Res.string.roster_check
    }
    val rotation by animateFloatAsState(
        targetValue = if (row.requiresRenewal) 360f else 0f,
        animationSpec = if (reduceMotion || !row.requiresRenewal) snap() else tween(260),
    )
    val state = stringResource(status)
    SimplioIconButton(
        painter = painterResource(if (row.requiresRenewal) Res.drawable.ic_refresh else Res.drawable.ic_check),
        contentDescription = stringResource(description, row.registeredStudent.student.fullName),
        onClick = onAction,
        modifier = Modifier.rotate(rotation).semantics { stateDescription = state },
        enabled = row.isCheckEnabled,
        tone = if (row.requiresRenewal || checked) SimplioActionTone.Positive else SimplioActionTone.Neutral,
        selected = checked,
    )
}
