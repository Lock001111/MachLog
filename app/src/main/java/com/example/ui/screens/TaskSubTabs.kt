package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.RepeatRule
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogWithParts
import com.example.data.TaskOutcome
import com.example.data.TodoReminderEntity
import com.example.engine.Formatters
import com.example.ui.MachinaUiState
import com.example.ui.TodoFilterState
import com.example.ui.components.CadencePill
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PriorityPill
import com.example.ui.components.RepeatRulePill
import com.example.ui.components.SeverityPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskOutcomePill
import com.example.ui.theme.LocalMachinaColors

data class PagedResult<T>(
    val items: List<T>,
    val safePageIndex: Int,
    val totalPages: Int,
    val totalItems: Int,
    val startNumber: Int,
    val endNumber: Int
)

fun <T> paginateItems(items: List<T>, pageIndex: Int, pageSize: Int): PagedResult<T> {
    val safeSize = if (pageSize in listOf(25, 50, 100)) pageSize else 25
    val total = items.size
    if (total == 0) {
        return PagedResult(emptyList(), safePageIndex = 0, totalPages = 1, totalItems = 0, startNumber = 0, endNumber = 0)
    }
    val totalPages = ((total + safeSize - 1) / safeSize).coerceAtLeast(1)
    val safePage = pageIndex.coerceIn(0, totalPages - 1)
    val from = safePage * safeSize
    val to = (from + safeSize).coerceAtMost(total)
    return PagedResult(
        items = items.subList(from, to),
        safePageIndex = safePage,
        totalPages = totalPages,
        totalItems = total,
        startNumber = from + 1,
        endNumber = to
    )
}

@Composable
fun TodoRemindersSubTab(
    state: MachinaUiState,
    onUpdateFilter: ((TodoFilterState) -> TodoFilterState) -> Unit,
    onSetPageSize: (Int) -> Unit,
    onToggleComplete: (TodoReminderEntity) -> Unit,
    onAddTodo: () -> Unit,
    onEditTodo: (TodoReminderEntity) -> Unit,
    onDeleteTodo: (TodoReminderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val groupsById = remember(state.groups) { state.groups.associateBy { it.id } }
    val filter = state.todoFilter
    val sem = LocalMachinaColors.current
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }

    val activeCount = remember(state.todos) { state.todos.count { !it.isCompleted } }
    val completedCount = remember(state.todos) { state.todos.count { it.isCompleted } }
    val overdueCount = remember(state.todos, now) {
        state.todos.count { !it.isCompleted && it.dueAtMillis < now }
    }
    val recurringCount = remember(state.todos) {
        state.todos.count { !it.isCompleted && it.repeatRule != RepeatRule.ONCE }
    }

    val filteredTodos = remember(state.todos, filter, machinesById) {
        state.todos.filter { td ->
            val m = td.machineId?.let { machinesById[it] }
            val matchRule = filter.repeatRule == null || td.repeatRule == filter.repeatRule
            val matchCompleted = filter.showCompleted || !td.isCompleted
            val q = filter.searchQuery.trim()
            val matchQuery = q.isEmpty() ||
                td.title.contains(q, ignoreCase = true) ||
                td.notes.contains(q, ignoreCase = true) ||
                td.personInCharge.contains(q, ignoreCase = true) ||
                (m?.name?.contains(q, ignoreCase = true) == true)
            matchRule && matchCompleted && matchQuery
        }.sortedWith(
            compareBy<TodoReminderEntity> { it.isCompleted }
                .thenBy { td -> if (!td.isCompleted) td.dueAtMillis else -(td.completedAtMillis ?: td.dueAtMillis) }
        )
    }
    val pagedTodos = remember(filteredTodos, filter.currentPage, pageSize) {
        paginateItems(filteredTodos, filter.currentPage, pageSize)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "todo_kpis") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                KpiMetricCard(
                    label = "Active",
                    value = "$activeCount",
                    icon = Icons.Filled.Checklist,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Overdue",
                    value = "$overdueCount",
                    icon = Icons.Filled.Alarm,
                    accentColor = if (overdueCount > 0) sem.critical else sem.operational,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Recurring",
                    value = "$recurringCount",
                    icon = Icons.Filled.Repeat,
                    accentColor = sem.info,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "todo_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter.searchQuery,
                    onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q, currentPage = 0) } },
                    placeholder = { Text("Search reminders, notes, or PIC...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search reminders") },
                    trailingIcon = {
                        if (filter.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "", currentPage = 0) } }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("todo_search_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = filter.repeatRule == null,
                        onClick = { onUpdateFilter { it.copy(repeatRule = null, currentPage = 0) } },
                        label = { Text("All Schedules") }
                    )
                    RepeatRule.entries.forEach { rule ->
                        FilterChip(
                            selected = filter.repeatRule == rule,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(
                                        repeatRule = if (it.repeatRule == rule) null else rule,
                                        currentPage = 0
                                    )
                                }
                            },
                            label = { Text(rule.label) },
                            modifier = Modifier.testTag("todo_rule_chip_${rule.name.lowercase()}")
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateFilter { it.copy(showCompleted = !it.showCompleted, currentPage = 0) }
                                }
                        ) {
                            Checkbox(
                                checked = filter.showCompleted,
                                onCheckedChange = { checked ->
                                    onUpdateFilter { it.copy(showCompleted = checked, currentPage = 0) }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("todo_show_completed_checkbox")
                            )
                            Text(
                                text = "Show Completed ($completedCount)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (filteredTodos.isNotEmpty()) {
                                Text(
                                    text = "${pagedTodos.startNumber}–${pagedTodos.endNumber} of ${pagedTodos.totalItems}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Per page:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(25, 50, 100).forEach { sizeOption ->
                                FilterChip(
                                    selected = pageSize == sizeOption,
                                    onClick = { onSetPageSize(sizeOption) },
                                    label = { Text("$sizeOption") },
                                    modifier = Modifier.testTag("todo_page_size_$sizeOption")
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredTodos.isEmpty()) {
            item(key = "empty_todos") {
                EmptyStateCard(
                    icon = Icons.Filled.Checklist,
                    title = "No reminders scheduled",
                    subtitle = "Create personal engineer to-dos triggered once, daily, weekly, monthly, or yearly.",
                    actionLabel = "New Reminder",
                    onAction = onAddTodo
                )
            }
        } else {
            items(pagedTodos.items, key = { "todo_${it.id}" }) { todo ->
                val machine = todo.machineId?.let { machinesById[it] }
                val group = todo.groupId?.let { groupsById[it] }
                TodoReminderCard(
                    todo = todo,
                    machine = machine,
                    group = group,
                    nowMillis = now,
                    onToggleComplete = { onToggleComplete(todo) },
                    onEdit = { onEditTodo(todo) },
                    onDelete = { onDeleteTodo(todo) }
                )
            }

            if (pagedTodos.totalPages > 1 || pagedTodos.totalItems > 25) {
                item(key = "todo_pagination_bar") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onUpdateFilter { it.copy(currentPage = (pagedTodos.safePageIndex - 1).coerceAtLeast(0)) }
                            },
                            enabled = pagedTodos.safePageIndex > 0,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("todo_prev_page_button")
                        ) {
                            Text("Prev")
                        }
                        Text(
                            text = "Page ${pagedTodos.safePageIndex + 1} of ${pagedTodos.totalPages}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        OutlinedButton(
                            onClick = {
                                onUpdateFilter {
                                    it.copy(currentPage = (pagedTodos.safePageIndex + 1).coerceAtMost(pagedTodos.totalPages - 1))
                                }
                            },
                            enabled = pagedTodos.safePageIndex < pagedTodos.totalPages - 1,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("todo_next_page_button")
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }

        item(key = "todo_bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun TodoReminderCard(
    todo: TodoReminderEntity,
    machine: MachineEntity?,
    group: CustomGroupEntity?,
    nowMillis: Long,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sem = LocalMachinaColors.current
    val isOverdue = !todo.isCompleted && todo.dueAtMillis < nowMillis

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (todo.isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isOverdue) 1.5.dp else 1.dp,
            color = if (isOverdue) sem.critical.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("todo_card_${todo.id}")
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp)
        ) {
            Checkbox(
                checked = todo.isCompleted,
                onCheckedChange = { onToggleComplete() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("todo_checkbox_${todo.id}")
            )
            Spacer(Modifier.width(4.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    RepeatRulePill(todo.repeatRule)
                    PriorityPill(todo.priority)
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
                }

                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (todo.notes.isNotBlank()) {
                    Text(
                        text = todo.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    StatusPill(
                        text = if (todo.isCompleted) {
                            val doneAt = todo.completedAtMillis
                            if (doneAt != null) "Done · ${Formatters.formatDateTime(doneAt)}" else "Done"
                        } else if (isOverdue) {
                            Formatters.formatRelativeDeadline(todo.dueAtMillis, nowMillis)
                        } else {
                            Formatters.formatDateTime(todo.dueAtMillis)
                        },
                        containerColor = when {
                            todo.isCompleted -> sem.operationalContainer
                            isOverdue -> sem.criticalContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = when {
                            todo.isCompleted -> sem.operational
                            isOverdue -> sem.critical
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (todo.personInCharge.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(3.dp))
                            Text(todo.personInCharge, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Icon(
                        imageVector = if (todo.reminderEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = if (todo.reminderEnabled) "Notification on" else "Notification off",
                        tint = if (todo.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_todo_button_${todo.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit reminder", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_todo_button_${todo.id}")
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete reminder",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskHistorySubTab(
    state: MachinaUiState,
    onDeleteLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var outcomeFilter by remember { mutableStateOf<TaskOutcome?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val sem = LocalMachinaColors.current

    val doneCount = remember(state.serviceLogs) {
        state.serviceLogs.count { it.log.outcome == TaskOutcome.DONE }
    }
    val rejectedCount = remember(state.serviceLogs) {
        state.serviceLogs.count { it.log.outcome == TaskOutcome.REJECTED }
    }

    val filteredLogs = remember(state.serviceLogs, outcomeFilter, searchQuery, machinesById) {
        val q = searchQuery.trim().lowercase()
        state.serviceLogs.filter { item ->
            val log = item.log
            val m = machinesById[log.machineId]
            val matchOutcome = outcomeFilter == null || log.outcome == outcomeFilter
            val matchQuery = q.isEmpty() ||
                log.summaryTitle.lowercase().contains(q) ||
                log.malfunctionDetails.lowercase().contains(q) ||
                log.rejectionReason.lowercase().contains(q) ||
                log.personInCharge.lowercase().contains(q) ||
                log.assistantEngineers.lowercase().contains(q) ||
                (m?.assetCode?.lowercase()?.contains(q) == true) ||
                (m?.name?.lowercase()?.contains(q) == true)
            matchOutcome && matchQuery
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "history_kpis") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                KpiMetricCard(
                    label = "All History",
                    value = "${state.serviceLogs.size}",
                    icon = Icons.Filled.History,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Done",
                    value = "$doneCount",
                    icon = Icons.Filled.CheckCircle,
                    accentColor = sem.operational,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    label = "Rejected",
                    value = "$rejectedCount",
                    icon = Icons.Filled.Block,
                    accentColor = sem.critical,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "history_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search completed/rejected task, reason, engineer...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search history") },
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
                        .testTag("task_history_search_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = outcomeFilter == null,
                        onClick = { outcomeFilter = null },
                        label = { Text("All (${state.serviceLogs.size})") },
                        modifier = Modifier.testTag("history_filter_all")
                    )
                    FilterChip(
                        selected = outcomeFilter == TaskOutcome.DONE,
                        onClick = {
                            outcomeFilter = if (outcomeFilter == TaskOutcome.DONE) null else TaskOutcome.DONE
                        },
                        label = { Text("Done ($doneCount)") },
                        modifier = Modifier.testTag("history_filter_done")
                    )
                    FilterChip(
                        selected = outcomeFilter == TaskOutcome.REJECTED,
                        onClick = {
                            outcomeFilter = if (outcomeFilter == TaskOutcome.REJECTED) null else TaskOutcome.REJECTED
                        },
                        label = { Text("Rejected ($rejectedCount)") },
                        modifier = Modifier.testTag("history_filter_rejected")
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item(key = "empty_history") {
                EmptyStateCard(
                    icon = Icons.Filled.History,
                    title = "No historical tasks yet",
                    subtitle = "Machine reports are generated automatically when tasks are marked Done or Rejected."
                )
            }
        } else {
            items(filteredLogs, key = { "hist_${it.log.id}" }) { item ->
                TaskHistoryCard(
                    item = item,
                    machine = machinesById[item.log.machineId],
                    onDelete = { onDeleteLog(item.log.id) }
                )
            }
        }

        item(key = "history_bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun TaskHistoryCard(
    item: ServiceLogWithParts,
    machine: MachineEntity?,
    onDelete: () -> Unit
) {
    val log = item.log
    val sem = LocalMachinaColors.current
    val isRejected = log.outcome == TaskOutcome.REJECTED
    val isMalfunction = log.entryType == ServiceEntryType.MALFUNCTION_REPAIR

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRejected || isMalfunction) sem.critical.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_task_card_${log.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TaskOutcomePill(log.outcome)
                log.cadenceSnapshot?.let { CadencePill(it) }
                if (isMalfunction && !isRejected) {
                    StatusPill("MALFUNCTION", sem.criticalContainer, sem.critical)
                    SeverityPill(log.severity)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_history_${log.id}")) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete history record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(log.summaryTitle, style = MaterialTheme.typography.titleMedium)

            if (machine != null) {
                Text(
                    text = "${machine.assetCode} · ${machine.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isRejected && log.rejectionReason.isNotBlank()) {
                Surface(
                    color = sem.criticalContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rejection Reason: ${log.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = sem.critical,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else if (log.malfunctionDetails.isNotBlank()) {
                Text(
                    text = log.malfunctionDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = Formatters.formatDateTime(log.completedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(log.personInCharge, style = MaterialTheme.typography.labelSmall)
                }
                if (log.assistantEngineers.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "+ ${log.assistantEngineers}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (!isRejected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(Formatters.formatDuration(log.serviceDurationMinutes), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (item.parts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    item.parts.forEach { part ->
                        val idStr = if (part.partCode.isNotBlank()) "[${part.partCode}] " else ""
                        Text(
                            text = "• $idStr${part.partName} × ${part.quantity} ${part.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
