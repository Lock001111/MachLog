package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogWithParts
import com.example.data.TaskOutcome
import com.example.data.TodoReminderEntity
import com.example.data.parseEngineerList
import com.example.engine.Formatters
import com.example.ui.HistoryFilterState
import com.example.ui.HistoryGroupBy
import com.example.ui.MachinaUiState
import com.example.ui.TodoFilterState
import com.example.ui.TodoGroupBy
import com.example.ui.components.CadencePill
import com.example.ui.components.CollapsibleFilterPanel
import com.example.ui.components.CompactSearchField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.ListPaginationFooter
import com.example.ui.components.PriorityPill
import com.example.ui.components.RepeatRulePill
import com.example.ui.components.SeverityPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskOutcomePill
import com.example.ui.theme.LocalMachinaColors
import java.util.concurrent.TimeUnit

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

sealed class TodoFeedItem {
    abstract val stableKey: String
    abstract val title: String
    abstract val subtitle: String
    abstract val priority: ReminderPriority
    abstract val machineId: Long?
    abstract val effectiveGroupId: Long?
    abstract val personInCharge: String
    abstract val createdAtMillis: Long
    abstract val triggerAtMillis: Long
    abstract val dueAtMillis: Long
    abstract val isCompleted: Boolean
    abstract val completedAtMillis: Long?
    abstract val reminderEnabled: Boolean

    data class StandaloneTodo(
        val todo: TodoReminderEntity,
        override val effectiveGroupId: Long?
    ) : TodoFeedItem() {
        override val stableKey: String get() = "todo_${todo.id}"
        override val title: String get() = todo.title
        override val subtitle: String get() = todo.notes
        override val priority: ReminderPriority get() = todo.priority
        override val machineId: Long? get() = todo.machineId
        override val personInCharge: String get() = todo.personInCharge
        override val createdAtMillis: Long get() = todo.createdAtMillis
        override val triggerAtMillis: Long get() = todo.dueAtMillis
        override val dueAtMillis: Long get() = todo.dueAtMillis
        override val isCompleted: Boolean get() = todo.isCompleted
        override val completedAtMillis: Long? get() = todo.completedAtMillis
        override val reminderEnabled: Boolean get() = todo.reminderEnabled
    }

    data class LinkedTaskReminder(
        val task: MaintenanceTaskEntity,
        override val effectiveGroupId: Long?,
        override val triggerAtMillis: Long
    ) : TodoFeedItem() {
        override val stableKey: String get() = "task_rem_${task.id}"
        override val title: String get() = task.title
        override val subtitle: String get() = task.description
        override val priority: ReminderPriority get() = task.priority
        override val machineId: Long? get() = task.machineId
        override val personInCharge: String get() = task.personInCharge
        override val createdAtMillis: Long get() = task.createdAtMillis
        override val dueAtMillis: Long get() = task.deadlineMillis
        override val isCompleted: Boolean get() = false
        override val completedAtMillis: Long? get() = null
        override val reminderEnabled: Boolean get() = task.reminderEnabled
    }
}

fun buildTodoFeedItems(
    todos: List<TodoReminderEntity>,
    tasks: List<MaintenanceTaskEntity>,
    machinesById: Map<Long, MachineEntity>,
    filter: TodoFilterState
): List<TodoFeedItem> {
    val standalone = todos.map { td ->
        val effGroup = td.groupId ?: td.machineId?.let { machinesById[it]?.groupId }
        TodoFeedItem.StandaloneTodo(todo = td, effectiveGroupId = effGroup)
    }
    val linked = tasks.filter { it.reminderEnabled }.map { t ->
        val effGroup = t.groupId ?: machinesById[t.machineId]?.groupId
        val trigger = t.deadlineMillis - TimeUnit.HOURS.toMillis(t.reminderLeadHours.coerceAtLeast(1).toLong())
        TodoFeedItem.LinkedTaskReminder(
            task = t,
            effectiveGroupId = effGroup,
            triggerAtMillis = trigger
        )
    }

    val combined = standalone + linked
    val q = filter.searchQuery.trim()
    val filtered = combined.filter { item ->
        val matchCompleted = filter.showCompleted || !item.isCompleted
        val matchRepeat = filter.repeatRule == null || when (item) {
            is TodoFeedItem.StandaloneTodo -> item.todo.repeatRule == filter.repeatRule
            is TodoFeedItem.LinkedTaskReminder -> item.task.cadence.name == filter.repeatRule.name
        }
        val matchPriority = filter.priority == null || item.priority == filter.priority
        val matchGroup = filter.groupId == null || item.effectiveGroupId == filter.groupId
        val matchPic = filter.picFilter.isNullOrBlank() ||
            item.personInCharge.equals(filter.picFilter, ignoreCase = true) ||
            (item is TodoFeedItem.LinkedTaskReminder &&
                parseEngineerList(item.task.assistantEngineers).any { it.equals(filter.picFilter, ignoreCase = true) })
        val m = item.machineId?.let { machinesById[it] }
        val matchQuery = q.isEmpty() ||
            item.title.contains(q, ignoreCase = true) ||
            item.subtitle.contains(q, ignoreCase = true) ||
            item.personInCharge.contains(q, ignoreCase = true) ||
            (item is TodoFeedItem.LinkedTaskReminder && item.task.assistantEngineers.contains(q, ignoreCase = true)) ||
            (m?.name?.contains(q, ignoreCase = true) == true) ||
            (m?.assetCode?.contains(q, ignoreCase = true) == true)
        matchCompleted && matchRepeat && matchPriority && matchGroup && matchPic && matchQuery
    }

    return filtered.sortedWith { a, b ->
        if (a.isCompleted != b.isCompleted) {
            return@sortedWith if (a.isCompleted) 1 else -1
        }
        val aKey = if (!a.isCompleted) a.triggerAtMillis else (a.completedAtMillis ?: a.dueAtMillis)
        val bKey = if (!b.isCompleted) b.triggerAtMillis else (b.completedAtMillis ?: b.dueAtMillis)
        if (filter.sortAscending) aKey.compareTo(bKey) else bKey.compareTo(aKey)
    }
}

const val COMPLETED_TODO_GROUP_TITLE = "Completed Tasks & To-Do Reminder"

fun groupTodoFeedItems(
    items: List<TodoFeedItem>,
    groupBy: TodoGroupBy
): List<Pair<String, List<TodoFeedItem>>> {
    val ongoingItems = items.filterNot { it.isCompleted }
    val completedItems = items.filter { it.isCompleted }

    val ongoingSections = when {
        ongoingItems.isEmpty() -> emptyList()
        groupBy == TodoGroupBy.NONE -> listOf("All Reminders" to ongoingItems)
        groupBy == TodoGroupBy.SOURCE -> listOfNotNull(
            ongoingItems.filterIsInstance<TodoFeedItem.LinkedTaskReminder>()
                .takeIf { it.isNotEmpty() }
                ?.let { "From Maintenance Tasks" to it },
            ongoingItems.filterIsInstance<TodoFeedItem.StandaloneTodo>()
                .takeIf { it.isNotEmpty() }
                ?.let { "Plain To-Do Reminders" to it }
        )
        groupBy == TodoGroupBy.PRIORITY -> listOf(
            ReminderPriority.URGENT,
            ReminderPriority.HIGH,
            ReminderPriority.NORMAL
        ).mapNotNull { prio ->
            val list = ongoingItems.filter { it.priority == prio }
            if (list.isEmpty()) null else "${prio.label} Priority" to list
        }
        else -> ongoingItems.groupBy { item ->
            item.personInCharge.ifBlank { "Unassigned" }
        }.toList()
    }

    return if (completedItems.isNotEmpty()) {
        ongoingSections + (COMPLETED_TODO_GROUP_TITLE to completedItems)
    } else {
        ongoingSections
    }
}

fun filterAndSortHistoryLogs(
    serviceLogs: List<ServiceLogWithParts>,
    machinesById: Map<Long, MachineEntity>,
    filter: HistoryFilterState
): List<ServiceLogWithParts> {
    val q = filter.searchQuery.trim().lowercase()
    val filtered = serviceLogs.filter { item ->
        val log = item.log
        val m = machinesById[log.machineId]
        val effGroupId = m?.groupId
        val matchOutcome = filter.outcome == null || log.outcome == filter.outcome
        val matchPriority = filter.priority == null || log.prioritySnapshot == filter.priority
        val matchCadence = filter.cadence == null || log.cadenceSnapshot == filter.cadence
        val matchGroup = filter.groupId == null || effGroupId == filter.groupId
        val matchPic = filter.picFilter.isNullOrBlank() ||
            log.personInCharge.equals(filter.picFilter, ignoreCase = true) ||
            parseEngineerList(log.assistantEngineers).any { it.equals(filter.picFilter, ignoreCase = true) }
        val partsHay = item.parts.joinToString(" ") { "${it.partCode} ${it.partName} ${it.partDescription}" }
        val matchQuery = q.isEmpty() ||
            log.summaryTitle.lowercase().contains(q) ||
            log.malfunctionDetails.lowercase().contains(q) ||
            log.rejectionReason.lowercase().contains(q) ||
            log.personInCharge.lowercase().contains(q) ||
            log.assistantEngineers.lowercase().contains(q) ||
            partsHay.lowercase().contains(q) ||
            (m?.assetCode?.lowercase()?.contains(q) == true) ||
            (m?.name?.lowercase()?.contains(q) == true)
        matchOutcome && matchPriority && matchCadence && matchGroup && matchPic && matchQuery
    }
    return if (filter.sortAscending) {
        filtered.sortedBy { it.log.completedAtMillis }
    } else {
        filtered.sortedByDescending { it.log.completedAtMillis }
    }
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
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onEditTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val groupsById = remember(state.groups) { state.groups.associateBy { it.id } }
    val filter = state.todoFilter
    val sem = LocalMachinaColors.current
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }
    var showPicMenu by remember { mutableStateOf(false) }
    var showGroupByMenu by remember { mutableStateOf(false) }
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val allFeedItems = remember(state.todos, state.tasks, machinesById) {
        buildTodoFeedItems(state.todos, state.tasks, machinesById, TodoFilterState(showCompleted = true))
    }
    val openCount = remember(allFeedItems) { allFeedItems.count { !it.isCompleted } }
    val completedCount = remember(state.todos) { state.todos.count { it.isCompleted } }
    val overdueCount = remember(allFeedItems, now) {
        allFeedItems.count { !it.isCompleted && it.dueAtMillis < now }
    }
    val approachingCount = remember(allFeedItems, now) {
        allFeedItems.count { item ->
            !item.isCompleted &&
                item.dueAtMillis >= now &&
                (item.triggerAtMillis <= now || (item.dueAtMillis - now) <= TimeUnit.HOURS.toMillis(24))
        }
    }

    val filteredTodos = remember(state.todos, state.tasks, filter, machinesById) {
        buildTodoFeedItems(state.todos, state.tasks, machinesById, filter)
    }
    val pagedTodos = remember(filteredTodos, filter.currentPage, pageSize) {
        paginateItems(filteredTodos, filter.currentPage, pageSize)
    }

    val groupedTodos = remember(pagedTodos.items, filter.groupBy) {
        groupTodoFeedItems(pagedTodos.items, filter.groupBy)
    }

    val activeFilterCount = remember(filter) {
        (if (filter.repeatRule != null) 1 else 0) +
            (if (filter.priority != null) 1 else 0) +
            (if (filter.groupId != null) 1 else 0) +
            (if (!filter.picFilter.isNullOrBlank()) 1 else 0) +
            (if (filter.showCompleted) 1 else 0)
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
                    label = "Open",
                    value = "$openCount",
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
                    label = "Approaching",
                    value = "$approachingCount",
                    icon = Icons.Filled.Schedule,
                    accentColor = sem.warning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item(key = "todo_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactSearchField(
                        value = filter.searchQuery,
                        onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q, currentPage = 0) } },
                        placeholder = "Search reminders, tasks, machine, or PIC...",
                        searchTag = "todo_search_input",
                        contentDescription = "Search reminders",
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showGroupByMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("todo_group_by_button")
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "Group reminders", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (filter.groupBy) {
                                    TodoGroupBy.SOURCE -> "Source"
                                    TodoGroupBy.PRIORITY -> "Priority"
                                    TodoGroupBy.PIC -> "PIC"
                                    TodoGroupBy.NONE -> "Flat"
                                },
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = showGroupByMenu,
                            onDismissRequest = { showGroupByMenu = false }
                        ) {
                            TodoGroupBy.entries.forEach { mode ->
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

                CollapsibleFilterPanel(
                    activeFilterCount = activeFilterCount,
                    onResetFilters = {
                        onUpdateFilter {
                            TodoFilterState(
                                groupBy = it.groupBy,
                                sortAscending = it.sortAscending,
                                searchQuery = it.searchQuery
                            )
                        }
                    },
                    testTagPrefix = "todo",
                    inlineQuickActions = {
                        FilterChip(
                            selected = true,
                            onClick = { onUpdateFilter { it.copy(sortAscending = !it.sortAscending, currentPage = 0) } },
                            leadingIcon = {
                                Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (filter.sortAscending) "Date ↑ Soonest" else "Date ↓ Latest")
                            },
                            modifier = Modifier.testTag("todo_date_sort_chip")
                        )
                        Surface(
                            color = if (filter.showCompleted) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable {
                                onUpdateFilter { it.copy(showCompleted = !it.showCompleted, currentPage = 0) }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 10.dp)
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
                                    text = "Done ($completedCount)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = filter.priority == null,
                            onClick = { onUpdateFilter { it.copy(priority = null, currentPage = 0) } },
                            label = { Text("All Priorities") },
                            modifier = Modifier.testTag("todo_priority_chip_all")
                        )
                        ReminderPriority.entries.forEach { prio ->
                            FilterChip(
                                selected = filter.priority == prio,
                                onClick = {
                                    onUpdateFilter {
                                        it.copy(
                                            priority = if (it.priority == prio) null else prio,
                                            currentPage = 0
                                        )
                                    }
                                },
                                label = { Text(prio.label) },
                                modifier = Modifier.testTag("todo_priority_chip_${prio.name.lowercase()}")
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
                                modifier = Modifier.testTag("todo_pic_filter_chip")
                            )
                            DropdownMenu(
                                expanded = showPicMenu,
                                onDismissRequest = { showPicMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Engineers") },
                                    onClick = {
                                        onUpdateFilter { it.copy(picFilter = null, currentPage = 0) }
                                        showPicMenu = false
                                    }
                                )
                                state.knownEngineers.forEach { pic ->
                                    DropdownMenuItem(
                                        text = { Text(pic) },
                                        onClick = {
                                            onUpdateFilter { it.copy(picFilter = pic, currentPage = 0) }
                                            showPicMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = filter.repeatRule == null,
                            onClick = { onUpdateFilter { it.copy(repeatRule = null, currentPage = 0) } },
                            label = { Text("All Cadences") },
                            modifier = Modifier.testTag("todo_rule_chip_all")
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

                    if (state.groups.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = filter.groupId == null,
                                onClick = { onUpdateFilter { it.copy(groupId = null, currentPage = 0) } },
                                label = { Text("All Groups") }
                            )
                            state.groups.forEach { group ->
                                FilterChip(
                                    selected = filter.groupId == group.id,
                                    onClick = {
                                        onUpdateFilter {
                                            it.copy(
                                                groupId = if (it.groupId == group.id) null else group.id,
                                                currentPage = 0
                                            )
                                        }
                                    },
                                    label = { Text(group.name) },
                                    modifier = Modifier.testTag("todo_group_chip_${group.id}")
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
                    subtitle = "Create personal engineer to-dos or enable deadline reminders on maintenance tasks.",
                    actionLabel = "New Reminder",
                    onAction = onAddTodo
                )
            }
        } else {
            groupedTodos.forEach { (sectionTitle, sectionItems) ->
                val isCollapsed = collapsedGroups[sectionTitle] == true
                val isCompletedSection = sectionTitle == COMPLETED_TODO_GROUP_TITLE
                val sectionOverdue = sectionItems.count { !it.isCompleted && it.dueAtMillis < now }

                if (filter.groupBy != TodoGroupBy.NONE || isCompletedSection) {
                    item(key = "todo_group_header_$sectionTitle") {
                        Surface(
                            color = if (isCompletedSection) {
                                sem.operationalContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            border = if (isCompletedSection) {
                                BorderStroke(1.dp, sem.operational.copy(alpha = 0.45f))
                            } else null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { collapsedGroups[sectionTitle] = !isCollapsed }
                                .testTag("todo_group_header_$sectionTitle")
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
                                        containerColor = sem.criticalContainer,
                                        contentColor = sem.critical
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                StatusPill(
                                    text = "${sectionItems.size}",
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
                    items(sectionItems, key = { it.stableKey }) { feedItem ->
                        when (feedItem) {
                            is TodoFeedItem.StandaloneTodo -> {
                                val todo = feedItem.todo
                                val machine = todo.machineId?.let { machinesById[it] }
                                val group = feedItem.effectiveGroupId?.let { groupsById[it] }
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
                            is TodoFeedItem.LinkedTaskReminder -> {
                                val task = feedItem.task
                                val machine = machinesById[task.machineId]
                                val group = feedItem.effectiveGroupId?.let { groupsById[it] }
                                LinkedTaskReminderCard(
                                    task = task,
                                    triggerAtMillis = feedItem.triggerAtMillis,
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
            }

            item(key = "todo_pagination_footer") {
                ListPaginationFooter(
                    startNumber = pagedTodos.startNumber,
                    endNumber = pagedTodos.endNumber,
                    totalItems = pagedTodos.totalItems,
                    safePageIndex = pagedTodos.safePageIndex,
                    totalPages = pagedTodos.totalPages,
                    pageSize = pageSize,
                    onPageSizeChange = onSetPageSize,
                    onPrevPage = {
                        onUpdateFilter { it.copy(currentPage = (pagedTodos.safePageIndex - 1).coerceAtLeast(0)) }
                    },
                    onNextPage = {
                        onUpdateFilter {
                            it.copy(currentPage = (pagedTodos.safePageIndex + 1).coerceAtMost(pagedTodos.totalPages - 1))
                        }
                    },
                    testTagPrefix = "todo"
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

                Text(
                    text = "Created: ${Formatters.formatDateTime(todo.createdAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
private fun LinkedTaskReminderCard(
    task: MaintenanceTaskEntity,
    triggerAtMillis: Long,
    machine: MachineEntity?,
    group: CustomGroupEntity?,
    nowMillis: Long,
    onComplete: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    val sem = LocalMachinaColors.current
    val isOverdue = task.deadlineMillis < nowMillis
    val isTriggered = !isOverdue && triggerAtMillis <= nowMillis

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isOverdue) 1.5.dp else 1.dp,
            color = when {
                isOverdue -> sem.critical.copy(alpha = 0.5f)
                isTriggered -> sem.warning.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            }
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("linked_task_reminder_card_${task.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                StatusPill(
                    text = "TASK REMINDER",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
                        color = MaterialTheme.colorScheme.primary,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                StatusPill(
                    text = when {
                        isOverdue -> Formatters.formatRelativeDeadline(task.deadlineMillis, nowMillis)
                        isTriggered -> "Reminder Due · ${Formatters.formatLeadTime(task.reminderLeadHours)} lead"
                        else -> "Remind ${Formatters.formatDateTime(triggerAtMillis)}"
                    },
                    containerColor = when {
                        isOverdue -> sem.criticalContainer
                        isTriggered -> sem.warningContainer
                        else -> sem.infoContainer
                    },
                    contentColor = when {
                        isOverdue -> sem.critical
                        isTriggered -> sem.warning
                        else -> sem.info
                    }
                )
                Text(
                    text = "Deadline: ${Formatters.formatDateTime(task.deadlineMillis)}",
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
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
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
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Block, contentDescription = "Reject task", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reject")
                }
                Button(
                    onClick = onComplete,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Complete task", modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Complete")
                }
            }
        }
    }
}

@Composable
fun TaskHistorySubTab(
    state: MachinaUiState,
    onUpdateFilter: ((HistoryFilterState) -> HistoryFilterState) -> Unit,
    onSetPageSize: (Int) -> Unit,
    onDeleteLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val groupsById = remember(state.groups) { state.groups.associateBy { it.id } }
    val filter = state.historyFilter
    val sem = LocalMachinaColors.current
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }
    var showPicMenu by remember { mutableStateOf(false) }
    var showGroupByMenu by remember { mutableStateOf(false) }
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val doneCount = remember(state.serviceLogs) {
        state.serviceLogs.count { it.log.outcome == TaskOutcome.DONE }
    }
    val rejectedCount = remember(state.serviceLogs) {
        state.serviceLogs.count { it.log.outcome == TaskOutcome.REJECTED }
    }

    val filteredLogs = remember(state.serviceLogs, filter, machinesById) {
        filterAndSortHistoryLogs(state.serviceLogs, machinesById, filter)
    }
    val pagedLogs = remember(filteredLogs, filter.currentPage, pageSize) {
        paginateItems(filteredLogs, filter.currentPage, pageSize)
    }

    val groupedLogs = remember(pagedLogs.items, filter.groupBy, machinesById, groupsById) {
        when (filter.groupBy) {
            HistoryGroupBy.NONE -> listOf("All History Records" to pagedLogs.items)
            HistoryGroupBy.OUTCOME -> TaskOutcome.entries.mapNotNull { out ->
                val list = pagedLogs.items.filter { it.log.outcome == out }
                if (list.isEmpty()) null else "${out.label} Jobs" to list
            }
            HistoryGroupBy.CADENCE -> pagedLogs.items.groupBy { entry ->
                entry.log.cadenceSnapshot?.let { "${it.label} Maintenance" } ?: "Direct / Ad-Hoc Log"
            }.toList()
            HistoryGroupBy.MACHINE -> pagedLogs.items.groupBy { entry ->
                machinesById[entry.log.machineId]?.let { "${it.assetCode} · ${it.name}" } ?: "Unknown Machine"
            }.toList()
            HistoryGroupBy.PIC -> pagedLogs.items.groupBy { entry ->
                entry.log.personInCharge.ifBlank { "Unassigned" }
            }.toList()
            HistoryGroupBy.CUSTOM_GROUP -> pagedLogs.items.groupBy { entry ->
                val machine = machinesById[entry.log.machineId]
                machine?.groupId?.let { groupsById[it]?.name } ?: "Ungrouped"
            }.toList()
        }
    }

    val activeFilterCount = remember(filter) {
        (if (filter.outcome != null) 1 else 0) +
            (if (filter.priority != null) 1 else 0) +
            (if (filter.cadence != null) 1 else 0) +
            (if (filter.groupId != null) 1 else 0) +
            (if (!filter.picFilter.isNullOrBlank()) 1 else 0)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactSearchField(
                        value = filter.searchQuery,
                        onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q, currentPage = 0) } },
                        placeholder = "Search task, reason, engineer, or part...",
                        searchTag = "task_history_search_input",
                        contentDescription = "Search history",
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showGroupByMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("history_group_by_button")
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "Group history", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (filter.groupBy) {
                                    HistoryGroupBy.CUSTOM_GROUP -> "Custom Group"
                                    HistoryGroupBy.OUTCOME -> "Outcome"
                                    HistoryGroupBy.CADENCE -> "Cadence"
                                    HistoryGroupBy.MACHINE -> "Machine"
                                    HistoryGroupBy.PIC -> "PIC"
                                    HistoryGroupBy.NONE -> "Flat List"
                                },
                                maxLines = 1
                            )
                        }
                        DropdownMenu(
                            expanded = showGroupByMenu,
                            onDismissRequest = { showGroupByMenu = false }
                        ) {
                            HistoryGroupBy.entries.forEach { mode ->
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

                CollapsibleFilterPanel(
                    activeFilterCount = activeFilterCount,
                    onResetFilters = {
                        onUpdateFilter {
                            HistoryFilterState(
                                groupBy = it.groupBy,
                                sortAscending = it.sortAscending,
                                searchQuery = it.searchQuery
                            )
                        }
                    },
                    testTagPrefix = "history",
                    inlineQuickActions = {
                        FilterChip(
                            selected = true,
                            onClick = { onUpdateFilter { it.copy(sortAscending = !it.sortAscending, currentPage = 0) } },
                            leadingIcon = {
                                Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (filter.sortAscending) "Recorded ↑ Oldest" else "Recorded ↓ Newest")
                            },
                            modifier = Modifier.testTag("history_date_sort_chip")
                        )
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = filter.outcome == null,
                            onClick = { onUpdateFilter { it.copy(outcome = null, currentPage = 0) } },
                            label = { Text("All (${state.serviceLogs.size})") },
                            modifier = Modifier.testTag("history_filter_all")
                        )
                        FilterChip(
                            selected = filter.outcome == TaskOutcome.DONE,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(
                                        outcome = if (it.outcome == TaskOutcome.DONE) null else TaskOutcome.DONE,
                                        currentPage = 0
                                    )
                                }
                            },
                            label = { Text("Done ($doneCount)") },
                            modifier = Modifier.testTag("history_filter_done")
                        )
                        FilterChip(
                            selected = filter.outcome == TaskOutcome.REJECTED,
                            onClick = {
                                onUpdateFilter {
                                    it.copy(
                                        outcome = if (it.outcome == TaskOutcome.REJECTED) null else TaskOutcome.REJECTED,
                                        currentPage = 0
                                    )
                                }
                            },
                            label = { Text("Rejected ($rejectedCount)") },
                            modifier = Modifier.testTag("history_filter_rejected")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = filter.priority == null,
                            onClick = { onUpdateFilter { it.copy(priority = null, currentPage = 0) } },
                            label = { Text("All Priorities") }
                        )
                        ReminderPriority.entries.forEach { prio ->
                            FilterChip(
                                selected = filter.priority == prio,
                                onClick = {
                                    onUpdateFilter {
                                        it.copy(
                                            priority = if (it.priority == prio) null else prio,
                                            currentPage = 0
                                        )
                                    }
                                },
                                label = { Text(prio.label) },
                                modifier = Modifier.testTag("history_priority_chip_${prio.name.lowercase()}")
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
                                modifier = Modifier.testTag("history_pic_filter_chip")
                            )
                            DropdownMenu(
                                expanded = showPicMenu,
                                onDismissRequest = { showPicMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Engineers") },
                                    onClick = {
                                        onUpdateFilter { it.copy(picFilter = null, currentPage = 0) }
                                        showPicMenu = false
                                    }
                                )
                                state.knownEngineers.forEach { pic ->
                                    DropdownMenuItem(
                                        text = { Text(pic) },
                                        onClick = {
                                            onUpdateFilter { it.copy(picFilter = pic, currentPage = 0) }
                                            showPicMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = filter.cadence == null,
                            onClick = { onUpdateFilter { it.copy(cadence = null, currentPage = 0) } },
                            label = { Text("All Cadences") }
                        )
                        Cadence.entries.forEach { cad ->
                            FilterChip(
                                selected = filter.cadence == cad,
                                onClick = {
                                    onUpdateFilter {
                                        it.copy(
                                            cadence = if (it.cadence == cad) null else cad,
                                            currentPage = 0
                                        )
                                    }
                                },
                                label = { Text(cad.label) },
                                modifier = Modifier.testTag("history_cadence_chip_${cad.name.lowercase()}")
                            )
                        }
                    }

                    if (state.groups.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = filter.groupId == null,
                                onClick = { onUpdateFilter { it.copy(groupId = null, currentPage = 0) } },
                                label = { Text("All Groups") }
                            )
                            state.groups.forEach { group ->
                                FilterChip(
                                    selected = filter.groupId == group.id,
                                    onClick = {
                                        onUpdateFilter {
                                            it.copy(
                                                groupId = if (it.groupId == group.id) null else group.id,
                                                currentPage = 0
                                            )
                                        }
                                    },
                                    label = { Text(group.name) },
                                    modifier = Modifier.testTag("history_group_chip_${group.id}")
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item(key = "empty_history") {
                EmptyStateCard(
                    icon = Icons.Filled.History,
                    title = "No matching historical records",
                    subtitle = "Machine reports are generated automatically when tasks are marked Done or Rejected."
                )
            }
        } else {
            groupedLogs.forEach { (sectionTitle, sectionLogs) ->
                val isCollapsed = collapsedGroups[sectionTitle] == true
                val sectionRejected = sectionLogs.count { it.log.outcome == TaskOutcome.REJECTED }

                if (filter.groupBy != HistoryGroupBy.NONE) {
                    item(key = "history_group_header_$sectionTitle") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { collapsedGroups[sectionTitle] = !isCollapsed }
                                .testTag("history_group_header_$sectionTitle")
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
                                if (sectionRejected > 0) {
                                    StatusPill(
                                        text = "$sectionRejected REJECTED",
                                        containerColor = sem.criticalContainer,
                                        contentColor = sem.critical
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                StatusPill(
                                    text = "${sectionLogs.size}",
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
                    items(sectionLogs, key = { "hist_${it.log.id}" }) { item ->
                        val machine = machinesById[item.log.machineId]
                        val group = machine?.groupId?.let { groupsById[it] }
                        TaskHistoryCard(
                            item = item,
                            machine = machine,
                            group = group,
                            onDelete = { onDeleteLog(item.log.id) }
                        )
                    }
                }
            }

            item(key = "history_pagination_footer") {
                ListPaginationFooter(
                    startNumber = pagedLogs.startNumber,
                    endNumber = pagedLogs.endNumber,
                    totalItems = pagedLogs.totalItems,
                    safePageIndex = pagedLogs.safePageIndex,
                    totalPages = pagedLogs.totalPages,
                    pageSize = pageSize,
                    onPageSizeChange = onSetPageSize,
                    onPrevPage = {
                        onUpdateFilter { it.copy(currentPage = (pagedLogs.safePageIndex - 1).coerceAtLeast(0)) }
                    },
                    onNextPage = {
                        onUpdateFilter {
                            it.copy(currentPage = (pagedLogs.safePageIndex + 1).coerceAtMost(pagedLogs.totalPages - 1))
                        }
                    },
                    testTagPrefix = "history"
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
    group: CustomGroupEntity?,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    TaskOutcomePill(log.outcome)
                    log.prioritySnapshot?.let { PriorityPill(it) }
                    log.cadenceSnapshot?.let { CadencePill(it) }
                    if (group != null) {
                        GroupBadge(group)
                    }
                    if (isMalfunction && !isRejected) {
                        StatusPill("MALFUNCTION", sem.criticalContainer, sem.critical)
                        SeverityPill(log.severity)
                    }
                }
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

            Text(
                text = "Created: ${Formatters.formatDateTime(log.createdAtMillis)} · Recorded: ${Formatters.formatDateTime(log.completedAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
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
