package com.karencalulo.wishwait.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karencalulo.wishwait.R
import com.karencalulo.wishwait.presentation.common.Fmt

/**
 * Optional Google Drive backup: sign in with Google, and WishWait keeps a backup in a hidden
 * folder of the person's own Drive. Off by default; WishWait works fully without it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveBackupCard(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    val backup = state.backup
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁️", fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (backup.driveConnected) R.string.drive_title_on else R.string.drive_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (backup.driveConnected) {
                        Text(
                            backup.driveAccount ?: stringResource(R.string.drive_account_unknown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.driveBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            if (backup.driveConnected) {
                Text(
                    backup.lastDriveBackupAt?.let { stringResource(R.string.drive_last_backup, Fmt.dateTime(it)) }
                        ?: stringResource(R.string.drive_not_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    stringResource(R.string.drive_on_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onIntent(SettingsIntent.DriveBackUpNow) }, enabled = !state.driveBusy) {
                        Text(stringResource(R.string.drive_backup_now))
                    }
                    OutlinedButton(onClick = { onIntent(SettingsIntent.DriveRestore) }, enabled = !state.driveBusy) {
                        Text(stringResource(R.string.drive_restore))
                    }
                    TextButton(onClick = { onIntent(SettingsIntent.DriveTurnOffRequested) }, enabled = !state.driveBusy) {
                        Text(stringResource(R.string.drive_turn_off))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.drive_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onIntent(SettingsIntent.DriveSignIn) },
                    enabled = !state.driveBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.drive_sign_in)) }
            }
        }
    }
}

/** Weekly "back up your wishes" nudge. Only offered when Drive backup is off (Drive backs up by itself). */
@Composable
fun BackupReminderRow(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    if (state.backup.driveConnected) return
    val on = state.backup.reminderEnabled
    ListItem(
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = { onIntent(SettingsIntent.BackupReminderToggled(it)) }),
        headlineContent = { Text(stringResource(R.string.backup_reminder)) },
        supportingContent = {
            Text(
                state.backup.lastBackupAt?.let { stringResource(R.string.backup_reminder_desc_last, Fmt.date(it)) }
                    ?: stringResource(R.string.backup_reminder_desc),
            )
        },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
    )
}

@Composable
fun DriveDialogs(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    state.driveFound?.let { found ->
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialogs) },
            icon = { Text("☁️", fontSize = 32.sp, modifier = Modifier.clearAndSetSemantics { }) },
            title = { Text(stringResource(R.string.drive_found_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.drive_found_body,
                        Fmt.dateTime(found.modifiedAt),
                        found.backup.wishes.size,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { onIntent(SettingsIntent.DriveFoundRestore) }) { Text(stringResource(R.string.drive_found_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DriveFoundKeepPhone) }) { Text(stringResource(R.string.drive_found_keep)) }
            },
        )
    }

    if (state.showDriveOff) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.DismissDialogs) },
            title = { Text(stringResource(R.string.drive_off_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.drive_off_body))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.deleteDriveBackup,
                                role = Role.Checkbox,
                                onValueChange = { onIntent(SettingsIntent.DriveDeleteToggled(it)) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = state.deleteDriveBackup, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.drive_off_delete))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DriveTurnOffConfirmed) }) {
                    Text(
                        stringResource(R.string.drive_turn_off),
                        color = if (state.deleteDriveBackup) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.DismissDialogs) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
