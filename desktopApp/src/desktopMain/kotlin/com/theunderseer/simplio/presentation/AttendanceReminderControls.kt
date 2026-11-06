package com.theunderseer.simplio.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.theunderseer.simplio.desktop.resources.Res
import com.theunderseer.simplio.desktop.resources.attendance_reminders
import com.theunderseer.simplio.desktop.resources.attendance_reminders_failed
import com.theunderseer.simplio.desktop.resources.attendance_reminders_permission
import com.theunderseer.simplio.desktop.resources.attendance_reminders_retry
import com.theunderseer.simplio.notifications.DesktopAttendanceReminders
import com.theunderseer.simplio.shared.presentation.components.SimplioTextButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AttendanceReminderControls(reminders: DesktopAttendanceReminders, account: String) {
    val settings by reminders.settings.collectAsState()
    if (settings.account != account) return

    var busy by remember(account) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val label = stringResource(Res.string.attendance_reminders)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    busy = true
                    scope.launch {
                        try {
                            reminders.setEnabled(account, enabled)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
        if (settings.permissionDenied) {
            Text(
                stringResource(Res.string.attendance_reminders_permission),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (settings.failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.attendance_reminders_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                SimplioTextButton(onClick = reminders::retry) {
                    Text(stringResource(Res.string.attendance_reminders_retry))
                }
            }
        }
    }
}
