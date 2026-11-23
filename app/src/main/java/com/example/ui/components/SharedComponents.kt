package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material3.AlertDialog
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.EngineerEntity
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.Severity
import com.example.data.TaskOutcome
import com.example.engine.Formatters
import com.example.ui.theme.LocalMachinaColors
import com.example.ui.theme.groupColorForKey
import java.util.Calendar

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MachineStatusBadge(status: MachineStatus, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (status) {
        MachineStatus.OPERATIONAL -> sem.operationalContainer to sem.operational
        MachineStatus.MAINTENANCE_DUE -> sem.warningContainer to sem.warning
        MachineStatus.MALFUNCTION -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = status.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun CadencePill(cadence: Cadence, modifier: Modifier = Modifier) {
    StatusPill(
        text = cadence.label.uppercase(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    )
}

@Composable
fun RepeatRulePill(rule: RepeatRule, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = if (rule == RepeatRule.ONCE) {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        sem.infoContainer to sem.info
    }
    StatusPill(text = rule.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun PriorityPill(priority: ReminderPriority, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (priority) {
        ReminderPriority.NORMAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ReminderPriority.HIGH -> sem.warningContainer to sem.warning
        ReminderPriority.URGENT -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = priority.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun SeverityPill(severity: Severity, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (severity) {
        Severity.LOW -> sem.operationalContainer to sem.operational
        Severity.MEDIUM -> sem.infoContainer to sem.info
        Severity.HIGH -> sem.warningContainer to sem.warning
        Severity.CRITICAL -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = severity.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun TaskOutcomePill(outcome: TaskOutcome, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (outcome) {
        TaskOutcome.DONE -> sem.operationalContainer to sem.operational
        TaskOutcome.REJECTED -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = outcome.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun GroupBadge(group: CustomGroupEntity, modifier: Modifier = Modifier) {
    val accent = groupColorForKey(group.colorKey)
    Surface(
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OverdueAlertBanner(
    overdueCount: Int,
    filterActive: Boolean,
    onToggleFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sem = LocalMachinaColors.current
    Surface(
        color = sem.criticalContainer,
        contentColor = sem.critical,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, sem.critical.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = "Overdue tasks alert",
                tint = sem.critical
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$overdueCount overdue maintenance ${if (overdueCount == 1) "task" else "tasks"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Immediate engineer attention required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            TextButton(
                onClick = onToggleFilter,
                colors = ButtonDefaults.textButtonColors(contentColor = sem.critical),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_overdue_filter_button")
            ) {
                Text(if (filterActive) "Show All" else "View Overdue")
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun DateTimePickerField(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = millis }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            onChange(updated.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pick_date_button")
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Date: ${Formatters.formatDateOnly(millis)}",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = millis }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                            }
                            onChange(updated.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("pick_time_button")
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = "Pick time", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                Text(
                    text = String.format("Time: %02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun filterMachinesByQuery(machines: List<MachineEntity>, query: String): List<MachineEntity> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return machines
    return machines.filter { m ->
        val hay = "${m.assetCode} ${m.name} ${m.locationZone} ${m.modelInfo} ${m.notes}".lowercase()
        tokens.all { hay.contains(it) }
    }
}

@Composable
fun SearchableMachinePickerField(
    label: String,
    selectedMachineId: Long?,
    machines: List<MachineEntity>,
    onSelectMachine: (MachineEntity?) -> Unit,
    testTag: String,
    allowNone: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selected = selectedMachineId?.let { id -> machines.find { it.id == id } }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                searchQuery = ""
                showPicker = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        ) {
            Icon(Icons.Filled.PrecisionManufacturing, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    selected != null -> "${selected.assetCode} · ${selected.name}"
                    allowNone -> "None (General)"
                    else -> "Select Machine"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.Search, contentDescription = "Search machine", modifier = Modifier.size(16.dp))
        }
    }

    if (showPicker) {
        val filtered = remember(machines, searchQuery) {
            filterMachinesByQuery(machines, searchQuery)
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Select Machine") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search ID, name, zone, or description...",
                        searchTag = "${testTag}_search_input",
                        contentDescription = "Search machines",
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        if (allowNone) {
                            item(key = "machine_none") {
                                Surface(
                                    onClick = {
                                        onSelectMachine(null)
                                        showPicker = false
                                    },
                                    color = if (selectedMachineId == null) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedMachineId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "None (General Reminder)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (selectedMachineId == null) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            item(key = "machine_empty") {
                                Text(
                                    text = "No machines match \"$searchQuery\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            items(filtered, key = { "pick_m_${it.id}" }) { machine ->
                                val isSel = machine.id == selectedMachineId
                                Surface(
                                    onClick = {
                                        onSelectMachine(machine)
                                        showPicker = false
                                    },
                                    color = if (isSel) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        width = if (isSel) 1.5.dp else 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("${testTag}_option_${machine.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                StatusPill(
                                                    text = machine.assetCode,
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                )
                                                MachineStatusBadge(machine.status)
                                            }
                                            Text(
                                                text = machine.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val desc = listOf(machine.locationZone, machine.modelInfo, machine.notes)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" · ")
                                            if (desc.isNotBlank()) {
                                                Text(
                                                    text = desc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isSel) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun EngineerPickerField(
    label: String,
    selectedName: String,
    engineers: List<EngineerEntity>,
    onSelect: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val matched = engineers.find { it.name.equals(selectedName.trim(), ignoreCase = true) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { if (engineers.isNotEmpty()) expanded = true },
                enabled = engineers.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        engineers.isEmpty() -> "No engineers available (Add in Engineers tab)"
                        matched != null -> if (matched.employeeCode.isNotBlank()) "${matched.name} (${matched.employeeCode})" else matched.name
                        else -> "Select Engineer *"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Open engineer selector")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                engineers.forEach { eng ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = if (eng.employeeCode.isNotBlank()) "${eng.name} (${eng.employeeCode})" else eng.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${eng.role} · ${eng.shift}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = {
                            if (eng.name.equals(selectedName.trim(), ignoreCase = true)) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected")
                            }
                        },
                        onClick = {
                            onSelect(eng.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantEngineersSelector(
    selectedAssistants: List<String>,
    excludePic: String,
    engineers: List<EngineerEntity>,
    onToggleAssistant: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val candidates = remember(engineers, excludePic) {
        engineers.filterNot { it.name.equals(excludePic.trim(), ignoreCase = true) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Assistant Engineers (Optional · Co-workers)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (candidates.isEmpty()) {
            Text(
                text = "Add more engineers in the Engineers menu to assign co-workers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                candidates.forEach { eng ->
                    val isSelected = selectedAssistants.any { it.equals(eng.name, ignoreCase = true) }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleAssistant(eng.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(eng.name) },
                        modifier = Modifier.testTag("assistant_chip_${eng.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    searchTag: String? = null,
    contentDescription: String = placeholder
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = if (value.isNotEmpty()) 2.dp else 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (searchTag != null) it.testTag(searchTag) else it }
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleFilterPanel(
    activeFilterCount: Int,
    onResetFilters: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    inlineQuickActions: (@Composable () -> Unit)? = null,
    filterContent: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(testTagPrefix) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            FilterChip(
                selected = expanded || activeFilterCount > 0,
                onClick = { expanded = !expanded },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Hide filters" else "Show filters",
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters",
                        maxLines = 1
                    )
                },
                modifier = Modifier.testTag("${testTagPrefix}_filters_toggle_chip")
            )
            inlineQuickActions?.invoke()
            if (activeFilterCount > 0) {
                TextButton(
                    onClick = onResetFilters,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTagPrefix}_filters_reset_button")
                ) {
                    Text("Reset")
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(10.dp),
                    content = filterContent
                )
            }
        }
    }
}

@Composable
fun ListPaginationFooter(
    startNumber: Int,
    endNumber: Int,
    totalItems: Int,
    safePageIndex: Int,
    totalPages: Int,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    if (totalItems == 0) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Showing $startNumber–$endNumber of $totalItems",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Per page:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(25, 50, 100).forEach { sizeOption ->
                        val selected = pageSize == sizeOption
                        Surface(
                            onClick = { onPageSizeChange(sizeOption) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("${testTagPrefix}_page_size_$sizeOption")
                        ) {
                            Text(
                                text = "$sizeOption",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (totalPages > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onPrevPage,
                        enabled = safePageIndex > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTagPrefix}_prev_page_button")
                    ) {
                        Text("Prev", maxLines = 1, softWrap = false)
                    }
                    Text(
                        text = "Page ${safePageIndex + 1} of $totalPages",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                    OutlinedButton(
                        onClick = onNextPage,
                        enabled = safePageIndex < totalPages - 1,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("${testTagPrefix}_next_page_button")
                    ) {
                        Text("Next", maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

