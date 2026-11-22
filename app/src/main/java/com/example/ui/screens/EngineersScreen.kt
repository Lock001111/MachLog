package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.EngineerEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.TaskOutcome
import com.example.data.parseEngineerList
import com.example.engine.Formatters
import com.example.ui.MachinaUiState
import com.example.ui.components.CadencePill
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PriorityPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMachinaColors

@Composable
fun EngineersScreen(
    state: MachinaUiState,
    onAddEngineer: () -> Unit,
    onEditEngineer: (EngineerEntity) -> Unit,
    onDeleteEngineer: (EngineerEntity) -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    onEditTask: (MaintenanceTaskEntity) -> Unit,
    onViewEngineerTasksInTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var inspectingEngineerId by remember { mutableStateOf<Long?>(null) }
    val sem = LocalMachinaColors.current
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }

    val filtered = remember(state.engineers, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) state.engineers
        else state.engineers.filter { eng ->
            eng.name.lowercase().contains(q) ||
                eng.employeeCode.lowercase().contains(q) ||
                eng.role.lowercase().contains(q) ||
                eng.shift.lowercase().contains(q)
        }
    }

    val totalAssignedTasks = remember(state.tasks) { state.tasks.size }
    val totalReportsDone = remember(state.serviceLogs) {
        state.serviceLogs.count { it.log.outcome == TaskOutcome.DONE }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "engineers_kpis") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                KpiMetricCard(
                    label = "Engineers",
                    value = "${state.engineers.size}",
                    icon = Icons.Filled.Groups,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Open Tasks",
                    value = "$totalAssignedTasks",
                    icon = Icons.Filled.TaskAlt,
                    accentColor = sem.warning,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Jobs Done",
                    value = "$totalReportsDone",
                    icon = Icons.Filled.Engineering,
                    accentColor = sem.operational,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "engineers_search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search engineer name, badge ID, role, shift...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search engineers") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("engineers_search_input")
            )
        }

        if (filtered.isEmpty()) {
            item(key = "empty_engineers") {
                EmptyStateCard(
                    icon = Icons.Filled.Engineering,
                    title = if (state.engineers.isEmpty()) "No engineers registered" else "No matching engineers",
                    subtitle = "Add maintenance engineers to populate the selectable Person In Charge (PIC) and Assistant Engineers roster.",
                    actionLabel = "Add Engineer",
                    onAction = onAddEngineer
                )
            }
        } else {
            items(filtered, key = { "eng_${it.id}" }) { engineer ->
                val activePicCount = state.tasks.count {
                    it.personInCharge.equals(engineer.name, ignoreCase = true) ||
                        parseEngineerList(it.assistantEngineers).any { a -> a.equals(engineer.name, ignoreCase = true) }
                }
                val historyCount = state.serviceLogs.count {
                    it.log.personInCharge.equals(engineer.name, ignoreCase = true) ||
                        parseEngineerList(it.log.assistantEngineers).any { a -> a.equals(engineer.name, ignoreCase = true) }
                }

                EngineerCard(
                    engineer = engineer,
                    activeCount = activePicCount,
                    historyCount = historyCount,
                    onInspectActiveTasks = { inspectingEngineerId = engineer.id },
                    onEdit = { onEditEngineer(engineer) },
                    onDelete = { onDeleteEngineer(engineer) }
                )
            }
        }

        item(key = "engineers_bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }

    val inspectingEngineer = inspectingEngineerId?.let { id -> state.engineers.find { it.id == id } }
    if (inspectingEngineer != null) {
        val assignedTasks = remember(state.tasks, inspectingEngineer.name) {
            state.tasks.filter { t ->
                t.personInCharge.equals(inspectingEngineer.name, ignoreCase = true) ||
                    parseEngineerList(t.assistantEngineers).any { it.equals(inspectingEngineer.name, ignoreCase = true) }
            }.sortedBy { it.deadlineMillis }
        }
        EngineerActiveTasksDialog(
            engineer = inspectingEngineer,
            tasks = assignedTasks,
            machinesById = machinesById,
            onDismiss = { inspectingEngineerId = null },
            onCompleteTask = onCompleteTask,
            onRejectTask = onRejectTask,
            onEditTask = onEditTask,
            onViewInTasksTab = {
                inspectingEngineerId = null
                onViewEngineerTasksInTab(inspectingEngineer.name)
            }
        )
    }
}

@Composable
private fun EngineerCard(
    engineer: EngineerEntity,
    activeCount: Int,
    historyCount: Int,
    onInspectActiveTasks: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sem = LocalMachinaColors.current
    val initials = remember(engineer.name) {
        engineer.name.trim().split(Regex("\\s+")).take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "EN" }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("engineer_card_${engineer.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = engineer.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (engineer.employeeCode.isNotBlank()) {
                        StatusPill(
                            text = engineer.employeeCode,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${engineer.role} · ${engineer.shift}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onInspectActiveTasks,
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeCount > 0) sem.infoContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (activeCount > 0) sem.info.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("engineer_active_tasks_button_${engineer.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$activeCount Active Tasks",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeCount > 0) sem.info else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "View active tasks",
                                tint = if (activeCount > 0) sem.info else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    StatusPill(
                        text = "$historyCount Logged",
                        containerColor = sem.operationalContainer,
                        contentColor = sem.operational
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_engineer_button_${engineer.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit engineer")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_engineer_button_${engineer.id}")
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Remove engineer",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineerActiveTasksDialog(
    engineer: EngineerEntity,
    tasks: List<MaintenanceTaskEntity>,
    machinesById: Map<Long, MachineEntity>,
    onDismiss: () -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    onEditTask: (MaintenanceTaskEntity) -> Unit,
    onViewInTasksTab: () -> Unit
) {
    val now = System.currentTimeMillis()
    val sem = LocalMachinaColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${engineer.name} · Active Tasks (${tasks.size})")
                Text(
                    text = "${engineer.role} · ${engineer.shift}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (tasks.isEmpty()) {
                    Text(
                        text = "No active tasks currently assigned to ${engineer.name} as PIC or assistant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    tasks.forEach { task ->
                        val machine = machinesById[task.machineId]
                        val isPic = task.personInCharge.equals(engineer.name, ignoreCase = true)
                        val isOverdue = task.deadlineMillis < now
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isOverdue) sem.critical.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("engineer_modal_task_${task.id}")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    PriorityPill(task.priority)
                                    CadencePill(task.cadence)
                                    StatusPill(
                                        text = if (isPic) "PIC" else "Assistant",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(task.title, style = MaterialTheme.typography.titleSmall)
                                if (machine != null) {
                                    Text(
                                        text = "${machine.assetCode} · ${machine.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${Formatters.formatRelativeDeadline(task.deadlineMillis, now)} · ${Formatters.formatDateTime(task.deadlineMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) sem.critical else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(onClick = { onEditTask(task) }) {
                                        Text("Edit")
                                    }
                                    OutlinedButton(
                                        onClick = { onRejectTask(task) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = sem.critical),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reject")
                                    }
                                    Button(
                                        onClick = { onCompleteTask(task) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Complete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tasks.isNotEmpty()) {
                Button(
                    onClick = onViewInTasksTab,
                    modifier = Modifier.testTag("engineer_filter_in_tasks_button")
                ) {
                    Text("Filter in Tasks Tab")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (tasks.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
fun EngineerEditorDialog(
    initialEngineer: EngineerEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        uuid: String?,
        name: String,
        employeeCode: String,
        role: String,
        shift: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialEngineer?.name ?: "") }
    var employeeCode by remember { mutableStateOf(initialEngineer?.employeeCode ?: "") }
    var role by remember { mutableStateOf(initialEngineer?.role ?: "Maintenance Engineer") }
    var shift by remember { mutableStateOf(initialEngineer?.shift ?: "Day Shift") }
    var nameError by remember { mutableStateOf(false) }

    val shiftPresets = listOf("Day Shift", "Night Shift", "Swing Shift", "On-Call")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEngineer == null) "Add Engineer" else "Edit Engineer") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text("Full Name *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Engineer name is required") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("engineer_name_input")
                )

                OutlinedTextField(
                    value = employeeCode,
                    onValueChange = { employeeCode = it },
                    label = { Text("Badge / ID Code (e.g. ENG-06)") },
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("engineer_code_input")
                )

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Specialty / Role") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("engineer_role_input")
                )

                Text("Assigned Shift", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    shiftPresets.forEach { preset ->
                        FilterChip(
                            selected = shift.equals(preset, ignoreCase = true),
                            onClick = { shift = preset },
                            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = shift,
                    onValueChange = { shift = it },
                    label = { Text("Shift Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    onSave(
                        initialEngineer?.id ?: 0L,
                        initialEngineer?.uuid,
                        name,
                        employeeCode,
                        role,
                        shift
                    )
                },
                modifier = Modifier.testTag("save_engineer_button")
            ) {
                Text("Save Engineer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
