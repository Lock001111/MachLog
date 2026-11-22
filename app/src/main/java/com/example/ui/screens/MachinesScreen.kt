package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceTaskEntity
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogWithParts
import com.example.data.TaskOutcome
import com.example.engine.Formatters
import com.example.ui.MachinaUiState
import com.example.ui.MachineFilterState
import com.example.ui.MachineGroupBy
import com.example.ui.components.CadencePill
import com.example.ui.components.CollapsibleFilterPanel
import com.example.ui.components.CompactSearchField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GroupBadge
import com.example.ui.components.ListPaginationFooter
import com.example.ui.components.MachineStatusBadge
import com.example.ui.components.PriorityPill
import com.example.ui.components.SeverityPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskOutcomePill
import com.example.ui.theme.LocalMachinaColors

@Composable
fun MachinesScreen(
    state: MachinaUiState,
    isExpanded: Boolean,
    onUpdateFilter: ((MachineFilterState) -> MachineFilterState) -> Unit,
    onSetListPageSize: (Int) -> Unit,
    onSelectMachine: (Long?) -> Unit,
    onAddMachine: () -> Unit,
    onEditMachine: (MachineEntity) -> Unit,
    onDeleteMachine: (MachineEntity) -> Unit,
    onUpdateMachineStatus: (Long, MachineStatus) -> Unit,
    onAddTaskForMachine: (Long) -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    onManageGroups: () -> Unit,
    onImportExcel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var compactShowingDetail by rememberSaveable { mutableStateOf(false) }
    val selectedMachine = remember(state.machines, state.selectedMachineId) {
        state.machines.find { it.id == state.selectedMachineId } ?: state.machines.firstOrNull()
    }

    if (!isExpanded && compactShowingDetail && selectedMachine != null) {
        BackHandler {
            compactShowingDetail = false
        }
        MachineDetailPane(
            machine = selectedMachine,
            state = state,
            showBackButton = true,
            onBack = { compactShowingDetail = false },
            onEditMachine = { onEditMachine(selectedMachine) },
            onDeleteMachine = {
                onDeleteMachine(selectedMachine)
                compactShowingDetail = false
            },
            onUpdateStatus = { status -> onUpdateMachineStatus(selectedMachine.id, status) },
            onAddTask = { onAddTaskForMachine(selectedMachine.id) },
            onCompleteTask = onCompleteTask,
            onRejectTask = onRejectTask,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    if (isExpanded) {
        Row(modifier = modifier.fillMaxSize()) {
            MachineListPane(
                state = state,
                selectedMachineId = selectedMachine?.id,
                onUpdateFilter = onUpdateFilter,
                onSetPageSize = onSetListPageSize,
                onClickMachine = { id -> onSelectMachine(id) },
                onAddMachine = onAddMachine,
                onManageGroups = onManageGroups,
                onImportExcel = onImportExcel,
                modifier = Modifier
                    .weight(0.43f)
                    .fillMaxHeight()
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (selectedMachine != null) {
                MachineDetailPane(
                    machine = selectedMachine,
                    state = state,
                    showBackButton = false,
                    onBack = {},
                    onEditMachine = { onEditMachine(selectedMachine) },
                    onDeleteMachine = { onDeleteMachine(selectedMachine) },
                    onUpdateStatus = { status -> onUpdateMachineStatus(selectedMachine.id, status) },
                    onAddTask = { onAddTaskForMachine(selectedMachine.id) },
                    onCompleteTask = onCompleteTask,
                    onRejectTask = onRejectTask,
                    modifier = Modifier
                        .weight(0.57f)
                        .fillMaxHeight()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(0.57f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.PrecisionManufacturing,
                        title = "Select a machine",
                        subtitle = "Choose equipment on the left or add a new machine to inspect tasks and malfunction logs.",
                        actionLabel = "Add Machine",
                        onAction = onAddMachine
                    )
                }
            }
        }
    } else {
        MachineListPane(
            state = state,
            selectedMachineId = null,
            onUpdateFilter = onUpdateFilter,
            onSetPageSize = onSetListPageSize,
            onClickMachine = { id ->
                onSelectMachine(id)
                compactShowingDetail = true
            },
            onAddMachine = onAddMachine,
            onManageGroups = onManageGroups,
            onImportExcel = onImportExcel,
            modifier = modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MachineListPane(
    state: MachinaUiState,
    selectedMachineId: Long?,
    onUpdateFilter: ((MachineFilterState) -> MachineFilterState) -> Unit,
    onSetPageSize: (Int) -> Unit,
    onClickMachine: (Long) -> Unit,
    onAddMachine: () -> Unit,
    onManageGroups: () -> Unit,
    onImportExcel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filter = state.machineFilter
    val groupsById = remember(state.groups) { state.groups.associateBy { it.id } }
    val sem = LocalMachinaColors.current
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }
    var showGroupByMenu by remember { mutableStateOf(false) }

    val operationalCount = state.machines.count { it.status == MachineStatus.OPERATIONAL }
    val dueCount = state.machines.count { it.status == MachineStatus.MAINTENANCE_DUE }
    val malfunctionCount = state.machines.count { it.status == MachineStatus.MALFUNCTION }

    val filteredMachines = remember(state.machines, filter) {
        state.machines.filter { m ->
            val matchStatus = filter.status == null || m.status == filter.status
            val matchGroup = filter.groupId == null || m.groupId == filter.groupId
            val q = filter.searchQuery.trim()
            val matchQuery = q.isEmpty() ||
                m.name.contains(q, ignoreCase = true) ||
                m.assetCode.contains(q, ignoreCase = true) ||
                m.locationZone.contains(q, ignoreCase = true) ||
                m.modelInfo.contains(q, ignoreCase = true)
            matchStatus && matchGroup && matchQuery
        }
    }

    val pagedMachines = remember(filteredMachines, filter.currentPage, pageSize) {
        paginateItems(filteredMachines, filter.currentPage, pageSize)
    }

    val grouped = remember(pagedMachines.items, filter.groupBy, groupsById) {
        when (filter.groupBy) {
            MachineGroupBy.NONE -> listOf("All Machines" to pagedMachines.items)
            MachineGroupBy.STATUS -> MachineStatus.entries.mapNotNull { st ->
                val list = pagedMachines.items.filter { it.status == st }
                if (list.isEmpty()) null else st.label to list
            }
            MachineGroupBy.CUSTOM_GROUP -> pagedMachines.items.groupBy { m ->
                m.groupId?.let { groupsById[it]?.name } ?: "Ungrouped"
            }.toList()
        }
    }

    val activeFilterCount = remember(filter) {
        (if (filter.status != null) 1 else 0) + (if (filter.groupId != null) 1 else 0)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        item(key = "fleet_hero") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.img_factory_banner_1795188995484),
                        contentDescription = "Factory equipment fleet overview illustration",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        StatusPill("$operationalCount Operational", sem.operationalContainer, sem.operational)
                        StatusPill("$dueCount Maint. Due", sem.warningContainer, sem.warning)
                        StatusPill("$malfunctionCount Down", sem.criticalContainer, sem.critical)
                    }
                }
            }
        }

        item(key = "machine_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CompactSearchField(
                        value = filter.searchQuery,
                        onValueChange = { q -> onUpdateFilter { it.copy(searchQuery = q, currentPage = 0) } },
                        placeholder = "Search asset code, machine, zone...",
                        searchTag = "machines_search_input",
                        contentDescription = "Search machines",
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showGroupByMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("machines_group_by_button")
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "Group machines", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(filter.groupBy.label, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = showGroupByMenu,
                            onDismissRequest = { showGroupByMenu = false }
                        ) {
                            MachineGroupBy.entries.forEach { mode ->
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
                            MachineFilterState(
                                groupBy = it.groupBy,
                                searchQuery = it.searchQuery
                            )
                        }
                    },
                    testTagPrefix = "machines",
                    inlineQuickActions = {
                        TextButton(
                            onClick = onManageGroups,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("machines_manage_groups_button")
                        ) {
                            Icon(Icons.Filled.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Groups")
                        }
                        OutlinedButton(
                            onClick = onImportExcel,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("machines_import_excel_button")
                        ) {
                            Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import")
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
                            selected = filter.status == null,
                            onClick = { onUpdateFilter { it.copy(status = null, currentPage = 0) } },
                            label = { Text("All (${state.machines.size})") }
                        )
                        MachineStatus.entries.forEach { st ->
                            FilterChip(
                                selected = filter.status == st,
                                onClick = {
                                    onUpdateFilter {
                                        it.copy(
                                            status = if (it.status == st) null else st,
                                            currentPage = 0
                                        )
                                    }
                                },
                                label = { Text(st.label) }
                            )
                        }
                    }

                    if (state.groups.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
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
                                    label = { Text(group.name) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (filteredMachines.isEmpty()) {
            item(key = "empty_machines") {
                EmptyStateCard(
                    icon = Icons.Filled.PrecisionManufacturing,
                    title = "No machines found",
                    subtitle = "Add factory machines and organize them into custom production lines or bays.",
                    actionLabel = "Add Machine",
                    onAction = onAddMachine
                )
            }
        } else {
            grouped.forEach { (sectionTitle, sectionMachines) ->
                if (filter.groupBy != MachineGroupBy.NONE) {
                    item(key = "m_header_$sectionTitle") {
                        Text(
                            text = "$sectionTitle (${sectionMachines.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                items(sectionMachines, key = { "machine_${it.id}" }) { machine ->
                    val group = machine.groupId?.let { groupsById[it] }
                    val openTaskCount = state.tasks.count { it.machineId == machine.id }
                    val overdueTaskCount = state.tasks.count {
                        it.machineId == machine.id && it.deadlineMillis < System.currentTimeMillis()
                    }
                    MachineCard(
                        machine = machine,
                        group = group,
                        openTaskCount = openTaskCount,
                        overdueTaskCount = overdueTaskCount,
                        isSelected = machine.id == selectedMachineId,
                        onClick = { onClickMachine(machine.id) }
                    )
                }
            }

            item(key = "machines_pagination_footer") {
                ListPaginationFooter(
                    startNumber = pagedMachines.startNumber,
                    endNumber = pagedMachines.endNumber,
                    totalItems = pagedMachines.totalItems,
                    safePageIndex = pagedMachines.safePageIndex,
                    totalPages = pagedMachines.totalPages,
                    pageSize = pageSize,
                    onPageSizeChange = onSetPageSize,
                    onPrevPage = {
                        onUpdateFilter { it.copy(currentPage = (pagedMachines.safePageIndex - 1).coerceAtLeast(0)) }
                    },
                    onNextPage = {
                        onUpdateFilter {
                            it.copy(currentPage = (pagedMachines.safePageIndex + 1).coerceAtMost(pagedMachines.totalPages - 1))
                        }
                    },
                    testTagPrefix = "machines"
                )
            }
        }

        item(key = "machines_bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MachineCard(
    machine: MachineEntity,
    group: CustomGroupEntity?,
    openTaskCount: Int,
    overdueTaskCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val sem = LocalMachinaColors.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("machine_card_${machine.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill(
                    text = machine.assetCode,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                MachineStatusBadge(machine.status)
                if (group != null) {
                    GroupBadge(group)
                }
            }

            Text(
                text = machine.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${machine.locationZone} · ${machine.modelInfo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$openTaskCount scheduled tasks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (overdueTaskCount > 0) {
                    StatusPill(
                        text = "$overdueTaskCount OVERDUE",
                        containerColor = sem.criticalContainer,
                        contentColor = sem.critical
                    )
                }
            }
        }
    }
}

@Composable
private fun MachineDetailPane(
    machine: MachineEntity,
    state: MachinaUiState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onEditMachine: () -> Unit,
    onDeleteMachine: () -> Unit,
    onUpdateStatus: (MachineStatus) -> Unit,
    onAddTask: () -> Unit,
    onCompleteTask: (MaintenanceTaskEntity) -> Unit,
    onRejectTask: (MaintenanceTaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val group = machine.groupId?.let { gid -> state.groups.find { it.id == gid } }
    val machineTasks = remember(state.tasks, machine.id) {
        state.tasks.filter { it.machineId == machine.id }
    }
    val machineLogs = remember(state.serviceLogs, machine.id) {
        state.serviceLogs.filter { it.log.machineId == machine.id }
    }
    val now = System.currentTimeMillis()
    val sem = LocalMachinaColors.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        item(key = "detail_header") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showBackButton) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("machine_detail_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to machines")
                            }
                        }
                        StatusPill(
                            text = machine.assetCode,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        if (group != null) GroupBadge(group)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onEditMachine, modifier = Modifier.testTag("edit_machine_button")) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit machine")
                        }
                        IconButton(onClick = onDeleteMachine, modifier = Modifier.testTag("delete_machine_button")) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete machine", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Text(machine.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${machine.locationZone}  |  ${machine.modelInfo}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (machine.notes.isNotBlank()) {
                        Text(
                            text = machine.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Status Switcher
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        MachineStatus.entries.forEach { st ->
                            FilterChip(
                                selected = machine.status == st,
                                onClick = { onUpdateStatus(st) },
                                label = { Text(st.label) },
                                modifier = Modifier.testTag("machine_status_chip_${st.name.lowercase()}")
                            )
                        }
                    }

                    Button(
                        onClick = onAddTask,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("machine_add_task_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add New Service Task")
                    }
                    Text(
                        text = "Reports below are automatically generated when a task is marked Complete or Rejected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "machine_tasks_heading") {
            Text(
                text = "Scheduled Tasks (${machineTasks.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (machineTasks.isEmpty()) {
            item(key = "no_machine_tasks") {
                EmptyStateCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "No tasks scheduled",
                    subtitle = "Add daily, weekly, monthly, or yearly maintenance tasks for ${machine.assetCode}.",
                    actionLabel = "Add Task",
                    onAction = onAddTask
                )
            }
        } else {
            items(machineTasks, key = { "mt_${it.id}" }) { task ->
                val overdue = task.deadlineMillis < now
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PriorityPill(task.priority)
                                CadencePill(task.cadence)
                                StatusPill(
                                    text = Formatters.formatRelativeDeadline(task.deadlineMillis, now),
                                    containerColor = if (overdue) sem.criticalContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (overdue) sem.critical else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Created: ${Formatters.formatDateTime(task.createdAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buildString {
                                    append("PIC: ${task.personInCharge}")
                                    if (task.assistantEngineers.isNotBlank()) {
                                        append(" (+${task.assistantEngineers})")
                                    }
                                    append(" · Est. ${Formatters.formatDuration(task.estimatedMinutes)}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onRejectTask(task) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = sem.critical),
                                border = BorderStroke(1.dp, sem.critical.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = "Reject task", modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reject")
                            }
                            Button(
                                onClick = { onCompleteTask(task) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
        }

        item(key = "machine_logs_heading") {
            Text(
                text = "Machine Service & Rejection Reports (${machineLogs.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (machineLogs.isEmpty()) {
            item(key = "no_machine_logs") {
                EmptyStateCard(
                    icon = Icons.Filled.Warning,
                    title = "No reports generated yet",
                    subtitle = "Machine reports appear automatically when tasks are marked Done or Rejected."
                )
            }
        } else {
            items(machineLogs, key = { "ml_${it.log.id}" }) { item ->
                MachineHistoryMiniCard(item)
            }
        }

        item(key = "detail_bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MachineHistoryMiniCard(item: ServiceLogWithParts) {
    val log = item.log
    val sem = LocalMachinaColors.current
    val isRejected = log.outcome == TaskOutcome.REJECTED
    val isMalfunction = log.entryType == ServiceEntryType.MALFUNCTION_REPAIR
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isRejected) sem.critical.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TaskOutcomePill(log.outcome)
                if (isMalfunction && !isRejected) {
                    StatusPill("MALFUNCTION", sem.criticalContainer, sem.critical)
                    SeverityPill(log.severity)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.formatDateOnly(log.completedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(log.summaryTitle, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Created: ${Formatters.formatDateTime(log.createdAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isRejected && log.rejectionReason.isNotBlank()) {
                Text(
                    text = "Rejection Reason: ${log.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = sem.critical
                )
            } else if (log.malfunctionDetails.isNotBlank()) {
                Text(
                    text = log.malfunctionDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = buildString {
                    append("PIC: ${log.personInCharge}")
                    if (log.assistantEngineers.isNotBlank()) {
                        append(" · Assistants: ${log.assistantEngineers}")
                    }
                    if (!isRejected) {
                        append(" · Time: ${Formatters.formatDuration(log.serviceDurationMinutes)}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.parts.isNotEmpty()) {
                item.parts.forEach { p ->
                    val idPrefix = if (p.partCode.isNotBlank()) "[${p.partCode}] " else ""
                    val descSuffix = if (p.partDescription.isNotBlank()) " — ${p.partDescription}" else ""
                    val whTag = if (p.fromWarehouse) " [Warehouse]" else ""
                    Text(
                        text = "• $idPrefix${p.partName}$descSuffix$whTag (×${p.quantity} ${p.unit})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
