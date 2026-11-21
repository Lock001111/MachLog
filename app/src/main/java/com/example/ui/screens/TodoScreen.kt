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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.RepeatRule
import com.example.data.TodoReminderEntity
import com.example.engine.Formatters
import com.example.ui.MachinaUiState
import com.example.ui.TodoFilterState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.PriorityPill
import com.example.ui.components.RepeatRulePill
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMachinaColors

@Composable
fun TodoScreen(
    state: MachinaUiState,
    onUpdateFilter: ((TodoFilterState) -> TodoFilterState) -> Unit,
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

    val activeCount = remember(state.todos) { state.todos.count { !it.isCompleted } }
    val overdueCount = remember(state.todos, now) {
        state.todos.count { !it.isCompleted && it.dueAtMillis < now }
    }
    val recurringCount = remember(state.todos) {
        state.todos.count { it.repeatRule != RepeatRule.ONCE }
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
        }
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
                    onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q) } },
                    placeholder = { Text("Search reminders, notes, or PIC...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search reminders") },
                    trailingIcon = {
                        if (filter.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onUpdateFilter { it.copy(searchQuery = "") } }) {
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
                        onClick = { onUpdateFilter { it.copy(repeatRule = null) } },
                        label = { Text("All Schedules") }
                    )
                    RepeatRule.entries.forEach { rule ->
                        FilterChip(
                            selected = filter.repeatRule == rule,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(repeatRule = if (it.repeatRule == rule) null else rule)
                                }
                            },
                            label = { Text(rule.label) },
                            modifier = Modifier.testTag("todo_rule_chip_${rule.name.lowercase()}")
                        )
                    }
                    FilterChip(
                        selected = filter.showCompleted,
                        onClick = { onUpdateFilter { it.copy(showCompleted = !it.showCompleted) } },
                        label = { Text("Show Done") },
                        modifier = Modifier.testTag("todo_show_completed_chip")
                    )
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
            items(filteredTodos, key = { "todo_${it.id}" }) { todo ->
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
                            "Done"
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
