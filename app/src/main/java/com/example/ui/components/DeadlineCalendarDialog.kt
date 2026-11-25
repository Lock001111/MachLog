package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.TodoReminderEntity
import com.example.engine.Formatters
import com.example.ui.theme.LocalMachinaColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarItemFilter(val label: String) {
    ALL("All"),
    TASKS("Tasks"),
    TODOS("To-Do")
}

data class CalendarDayEntries(
    val dayKey: Long,
    val tasks: List<MaintenanceTaskEntity> = emptyList(),
    val todos: List<TodoReminderEntity> = emptyList()
) {
    val totalCount: Int get() = tasks.size + todos.size
    fun hasOverdue(nowMillis: Long): Boolean =
        tasks.any { it.deadlineMillis < nowMillis } ||
            todos.any { !it.isCompleted && it.dueAtMillis < nowMillis }
}

fun localDayKey(millis: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return (cal.get(Calendar.YEAR).toLong() * 10000L) +
        ((cal.get(Calendar.MONTH) + 1).toLong() * 100L) +
        cal.get(Calendar.DAY_OF_MONTH).toLong()
}

fun buildCalendarDayEntries(
    tasks: List<MaintenanceTaskEntity>,
    todos: List<TodoReminderEntity>,
    filter: CalendarItemFilter = CalendarItemFilter.ALL,
    includeCompletedTodos: Boolean = false
): Map<Long, CalendarDayEntries> {
    val map = linkedMapOf<Long, CalendarDayEntries>()
    if (filter == CalendarItemFilter.ALL || filter == CalendarItemFilter.TASKS) {
        tasks.sortedBy { it.deadlineMillis }.forEach { task ->
            val key = localDayKey(task.deadlineMillis)
            val cur = map[key] ?: CalendarDayEntries(dayKey = key)
            map[key] = cur.copy(tasks = cur.tasks + task)
        }
    }
    if (filter == CalendarItemFilter.ALL || filter == CalendarItemFilter.TODOS) {
        todos.filter { includeCompletedTodos || !it.isCompleted }
            .sortedBy { it.dueAtMillis }
            .forEach { todo ->
                val key = localDayKey(todo.dueAtMillis)
                val cur = map[key] ?: CalendarDayEntries(dayKey = key)
                map[key] = cur.copy(todos = cur.todos + todo)
            }
    }
    return map
}

private fun dayKeyToMillis(dayKey: Long): Long {
    val year = (dayKey / 10000L).toInt()
    val monthZero = (((dayKey % 10000L) / 100L).toInt() - 1).coerceIn(0, 11)
    val day = (dayKey % 100L).toInt().coerceIn(1, 31)
    return Calendar.getInstance().apply {
        set(year, monthZero, day, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
fun DeadlineCalendarDialog(
    tasks: List<MaintenanceTaskEntity>,
    todos: List<TodoReminderEntity>,
    machines: List<MachineEntity>,
    onDismiss: () -> Unit,
    onEditTask: (MaintenanceTaskEntity) -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    onEditTodo: (TodoReminderEntity) -> Unit,
    onToggleTodoComplete: (TodoReminderEntity) -> Unit,
    onAddTaskForDate: (Long) -> Unit,
    onAddTodoForDate: (Long) -> Unit
) {
    val sem = LocalMachinaColors.current
    val now = System.currentTimeMillis()
    val todayKey = remember { localDayKey(now) }
    val todayCal = remember { Calendar.getInstance() }
    var visibleYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var visibleMonthZero by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }
    var selectedDayKey by remember { mutableStateOf(todayKey) }
    var filter by remember { mutableStateOf(CalendarItemFilter.ALL) }
    var includeCompletedTodos by remember { mutableStateOf(false) }

    val machinesById = remember(machines) { machines.associateBy { it.id } }
    val entriesByDay = remember(tasks, todos, filter, includeCompletedTodos) {
        buildCalendarDayEntries(tasks, todos, filter, includeCompletedTodos)
    }

    val monthTitle = remember(visibleYear, visibleMonthZero) {
        val cal = Calendar.getInstance().apply { set(visibleYear, visibleMonthZero, 1) }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(cal.timeInMillis))
    }

    val selectedDateLabel = remember(selectedDayKey) {
        Formatters.formatDateOnly(dayKeyToMillis(selectedDayKey))
    }
    val selectedDayEntries = entriesByDay[selectedDayKey] ?: CalendarDayEntries(dayKey = selectedDayKey)

    val monthGridCells = remember(visibleYear, visibleMonthZero) {
        val firstOfMonth = Calendar.getInstance().apply {
            set(visibleYear, visibleMonthZero, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val leadingBlanks = (firstOfMonth.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).let {
            if (it < 0) it + 7 else it
        }
        val daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<Int?>()
        repeat(leadingBlanks) { cells += null }
        for (d in 1..daysInMonth) cells += d
        while (cells.size % 7 != 0) cells += null
        cells
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(0.96f)
                .widthIn(max = 860.dp)
                .heightIn(max = 760.dp)
                .testTag("deadline_calendar_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dialog Top Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Deadline Calendar",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Tap a date to inspect tasks & to-do deadlines",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("calendar_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Calendar")
                    }
                }
                HorizontalDivider()

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    // Month Navigation & Filter Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (visibleMonthZero == 0) {
                                        visibleMonthZero = 11
                                        visibleYear -= 1
                                    } else {
                                        visibleMonthZero -= 1
                                    }
                                },
                                modifier = Modifier.testTag("calendar_prev_month")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                            }
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.widthIn(min = 120.dp),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = {
                                    if (visibleMonthZero == 11) {
                                        visibleMonthZero = 0
                                        visibleYear += 1
                                    } else {
                                        visibleMonthZero += 1
                                    }
                                },
                                modifier = Modifier.testTag("calendar_next_month")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                            }
                        }
                        TextButton(
                            onClick = {
                                val c = Calendar.getInstance()
                                visibleYear = c.get(Calendar.YEAR)
                                visibleMonthZero = c.get(Calendar.MONTH)
                                selectedDayKey = todayKey
                            },
                            modifier = Modifier.testTag("calendar_today_button")
                        ) {
                            Text("Today")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        CalendarItemFilter.entries.forEach { item ->
                            FilterChip(
                                selected = filter == item,
                                onClick = { filter = item },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("calendar_filter_${item.name.lowercase()}")
                            )
                        }
                        FilterChip(
                            selected = includeCompletedTodos,
                            onClick = { includeCompletedTodos = !includeCompletedTodos },
                            label = { Text("Done To-Dos") },
                            modifier = Modifier.testTag("calendar_toggle_done_todos")
                        )
                    }

                    // Weekday Headers
                    val dayNames = remember { listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat") }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        dayNames.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Month Grid
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        monthGridCells.chunked(7).forEach { week ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                week.forEach { dayNum ->
                                    if (dayNum == null) {
                                        Spacer(modifier = Modifier.weight(1f).height(52.dp))
                                    } else {
                                        val key = (visibleYear.toLong() * 10000L) +
                                            ((visibleMonthZero + 1).toLong() * 100L) +
                                            dayNum.toLong()
                                        val entry = entriesByDay[key]
                                        val isSelected = key == selectedDayKey
                                        val isToday = key == todayKey
                                        val hasOverdue = entry?.hasOverdue(now) == true
                                        val taskCount = entry?.tasks?.size ?: 0
                                        val todoCount = entry?.todos?.size ?: 0

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                hasOverdue -> sem.criticalContainer.copy(alpha = 0.45f)
                                                entry != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else if (isToday) 1.5.dp else 1.dp,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.tertiary
                                                    hasOverdue -> sem.critical.copy(alpha = 0.6f)
                                                    else -> MaterialTheme.colorScheme.outlineVariant
                                                }
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedDayKey = key }
                                                .testTag("calendar_day_$key")
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                            ) {
                                                Text(
                                                    text = "$dayNum",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        hasOverdue -> sem.critical
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                if (taskCount > 0 || todoCount > 0) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (taskCount > 0) {
                                                            Box(
                                                                contentAlignment = Alignment.Center,
                                                                modifier = Modifier
                                                                    .background(
                                                                        color = if (hasOverdue) sem.critical else MaterialTheme.colorScheme.primary,
                                                                        shape = RoundedCornerShape(6.dp)
                                                                    )
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text(
                                                                    text = "${taskCount}T",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                    color = MaterialTheme.colorScheme.onPrimary
                                                                )
                                                            }
                                                        }
                                                        if (todoCount > 0) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(7.dp)
                                                                    .background(sem.warning, CircleShape)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

                    // Quick Detail Section for Selected Date
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$selectedDateLabel (${selectedDayEntries.totalCount})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.testTag("calendar_selected_date_title")
                            )
                            Text(
                                text = "${selectedDayEntries.tasks.size} task(s) · ${selectedDayEntries.todos.size} reminder(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onAddTodoForDate(dayKeyToMillis(selectedDayKey)) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("calendar_add_todo_for_date")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Reminder", maxLines = 1)
                            }
                            Button(
                                onClick = { onAddTaskForDate(dayKeyToMillis(selectedDayKey)) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("calendar_add_task_for_date")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Task", maxLines = 1)
                            }
                        }
                    }

                    if (selectedDayEntries.totalCount == 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No maintenance task deadlines or to-do reminders scheduled for $selectedDateLabel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        selectedDayEntries.tasks.forEach { task ->
                            val machine = machinesById[task.machineId]
                            val overdue = task.deadlineMillis < now
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (overdue) sem.critical.copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar_detail_task_${task.id}")
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.TaskAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        StatusPill(
                                            text = "TASK",
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        PriorityPill(task.priority)
                                        StatusPill(
                                            text = task.cadence.label,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        StatusPill(
                                            text = Formatters.formatRelativeDeadline(task.deadlineMillis, now),
                                            containerColor = if (overdue) sem.criticalContainer else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (overdue) sem.critical else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(machine?.let { "${it.assetCode} · ${it.name}" } ?: "Machine #${task.machineId}")
                                            append(" · Due ${Formatters.formatDateTime(task.deadlineMillis)}")
                                            if (task.personInCharge.isNotBlank()) append(" · PIC: ${task.personInCharge}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (task.description.isNotBlank()) {
                                        Text(
                                            text = task.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onEditTask(task) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Edit")
                                        }
                                        OutlinedButton(
                                            onClick = { onRejectTask(task) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reject")
                                        }
                                        Button(
                                            onClick = { onCompleteTask(task) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Done")
                                        }
                                    }
                                }
                            }
                        }

                        selectedDayEntries.todos.forEach { todo ->
                            val machine = todo.machineId?.let { machinesById[it] }
                            val overdue = !todo.isCompleted && todo.dueAtMillis < now
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (overdue) sem.warning.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar_detail_todo_${todo.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { onToggleTodoComplete(todo) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = if (todo.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = if (todo.isCompleted) "Reopen reminder" else "Complete reminder",
                                            tint = if (todo.isCompleted) sem.operational else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.NotificationsActive,
                                                contentDescription = null,
                                                tint = sem.warning,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            StatusPill(
                                                text = "TO-DO",
                                                containerColor = sem.warningContainer,
                                                contentColor = sem.warning
                                            )
                                            PriorityPill(todo.priority)
                                            StatusPill(
                                                text = todo.repeatRule.label,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = todo.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = buildString {
                                                append("Due ${Formatters.formatDateTime(todo.dueAtMillis)}")
                                                if (machine != null) append(" · ${machine.assetCode}")
                                                if (todo.personInCharge.isNotBlank()) append(" · PIC: ${todo.personInCharge}")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    }
                                    IconButton(
                                        onClick = { onEditTodo(todo) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Reminder")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
