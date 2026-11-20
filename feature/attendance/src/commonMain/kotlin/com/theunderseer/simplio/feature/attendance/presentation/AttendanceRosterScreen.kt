package com.theunderseer.simplio.feature.attendance.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.feature.attendance.domain.model.AttendanceRoster
import com.theunderseer.simplio.feature.attendance.domain.schedule.AttendanceCheckState
import com.theunderseer.simplio.feature.attendance.resources.Res
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

internal sealed interface AttendanceRosterState {
    data object Loading : AttendanceRosterState
    data class Content(val roster: AttendanceRoster) : AttendanceRosterState
    data class Error(val message: StringResource) : AttendanceRosterState
}

@Suppress("FunctionNaming") // Compose UI functions use PascalCase.
@Composable
internal fun AttendanceRosterScreen(
    state: AttendanceRosterState,
    now: Instant,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.roster_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onRefresh, enabled = state is AttendanceRosterState.Content) {
                Text(stringResource(Res.string.roster_refresh))
            }
            TextButton(onClick = onSignOut) { Text(stringResource(Res.string.roster_sign_out)) }
        }
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
            is AttendanceRosterState.Content -> AttendanceRosterRows(state.roster, now)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AttendanceRosterRows(roster: AttendanceRoster, now: Instant) {
    val rows = remember(roster, now) { roster.toRows(now) }
    if (rows.isEmpty()) {
        RosterStatus { Text(stringResource(Res.string.roster_empty)) }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.registeredStudent.student.id }) { row ->
                AttendanceRosterRow(row)
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
private fun AttendanceRosterRow(row: StudentAttendanceUiState) {
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
    val action = when {
        row.requiresRenewal -> Res.string.roster_renew
        row.checkState == AttendanceCheckState.Checked -> Res.string.roster_checked
        else -> Res.string.roster_check
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(student.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$used/$total",
                modifier = Modifier.clearAndSetSemantics { contentDescription = visits },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(expiration, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(action)) }
    }
}
