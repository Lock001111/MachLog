package com.example.ui.sheets

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AutoBackupFrequency
import com.example.data.Cadence
import com.example.data.ServiceEntryType
import com.example.data.TaskOutcome
import com.example.engine.CategoryDiffStats
import com.example.engine.ExcelImportPreview
import com.example.engine.ExportFilterParams
import com.example.engine.Formatters
import com.example.engine.NotificationChannels
import com.example.engine.PortableBackupPackage
import com.example.engine.ReportExportEngine
import com.example.engine.RestoreMode
import com.example.engine.WebPortalStatus
import com.example.ui.MachinaUiState
import com.example.ui.components.EngineerPickerField
import java.io.File
import java.util.Calendar

@Composable
fun ExportReportDialog(
    state: MachinaUiState,
    initialParams: ExportFilterParams,
    preferPdf: Boolean,
    onDismiss: () -> Unit,
    onUpdateParams: (ExportFilterParams) -> Unit,
    onSaveToDevice: (isPdf: Boolean, params: ExportFilterParams) -> Unit,
    onShareReport: (isPdf: Boolean, params: ExportFilterParams) -> Unit
) {
    val context = LocalContext.current
    var params by remember { mutableStateOf(initialParams) }
    var isPdf by remember { mutableStateOf(preferPdf) }

    var showMachineMenu by remember { mutableStateOf(false) }
    var showPicMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    val matchedDataset = remember(params, state.machines, state.groups, state.tasks, state.serviceLogs) {
        ReportExportEngine.filterDataset(
            params = params,
            machines = state.machines,
            groups = state.groups,
            allTasks = state.tasks,
            allLogs = state.serviceLogs
        )
    }

    fun pickDate(current: Long?, isEnd: Boolean, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply {
            if (current != null) timeInMillis = current
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    if (isEnd) {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Report (Excel & PDF)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isPdf,
                        onClick = { isPdf = false },
                        leadingIcon = { Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Excel (.xlsx)") },
                        modifier = Modifier.testTag("export_format_excel")
                    )
                    FilterChip(
                        selected = isPdf,
                        onClick = { isPdf = true },
                        leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("PDF Document") },
                        modifier = Modifier.testTag("export_format_pdf")
                    )
                }

                Text("Report Content", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = params.includeTasks,
                        onCheckedChange = {
                            params = params.copy(includeTasks = it)
                            onUpdateParams(params)
                        }
                    )
                    Text("Scheduled Tasks (${matchedDataset.tasks.size})")
                    Spacer(Modifier.width(8.dp))
                    Checkbox(
                        checked = params.includeLogs,
                        onCheckedChange = {
                            params = params.copy(includeLogs = it)
                            onUpdateParams(params)
                        }
                    )
                    Text("History (${matchedDataset.logs.size})")
                }

                // Machine Parameter
                Box {
                    val selM = params.machineId?.let { mid -> state.machines.find { it.id == mid } }
                    OutlinedButton(
                        onClick = { showMachineMenu = true },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Text("Machine: ${selM?.let { "${it.assetCode} · ${it.name}" } ?: "All Machines"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMachineMenu, onDismissRequest = { showMachineMenu = false }) {
                        DropdownMenuItem(text = { Text("All Machines") }, onClick = {
                            params = params.copy(machineId = null)
                            onUpdateParams(params)
                            showMachineMenu = false
                        })
                        state.machines.forEach { m ->
                            DropdownMenuItem(text = { Text("${m.assetCode} · ${m.name}") }, onClick = {
                                params = params.copy(machineId = m.id)
                                onUpdateParams(params)
                                showMachineMenu = false
                            })
                        }
                    }
                }

                // PIC Parameter
                Box {
                    OutlinedButton(
                        onClick = { showPicMenu = true },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Text("Person In Charge: ${params.personInCharge ?: "All Engineers"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showPicMenu, onDismissRequest = { showPicMenu = false }) {
                        DropdownMenuItem(text = { Text("All Engineers") }, onClick = {
                            params = params.copy(personInCharge = null)
                            onUpdateParams(params)
                            showPicMenu = false
                        })
                        state.knownEngineers.forEach { pic ->
                            DropdownMenuItem(text = { Text(pic) }, onClick = {
                                params = params.copy(personInCharge = pic)
                                onUpdateParams(params)
                                showPicMenu = false
                            })
                        }
                    }
                }

                // Group Parameter
                Box {
                    val selG = params.customGroupId?.let { gid -> state.groups.find { it.id == gid } }
                    OutlinedButton(
                        onClick = { showGroupMenu = true },
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                    ) {
                        Text("Custom Group: ${selG?.name ?: "All Groups"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        DropdownMenuItem(text = { Text("All Groups") }, onClick = {
                            params = params.copy(customGroupId = null)
                            onUpdateParams(params)
                            showGroupMenu = false
                        })
                        state.groups.forEach { g ->
                            DropdownMenuItem(text = { Text(g.name) }, onClick = {
                                params = params.copy(customGroupId = g.id)
                                onUpdateParams(params)
                                showGroupMenu = false
                            })
                        }
                    }
                }

                // Cadence Parameter
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = params.cadence == null,
                        onClick = {
                            params = params.copy(cadence = null)
                            onUpdateParams(params)
                        },
                        label = { Text("All Cadences") }
                    )
                    Cadence.entries.forEach { cad ->
                        FilterChip(
                            selected = params.cadence == cad,
                            onClick = {
                                params = params.copy(cadence = if (params.cadence == cad) null else cad)
                                onUpdateParams(params)
                            },
                            label = { Text(cad.label) }
                        )
                    }
                }

                // Entry Type Parameter
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = params.entryType == null,
                        onClick = {
                            params = params.copy(entryType = null)
                            onUpdateParams(params)
                        },
                        label = { Text("All Logs") }
                    )
                    ServiceEntryType.entries.forEach { t ->
                        FilterChip(
                            selected = params.entryType == t,
                            onClick = {
                                params = params.copy(entryType = if (params.entryType == t) null else t)
                                onUpdateParams(params)
                            },
                            label = { Text(if (t == ServiceEntryType.PREVENTIVE_SERVICE) "Preventive" else "Malfunctions") }
                        )
                    }
                }

                // Outcome Parameter (Done vs Rejected)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = params.outcome == null,
                        onClick = {
                            params = params.copy(outcome = null)
                            onUpdateParams(params)
                        },
                        label = { Text("All Outcomes") }
                    )
                    TaskOutcome.entries.forEach { out ->
                        FilterChip(
                            selected = params.outcome == out,
                            onClick = {
                                params = params.copy(outcome = if (params.outcome == out) null else out)
                                onUpdateParams(params)
                            },
                            label = { Text(out.label) }
                        )
                    }
                }

                // Time Period From / To
                Text("Time Period", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickDate(params.startMillis, isEnd = false) { picked ->
                                params = params.copy(startMillis = picked)
                                onUpdateParams(params)
                            }
                        },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(params.startMillis?.let { Formatters.formatDateOnly(it) } ?: "From: Any")
                    }
                    OutlinedButton(
                        onClick = {
                            pickDate(params.endMillis, isEnd = true) { picked ->
                                params = params.copy(endMillis = picked)
                                onUpdateParams(params)
                            }
                        },
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(params.endMillis?.let { Formatters.formatDateOnly(it) } ?: "To: Present")
                    }
                }
                if (params.startMillis != null || params.endMillis != null) {
                    TextButton(onClick = {
                        params = params.copy(startMillis = null, endMillis = null)
                        onUpdateParams(params)
                    }) {
                        Text("Clear Date Range")
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ready to export: ${matchedDataset.tasks.size} tasks & ${matchedDataset.logs.size} service/malfunction logs",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onShareReport(isPdf, params) },
                    modifier = Modifier.testTag("confirm_share_report_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                Button(
                    onClick = { onSaveToDevice(isPdf, params) },
                    modifier = Modifier.testTag("confirm_save_report_button")
                ) {
                    Text(if (isPdf) "Save PDF" else "Save Excel")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SyncBackupSettingsDialog(
    state: MachinaUiState,
    onDismiss: () -> Unit,
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
    onOpenPcWebPortal: () -> Unit
) {
    val context = LocalContext.current
    val hasPermission = NotificationChannels.hasNotificationPermission(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync, Backup & Import") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Live PC Browser Portal (Local Wi-Fi)
                Surface(
                    color = if (state.webPortalStatus.isRunning) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Computer,
                            contentDescription = null,
                            tint = if (state.webPortalStatus.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PC Browser Web Portal (Wi-Fi)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (state.webPortalStatus.isRunning) {
                                    "Online · ${state.webPortalStatus.primaryUrl ?: "Port ${state.webPortalStatus.port}"}"
                                } else {
                                    "Off · Open MachinaLog in your PC browser over LAN"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenPcWebPortal,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_open_pc_web_button")
                        ) {
                            Text(if (state.webPortalStatus.isRunning) "Manage" else "Start")
                        }
                    }
                }

                // 0. Continuous & First-Time Setup: Excel Import (.xlsx)
                Text(
                    "Excel Import (.xlsx) · Auto-Deduplication",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Import or re-import updated external lists for Warehouse Inventory, Machine List, Task List, To-Do Reminders, and Groups. Duplicates are checked automatically so only new or changed rows are applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onImportExcelFile,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("import_excel_button")
                    ) {
                        Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import Excel")
                    }
                    OutlinedButton(
                        onClick = onSaveExcelTemplate,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_excel_template_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Template")
                    }
                }

                HorizontalDivider()

                // 1. Portable Manual & PC Sync + Restore from Backup
                Text("Backup, Restore & PC File Sync", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Create a portable .json backup file to transfer to your PC or another Android device, or restore/merge from an existing backup file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onExportManualBackupFile,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("export_backup_file_button")
                    ) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Backup")
                    }
                    OutlinedButton(
                        onClick = onShareManualBackupFile,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_backup_file_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Send to PC")
                    }
                }
                OutlinedButton(
                    onClick = onImportBackupFile,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("import_backup_file_button")
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore from Backup (.json)")
                }

                HorizontalDivider()

                // 2. Automatic Backup
                Text("Automatic Scheduled Backup", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onPickAutoBackupFolder,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.preferences.autoBackupTreeUri != null) "Folder Linked" else "Link Folder")
                    }
                    OutlinedButton(
                        onClick = onRunAutoBackupNow,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("run_autobackup_now_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Backup Now")
                    }
                }
                state.preferences.lastAutoBackupMillis?.let { last ->
                    Text(
                        text = "Last auto-backup: ${Formatters.formatDateTime(last)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.localBackups.isNotEmpty()) {
                    Text("Recent Auto-Backup Snapshots", style = MaterialTheme.typography.labelLarge)
                    state.localBackups.take(4).forEach { file ->
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

                HorizontalDivider()

                // 3. Notifications & Reminders
                Text("Maintenance Notifications", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("System Task & To-Do Alerts", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (hasPermission) "Notification permission granted" else "Permission needed on Android 13+",
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
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasPermission) {
                        OutlinedButton(onClick = onRequestNotificationPermission, modifier = Modifier.weight(1f)) {
                            Text("Grant Permission")
                        }
                    }
                    OutlinedButton(
                        onClick = onTriggerReminderCheck,
                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("test_notification_button")
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Check Alerts Now")
                    }
                }

                HorizontalDivider()

                // 4. Default Engineer & Sample Reset
                EngineerPickerField(
                    label = "Default Engineer (PIC)",
                    selectedName = state.preferences.defaultEngineerName,
                    engineers = state.engineers,
                    onSelect = onSetEngineerName,
                    testTag = "sync_default_engineer_picker"
                )

                TextButton(
                    onClick = onResetSampleData,
                    modifier = Modifier.testTag("reset_sample_data_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset / Reload Sample Factory Data")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun ImportBackupPreviewDialog(
    pkg: PortableBackupPackage,
    onDismiss: () -> Unit,
    onConfirm: (RestoreMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore / Sync Backup File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Source: ${pkg.deviceLabel}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Exported: ${Formatters.formatDateTime(pkg.exportedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text("• ${pkg.engineers.size} Engineers", style = MaterialTheme.typography.bodySmall)
                        Text("• ${pkg.groups.size} Custom Groups", style = MaterialTheme.typography.bodySmall)
                        Text("• ${pkg.machines.size} Machines", style = MaterialTheme.typography.bodySmall)
                        Text("• ${pkg.tasks.size} Maintenance Tasks", style = MaterialTheme.typography.bodySmall)
                        Text("• ${pkg.todos.size} To-Do Reminders", style = MaterialTheme.typography.bodySmall)
                        Text("• ${pkg.serviceLogs.size} Historical Service Logs", style = MaterialTheme.typography.bodySmall)
                        Text("• ${pkg.warehouseParts.size} Warehouse Spare Parts", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Choose Merge to keep your current records and add/update newer entries by UUID, or Replace to overwrite this device completely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onConfirm(RestoreMode.REPLACE) }) {
                    Text("Replace All")
                }
                Button(onClick = { onConfirm(RestoreMode.MERGE) }) {
                    Text("Merge Sync")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ExcelImportPreviewDialog(
    preview: ExcelImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (replaceExisting: Boolean) -> Unit
) {
    val diff = preview.diffSummary

    fun formatCategoryLine(label: String, rawCount: Int, stats: CategoryDiffStats?): String {
        if (stats == null) return "• $rawCount $label"
        return "• $label ($rawCount): ${stats.newCount} new · ${stats.updatedCount} updated · ${stats.unchangedSkippedCount} duplicate(s) skipped"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Excel (.xlsx) · Diff Check") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (diff != null) {
                    Surface(
                        color = if (diff.totalChanges > 0) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (diff.totalChanges > 0) {
                                "Ready to apply: +${diff.totalNew} new · ~${diff.totalUpdated} updated · ${diff.totalSkippedDuplicates} unchanged duplicates skipped"
                            } else {
                                "All ${diff.totalSkippedDuplicates} row(s) already match your database — no duplicates will be added."
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (diff.totalChanges > 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Detected records in workbook:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        if (preview.groups.isNotEmpty() || diff == null) {
                            Text(formatCategoryLine("Custom Groups", preview.groups.size, diff?.groups), style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.machines.isNotEmpty() || diff == null) {
                            Text(formatCategoryLine("Machines", preview.machines.size, diff?.machines), style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.tasks.isNotEmpty() || diff == null) {
                            Text(formatCategoryLine("Maintenance Tasks", preview.tasks.size, diff?.tasks), style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.todos.isNotEmpty() || diff == null) {
                            Text(formatCategoryLine("To-Do Reminders", preview.todos.size, diff?.todos), style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview.warehouseParts.isNotEmpty() || diff == null) {
                            Text(formatCategoryLine("Warehouse Parts", preview.warehouseParts.size, diff?.warehouseParts), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                val missingMachines = diff?.missingMachineCodesForTasks.orEmpty()
                if (preview.warnings.isNotEmpty() || missingMachines.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(10.dp)
                        ) {
                            preview.warnings.forEach { warn ->
                                Text(
                                    text = "• $warn",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            if (missingMachines.isNotEmpty()) {
                                Text(
                                    text = "• Tasks skipped for unknown machines (import Machine List too): ${missingMachines.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Smart Merge compares against your existing lists by Part Code, Machine Asset Code, and Machine + Task + Cadence so only new or changed rows are applied.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onConfirm(true) },
                    enabled = preview.totalRecords > 0,
                    modifier = Modifier.testTag("excel_import_replace_button")
                ) {
                    Text("Replace All")
                }
                Button(
                    onClick = { onConfirm(false) },
                    enabled = preview.totalRecords > 0,
                    modifier = Modifier.testTag("excel_import_merge_button")
                ) {
                    Text(if (diff != null && diff.totalChanges == 0) "Confirm (No New)" else "Smart Merge")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PcWebPortalDialog(
    status: WebPortalStatus,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetPinEnabled: (Boolean) -> Unit,
    onRegeneratePin: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onCopiedUrl: (String) -> Unit
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("PC Browser Web Access")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Enable Server Toggle
                Surface(
                    color = if (status.isRunning) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (status.isRunning) "Web Portal: ONLINE" else "Web Portal: OFFLINE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (status.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use MachinaLog from any PC browser on the same factory Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = status.isRunning,
                            onCheckedChange = onSetEnabled,
                            modifier = Modifier.testTag("pc_web_server_switch")
                        )
                    }
                }

                status.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (status.isRunning && status.primaryUrl != null) {
                    Text(
                        text = "Open this URL on your PC browser:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = status.primaryUrl,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("pc_web_url_text")
                                )
                            }
                            if (status.urls.size > 1) {
                                Text(
                                    text = "Other detected LAN URLs: ${status.urls.drop(1).joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(status.primaryUrl))
                                        onCopiedUrl(status.primaryUrl)
                                    },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("pc_web_copy_url_button")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy URL")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(status.primaryUrl)))
                                        }
                                    },
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("pc_web_open_browser_button")
                                ) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open")
                                }
                            }
                            OutlinedButton(
                                onClick = onRefreshNetwork,
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pc_web_refresh_ip_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Refresh Wi-Fi IP Address")
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Optional PIN Protection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require PIN Lock", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Protect PC portal APIs with a 4-digit shop-floor PIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = status.pinEnabled,
                        onCheckedChange = onSetPinEnabled,
                        modifier = Modifier.testTag("pc_web_pin_switch")
                    )
                }

                if (status.pinEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Access PIN: ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = status.pinCode,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).testTag("pc_web_pin_value")
                            )
                            TextButton(
                                onClick = onRegeneratePin,
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("pc_web_regen_pin_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New PIN")
                            }
                        }
                    }
                }

                Text(
                    text = "All edits made from your PC browser save directly to this device's database. Your data also stays 100% compatible with portable .json backups and .xlsx workbooks for future standalone PC web upgrades.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}


