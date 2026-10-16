package com.z.grow.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.grow.data.Backup
import com.z.grow.work.Notifications
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BackupTime = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm")

@Composable
fun SettingsScreen(vm: GrowViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }

    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) vm.updateSettings { it.copy(remindersEnabled = true) }
    }

    fun runBackup(uri: Uri) {
        busy = "Backing up…"
        scope.launch {
            val result = vm.backupTo(uri)
            busy = null
            snackbar.showSnackbar(
                result.fold({ "Backup saved" }, { "Backup failed: ${it.message ?: "unknown error"}" }),
            )
        }
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep access so later and automatic backups can overwrite the same file.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        runBackup(uri)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    val newBackupFile = { createBackup.launch("grow-with-green-backup-${LocalDate.now()}.zip") }

    Scaffold(
        topBar = { GrowTopBar("Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Reminders")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily watering & feeding reminders", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "One notification listing what's due that day, plus light changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.remindersEnabled,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings { it.copy(remindersEnabled = on) }
                        }
                    },
                )
            }
            if (permissionDenied) {
                Text(
                    "Notifications are blocked. Allow them for Grow with Green in your phone's Settings to get reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (settings.remindersEnabled) {
                TimeField("Remind me at", settings.reminderMinute) { minute ->
                    vm.updateSettings { it.copy(reminderMinute = minute) }
                }
                Text(
                    "Turn reminders off for individual plants on each plant's page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("Backup to Google Drive")
            Text(
                "Saves all plants, spaces, logs, journal entries and photos into one file. " +
                    "When the file picker opens, choose Google Drive (the Drive app must be installed and signed in). " +
                    "After reinstalling, use Restore and pick the same file.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val last = settings.lastBackupMillis.takeIf { it > 0 }?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(BackupTime)
            }
            Text(
                last?.let { "Last backup: $it" } ?: "No backup yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val backupUri = settings.backupUri
            if (backupUri == null) {
                Button(onClick = newBackupFile, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Back up now")
                }
            } else {
                Button(onClick = { runBackup(backupUri) }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Back up now")
                }
                OutlinedButton(onClick = newBackupFile, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Save to a new backup file")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Back up automatically every week", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Overwrites the backup file you last saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.autoBackup,
                        onCheckedChange = { on -> vm.updateSettings { it.copy(autoBackup = on) } },
                    )
                }
            }
            OutlinedButton(
                onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore from backup") }

            busy?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(it)
                }
            }
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything currently in the app will be replaced by the backup. The app will restart when it's done.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    busy = "Restoring…"
                    scope.launch {
                        vm.restoreFrom(uri).fold(
                            onSuccess = { Backup.restartApp(context) },
                            onFailure = {
                                busy = null
                                snackbar.showSnackbar("Restore failed: ${it.message ?: "unknown error"}")
                            },
                        )
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }
}
