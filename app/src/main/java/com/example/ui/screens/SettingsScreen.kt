package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.AutoBackupFrequency
import com.example.engine.AppUpdateChecker
import com.example.engine.Formatters
import com.example.engine.NotificationChannels
import com.example.ui.MachinaUiState
import com.example.ui.components.EngineerPickerField
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMachinaColors
import java.io.File

@Composable
fun SettingsScreen(
    state: MachinaUiState,
    onNavigateBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onTriggerReminderCheck: () -> Unit,
    onSetAutoBackupFrequency: (AutoBackupFrequency) -> Unit,
    onPickAutoBackupFolder: () -> Unit,
    onRunAutoBackupNow: () -> Unit,
    onExportManualBackupFile: () -> Unit,
    onShareManualBackupFile: () -> Unit,
    onImportBackupFile: () -> Unit,
    onRestoreLocalBackup: (File) -> Unit,
    onShareLocalBackup: (File) -> Unit,
    onImportExcelFile: () -> Unit,
    onSaveExcelTemplate: () -> Unit,
    onSetEngineerName: (String) -> Unit,
    onResetSampleData: () -> Unit,
    onOpenPcWebPortal: () -> Unit,
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPermission = NotificationChannels.hasNotificationPermission(context)
    val sem = LocalMachinaColors.current

    BackHandler(onBack = onNavigateBack)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list")
    ) {
        // Header Card
        item(key = "settings_header") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settings, Sync & Updates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "MachinaLog v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1. Live PC Browser Portal (Local Wi-Fi)
        item(key = "settings_pc_portal") {
            SettingsSectionCard(
                title = "PC Browser Web Portal (Local Wi-Fi)",
                subtitle = if (state.webPortalStatus.isRunning) {
                    "Online · ${state.webPortalStatus.primaryUrl ?: "Port ${state.webPortalStatus.port}"}"
                } else {
                    "Offline · Access & edit MachinaLog from any PC browser on the same Wi-Fi"
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = if (state.webPortalStatus.isRunning) sem.operational else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPill(
                        text = if (state.webPortalStatus.isRunning) "ONLINE" else "OFFLINE",
                        containerColor = if (state.webPortalStatus.isRunning) sem.operationalContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (state.webPortalStatus.isRunning) sem.operational else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onOpenPcWebPortal,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("sync_open_pc_web_button")
                    ) {
                        Text(if (state.webPortalStatus.isRunning) "Manage Portal" else "Start Portal")
                    }
                }
            }
        }

        // 2. Excel Import (.xlsx)
        item(key = "settings_excel") {
            SettingsSectionCard(
                title = "Excel Import (.xlsx) · Auto-Deduplication",
                subtitle = "Import updated lists for Warehouse Inventory, Machine List, Maintenance Tasks, To-Do Reminders, and Custom Groups."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onImportExcelFile,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("import_excel_button")
                    ) {
                        Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import Excel")
                    }
                    OutlinedButton(
                        onClick = onSaveExcelTemplate,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("save_excel_template_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Template")
                    }
                }
            }
        }

        // 3. Portable Backup & Restore
        item(key = "settings_backup") {
            SettingsSectionCard(
                title = "Backup, Restore & PC File Sync",
                subtitle = "Save a portable .json backup to transfer to a PC or another Android device, or merge/restore from a saved file."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onExportManualBackupFile,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("export_backup_file_button")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Backup")
                    }
                    OutlinedButton(
                        onClick = onShareManualBackupFile,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("share_backup_file_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Send to PC")
                    }
                }
                OutlinedButton(
                    onClick = onImportBackupFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("import_backup_file_button")
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore from Backup (.json)")
                }
            }
        }

        // 4. Automatic Scheduled Backup
        item(key = "settings_autobackup") {
            SettingsSectionCard(
                title = "Automatic Scheduled Backup",
                subtitle = state.preferences.lastAutoBackupMillis?.let {
                    "Last auto-backup: ${Formatters.formatDateTime(it)}"
                } ?: "Schedule background .json snapshots on your device or a linked folder."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    AutoBackupFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = state.preferences.autoBackupFrequency == freq,
                            onClick = { onSetAutoBackupFrequency(freq) },
                            label = { Text(freq.label) },
                            modifier = Modifier.testTag("autobackup_freq_${freq.name.lowercase()}")
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onPickAutoBackupFolder,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.preferences.autoBackupTreeUri != null) "Folder Linked" else "Link Folder")
                    }
                    OutlinedButton(
                        onClick = onRunAutoBackupNow,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("run_autobackup_now_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Backup Now")
                    }
                }

                if (state.localBackups.isNotEmpty()) {
                    Text("Recent Auto-Backup Snapshots", style = MaterialTheme.typography.labelLarge)
                    state.localBackups.take(5).forEach { file ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { onRestoreLocalBackup(file) },
                                    modifier = Modifier.testTag("restore_snapshot_${file.name}")
                                ) {
                                    Text("Restore")
                                }
                                TextButton(onClick = { onShareLocalBackup(file) }) {
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Maintenance & New Task Notifications
        item(key = "settings_notifications") {
            SettingsSectionCard(
                title = "Notifications & New Task Alerts",
                subtitle = if (hasPermission) {
                    "Notification permission granted · Alerts for new tasks, approaching deadlines, and reminders"
                } else {
                    "Notification permission required on Android 13+ for deadline & new task alerts"
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System Task, Reminder & New Task Alerts", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Notifies when new tasks arrive from Phone, PC Web, or Excel, or deadlines approach.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.preferences.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasPermission) {
                                onRequestNotificationPermission()
                            }
                            onSetNotificationsEnabled(enabled)
                        },
                        modifier = Modifier.testTag("settings_notifications_switch")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!hasPermission) {
                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Grant Permission")
                        }
                    }
                    OutlinedButton(
                        onClick = onTriggerReminderCheck,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("test_notification_button")
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Check Alerts Now")
                    }
                }
            }
        }

        // 6. GitHub App Updates
        item(key = "settings_updates") {
            SettingsSectionCard(
                title = "App Updates (GitHub: ${AppUpdateChecker.GITHUB_REPO})",
                subtitle = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-check updates on launch", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Queries GitHub Releases (including pre-releases) & notifies when a newer APK is published.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.preferences.autoCheckUpdates,
                        onCheckedChange = onSetAutoCheckUpdates,
                        modifier = Modifier.testTag("auto_check_updates_switch")
                    )
                }
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = !state.isCheckingUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("check_for_updates_button")
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isCheckingUpdate) "Checking GitHub Releases..." else "Check for Updates Now")
                }
                state.updateStatusText?.let { statusLine ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                state.preferences.lastUpdateCheckMillis?.let { checkedAt ->
                    Text(
                        text = "Last checked: ${Formatters.formatDateTime(checkedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 7. Crew Defaults & Sample Data
        item(key = "settings_defaults") {
            SettingsSectionCard(
                title = "Crew Defaults & Dataset Tools",
                subtitle = "Set the default Person In Charge pre-selected when creating tasks or reminders."
            ) {
                EngineerPickerField(
                    label = "Default Engineer (PIC)",
                    selectedName = state.preferences.defaultEngineerName,
                    engineers = state.engineers,
                    onSelect = onSetEngineerName,
                    testTag = "sync_default_engineer_picker"
                )
                TextButton(
                    onClick = onResetSampleData,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("reset_sample_data_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset / Reload Sample Factory Data")
                }
            }
        }

        item(key = "settings_bottom_spacer") {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}
