package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.TodoReminderEntity
import com.example.engine.Formatters
import com.example.ui.HistoryFilterState
import com.example.ui.MachinaUiState
import com.example.ui.TaskGroupBy
import com.example.ui.TasksFilterState
import com.example.ui.TodoFilterState
import com.example.ui.components.CadencePill
import com.example.ui.components.CompactSearchField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.OverdueAlertBanner
import com.example.ui.components.PriorityPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMachinaColors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: MachinaUiState,
    selectedSubTab: Int,
    onSelectSubTab: (Int) -> Unit,
    onUpdateFilter: ((TasksFilterState) -> TasksFilterState) -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (MaintenanceTaskEntity) -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    onManageGroups: () -> Unit,
    onImportExcel: () -> Unit,
    onUpdateTodoFilter: ((TodoFilterState) -> TodoFilterState) -> Unit,
    onSetTodoPageSize: (Int) -> Unit,
    onToggleTodoComplete: (TodoReminderEntity) -> Unit,
    onAddTodo: () -> Unit,
    onEditTodo: (TodoReminderEntity) -> Unit,
    onDeleteTodo: (TodoReminderEntity) -> Unit,
    onUpdateHistoryFilter: ((HistoryFilterState) -> HistoryFilterState) -> Unit,
    onDeleteLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val openTodoCount = remember(state.todos, state.tasks) {
        state.todos.count { !it.isCompleted } + state.tasks.count { it.reminderEnabled }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedSubTab.coerceIn(0, 2)) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { onSelectSubTab(0) },
                text = { Text("Active (${state.tasks.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("tasks_subtab_active")
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { onSelectSubTab(1) },
                text = { Text("To-Do ($openTodoCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("tasks_subtab_todo")
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { onSelectSubTab(2) },
                text = { Text("History (${state.serviceLogs.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("tasks_subtab_history")
            )
        }

        when (selectedSubTab) {
            1 -> TodoRemindersSubTab(
                state = state,
                onUpdateFilter = onUpdateTodoFilter,
                onSetPageSize = onSetTodoPageSize,
                onToggleComplete = onToggleTodoComplete,
                onAddTodo = onAddTodo,
                onEditTodo = onEditTodo,
                onDeleteTodo = onDeleteTodo,
                onCompleteTask = onCompleteTask,
                onEditTask = onEditTask,
                onRejectTask = onRejectTask,
                modifier = Modifier.weight(1f)
            )
            2 -> TaskHistorySubTab(
                state = state,
                onUpdateFilter = onUpdateHistoryFilter,
                onDeleteLog = onDeleteLog,
                modifier = Modifier.weight(1f)
            )
            else -> ActiveTasksSubTab(
                state = state,
                onUpdateFilter = onUpdateFilter,
                onAddTask = onAddTask,
                onEditTask = onEditTask,
                onCompleteTask = onCompleteTask,
                onRejectTask = onRejectTask,
                onManageGroups = onManageGroups,
                onImportExcel = onImportExcel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActiveTasksSubTab(
    state: MachinaUiState,
    onUpdateFilter: ((TasksFilterState) -> TasksFilterState) -> Unit,
    onAddTask: () -> Unit,
    onEditTask: (MaintenanceTaskEntity) -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    onManageGroups: () -> Unit,
    onImportExcel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val groupsById = remember(state.groups) { state.groups.associateBy { it.id } }
    val filter = state.tasksFilter

    val overdueCount = remember(state.tasks, now) {
        state.tasks.count { it.deadlineMillis < now }
    }

    val filteredTasks = remember(state.tasks, filter, machinesById, now) {
        val base = state.tasks.filter { task ->
            val machine = machinesById[task.machineId]
            val effectiveGroupId = task.groupId ?: machine?.groupId
            val matchCadence = filter.cadence == null || task.cadence == filter.cadence
            val matchPriority = filter.priority == null || task.priority == filter.priority
            val matchOverdue = !filter.overdueOnly || task.deadlineMillis < now
            val matchGroup = filter.groupId == null || effectiveGroupId == filter.groupId
            val matchPic = filter.picFilter.isNullOrBlank() ||
                task.personInCharge.equals(filter.picFilter, ignoreCase = true) ||
                task.assistantEngineers.contains(filter.picFilter, ignoreCase = true)
            val q = filter.searchQuery.trim()
            val matchQuery = q.isEmpty() ||
                task.title.contains(q, ignoreCase = true) ||
                task.description.contains(q, ignoreCase = true) ||
                task.personInCharge.contains(q, ignoreCase = true) ||
                task.assistantEngineers.contains(q, ignoreCase = true) ||
                (machine?.name?.contains(q, ignoreCase = true) == true) ||
                (machine?.assetCode?.contains(q, ignoreCase = true) == true)
            matchCadence && matchPriority && matchOverdue && matchGroup && matchPic && matchQuery
        }
        if (filter.sortAscending) {
            base.sortedBy { it.deadlineMillis }
        } else {
            base.sortedByDescending { it.deadlineMillis }
        }
    }

    val groupedSections = remember(filteredTasks, filter.groupBy, machinesById, groupsById) {
        when (filter.groupBy) {
            TaskGroupBy.NONE -> listOf("All Scheduled Tasks" to filteredTasks)
            TaskGroupBy.CADENCE -> Cadence.entries.mapNotNull { cad ->
                val list = filteredTasks.filter { it.cadence == cad }
                if (list.isEmpty()) null else "${cad.label} Maintenance" to list
            }
            TaskGroupBy.MACHINE -> filteredTasks.groupBy { task ->
                machinesById[task.machineId]?.let { "${it.assetCode} · ${it.name}" } ?: "Unknown Machine"
            }.toList()
            TaskGroupBy.PIC -> filteredTasks.groupBy { task ->
                task.personInCharge.ifBlank { "Unassigned" }
            }.toList()
            TaskGroupBy.CUSTOM_GROUP -> filteredTasks.groupBy { task ->
                val machine = machinesById[task.machineId]
                val gid = task.groupId ?: machine?.groupId
                gid?.let { groupsById[it]?.name } ?: "Ungrouped"
            }.toList()
        }
    }

    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }
    var showGroupByMenu by remember { mutableStateOf(false) }
    var showPicMenu by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "overdue_banner") {
            AnimatedVisibility(visible = overdueCount > 0) {
                OverdueAlertBanner(
                    overdueCount = overdueCount,
                    filterActive = filter.overdueOnly,
                    onToggleFilter = {
                        onUpdateFilter { it.copy(overdueOnly = !it.overdueOnly) }
                    }
                )
            }
        }

        item(key = "search_and_grouping") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactSearchField(
                        value = filter.searchQuery,
                        onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                        placeholder = "Search machine, task, or PIC...",
                        searchTag = "tasks_search_input",
                        contentDescription = "Search tasks",
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showGroupByMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("tasks_group_by_button")
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "Group by", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(filter.groupBy.label, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = showGroupByMenu,
                            onDismissRequest = { showGroupByMenu = false }
                        ) {
                            TaskGroupBy.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text("Group by: ${mode.label}") },
                                    onClick = {
                                        onUpdateFilter { it.copy(groupBy = mode) }
                                        showGroupByMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = filter.cadence == null && !filter.overdueOnly,
                        onClick = { onUpdateFilter { it.copy(cadence = null, overdueOnly = false) } },
                        label = { Text("All (${state.tasks.size})") },
                        modifier = Modifier.testTag("cadence_chip_all")
                    )
                    Cadence.entries.forEach { cad ->
                        val count = state.tasks.count { it.cadence == cad }
                        FilterChip(
                            selected = filter.cadence == cad,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(cadence = if (it.cadence == cad) null else cad)
                                }
                            },
                            label = { Text("${cad.label} ($count)") },
                            modifier = Modifier.testTag("cadence_chip_${cad.name.lowercase()}")
                        )
                    }
                    Box {
                        FilterChip(
                            selected = !filter.picFilter.isNullOrBlank(),
                            onClick = { showPicMenu = true },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(filter.picFilter ?: "All PICs") },
                            modifier = Modifier.testTag("pic_filter_chip")
                        )
                        DropdownMenu(
                            expanded = showPicMenu,
                            onDismissRequest = { showPicMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Engineers") },
                                onClick = {
                                    onUpdateFilter { it.copy(picFilter = null) }
                                    showPicMenu = false
                                }
                            )
                            state.knownEngineers.forEach { pic ->
                                DropdownMenuItem(
                                    text = { Text(pic) },
                                    onClick = {
                                        onUpdateFilter { it.copy(picFilter = pic) }
                                        showPicMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { onUpdateFilter { it.copy(sortAscending = !it.sortAscending) } },
                        leadingIcon = {
                            Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(if (filter.sortAscending) "Date ↑ Soonest" else "Date ↓ Latest")
                        },
                        modifier = Modifier.testTag("task_date_sort_chip")
                    )
                    FilterChip(
                        selected = filter.priority == null,
                        onClick = { onUpdateFilter { it.copy(priority = null) } },
                        label = { Text("All Priorities") },
                        modifier = Modifier.testTag("task_priority_chip_all")
                    )
                    ReminderPriority.entries.forEach { prio ->
                        val pCount = state.tasks.count { it.priority == prio }
                        FilterChip(
                            selected = filter.priority == prio,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(priority = if (it.priority == prio) null else prio)
                                }
                            },
                            label = { Text("${prio.label} ($pCount)") },
                            modifier = Modifier.testTag("task_priority_chip_${prio.name.lowercase()}")
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = filter.groupId == null,
                        onClick = { onUpdateFilter { it.copy(groupId = null) } },
                        label = { Text("All Groups") }
                    )
                    state.groups.forEach { group ->
                        FilterChip(
                            selected = filter.groupId == group.id,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(groupId = if (it.groupId == group.id) null else group.id)
                                }
                            },
                            label = { Text(group.name) },
                            modifier = Modifier.testTag("task_group_chip_${group.id}")
                        )
                    }
                    TextButton(
                        onClick = onManageGroups,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("manage_groups_shortcut")
                    ) {
                        Icon(Icons.Filled.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Manage Groups")
                    }
                    OutlinedButton(
                        onClick = onImportExcel,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tasks_import_excel_button")
                    ) {
                        Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import Excel")
                    }
                }
            }
        }

        if (filteredTasks.isEmpty()) {
            item(key = "empty_tasks") {
                EmptyStateCard(
                    icon = Icons.Filled.TaskAlt,
                    title = "No matching maintenance tasks",
                    subtitle = "Adjust your cadence, custom group, or search filters, or create a new task.",
                    actionLabel = "New Task",
                    onAction = onAddTask
                )
            }
        } else {
            groupedSections.forEach { (sectionTitle, sectionTasks) ->
                val isCollapsed = collapsedGroups[sectionTitle] == true
                val sectionOverdue = sectionTasks.count { it.deadlineMillis < now }

                if (filter.groupBy != TaskGroupBy.NONE) {
                    item(key = "header_$sectionTitle") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { collapsedGroups[sectionTitle] = !isCollapsed }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                if (sectionOverdue > 0) {
                                    StatusPill(
                                        text = "$sectionOverdue OVERDUE",
                                        containerColor = LocalMachinaColors.current.criticalContainer,
                                        contentColor = LocalMachinaColors.current.critical
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                StatusPill(
                                    text = "${sectionTasks.size}",
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                    contentDescription = if (isCollapsed) "Expand group" else "Collapse group"
                                )
                            }
                        }
                    }
                }

                if (!isCollapsed) {
                    items(sectionTasks, key = { "task_${it.id}" }) { task ->
                        val machine = machinesById[task.machineId]
                        val group = (task.groupId ?: machine?.groupId)?.let { groupsById[it] }
                        MaintenanceTaskCard(
                            task = task,
                            machine = machine,
                            group = group,
                            nowMillis = now,
                            onComplete = { onCompleteTask(task) },
                            onReject = { onRejectTask(task) },
                            onEdit = { onEditTask(task) }
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MaintenanceTaskCard(
    task: MaintenanceTaskEntity,
    machine: MachineEntity?,
    group: CustomGroupEntity?,
    nowMillis: Long,
    onComplete: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    val sem = LocalMachinaColors.current
    val isOverdue = task.deadlineMillis < nowMillis
    val isDueSoon = !isOverdue && (task.deadlineMillis - nowMillis) <= TimeUnit.HOURS.toMillis(24)
    val accentColor = when {
        isOverdue -> sem.critical
        isDueSoon -> sem.warning
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isOverdue) 1.5.dp else 1.dp,
            color = if (isOverdue) sem.critical.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PriorityPill(task.priority)
                    CadencePill(task.cadence)
                    if (machine != null) {
                        StatusPill(
                            text = machine.assetCode,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (group != null) {
                        GroupBadge(group)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (task.reminderEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = if (task.reminderEnabled) "Reminder active" else "Reminder off",
                            tint = if (task.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp)
                        )
                        if (task.reminderEnabled) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = Formatters.formatLeadTime(task.reminderLeadHours),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (machine != null) {
                        Text(
                            text = "${machine.name} · ${machine.locationZone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Created: ${Formatters.formatDateTime(task.createdAtMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    StatusPill(
                        text = Formatters.formatRelativeDeadline(task.deadlineMillis, nowMillis),
                        containerColor = when {
                            isOverdue -> sem.criticalContainer
                            isDueSoon -> sem.warningContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = when {
                            isOverdue -> sem.critical
                            isDueSoon -> sem.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = Formatters.formatDateTime(task.deadlineMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(task.personInCharge, style = MaterialTheme.typography.labelSmall)
                    }
                    if (task.assistantEngineers.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "+ ${task.assistantEngineers}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(Formatters.formatDuration(task.estimatedMinutes), style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_task_button_${task.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit task", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = sem.critical),
                        border = BorderStroke(1.dp, sem.critical.copy(alpha = 0.55f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reject_task_button_${task.id}")
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = "Reject task", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reject")
                    }
                    Button(
                        onClick = onComplete,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("complete_task_button_${task.id}")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Complete task", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Complete")
                    }
                }
            }
        }
    }
}
