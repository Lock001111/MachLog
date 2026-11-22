package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.Cadence
import com.example.data.MachineEntity
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogWithParts
import com.example.data.TaskOutcome
import com.example.engine.ExportFilterParams
import com.example.engine.Formatters
import com.example.engine.ReportExportEngine
import com.example.ui.MachinaUiState
import com.example.ui.components.CadencePill
import com.example.ui.components.CollapsibleFilterPanel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.ListPaginationFooter
import com.example.ui.components.SeverityPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskOutcomePill
import com.example.ui.theme.LocalMachinaColors
import java.util.concurrent.TimeUnit

enum class ReportPeriodPreset(val label: String, val daysBack: Long?) {
    ALL("All Time", null),
    DAYS_7("Last 7d", 7),
    DAYS_30("Last 30d", 30),
    DAYS_365("Last 1y", 365)
}

@Composable
fun ReportsScreen(
    state: MachinaUiState,
    isExpanded: Boolean,
    onSetListPageSize: (Int) -> Unit,
    onUpdateReportFilter: ((ExportFilterParams) -> ExportFilterParams) -> Unit,
    onOpenExportSheet: (preferPdf: Boolean) -> Unit,
    onDeleteLog: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val params = state.reportFilter
    val sem = LocalMachinaColors.current
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    val dataset = remember(state.machines, state.groups, state.tasks, state.serviceLogs, params) {
        ReportExportEngine.filterDataset(
            params = params,
            machines = state.machines,
            groups = state.groups,
            allTasks = state.tasks,
            allLogs = state.serviceLogs
        )
    }
    val pagedLogs = remember(dataset.logs, currentPage, pageSize) {
        paginateItems(dataset.logs, currentPage, pageSize)
    }

    val totalLogs = dataset.logs.size
    val doneCount = dataset.logs.count { it.log.outcome == TaskOutcome.DONE }
    val rejectedCount = dataset.logs.count { it.log.outcome == TaskOutcome.REJECTED }
    val totalPartsQty = dataset.logs.sumOf { item -> item.parts.sumOf { it.quantity } }

    var showMachineMenu by remember { mutableStateOf(false) }
    var showPicMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var activePeriod by remember { mutableStateOf(ReportPeriodPreset.ALL) }

    val activeFilterCount = remember(params, activePeriod) {
        (if (params.machineId != null) 1 else 0) +
            (if (!params.personInCharge.isNullOrBlank()) 1 else 0) +
            (if (params.customGroupId != null) 1 else 0) +
            (if (params.entryType != null) 1 else 0) +
            (if (params.outcome != null) 1 else 0) +
            (if (params.cadence != null) 1 else 0) +
            (if (activePeriod != ReportPeriodPreset.ALL) 1 else 0)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Export Action Bar
        item(key = "export_bar") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Automated Machine Reports & Export",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Reports are auto-generated whenever tasks are completed or rejected. Filter below and export to Excel (.xlsx) or PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onOpenExportSheet(false) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_excel_button")
                        ) {
                            Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Excel")
                        }
                        OutlinedButton(
                            onClick = { onOpenExportSheet(true) },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }
                }
            }
        }

        // KPI Grid
        item(key = "report_kpis") {
            if (isExpanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KpiMetricCard("Total Reports", "$totalLogs", Icons.Filled.History, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    KpiMetricCard("Done", "$doneCount", Icons.Filled.CheckCircle, sem.operational, Modifier.weight(1f))
                    KpiMetricCard("Rejected", "$rejectedCount", Icons.Filled.Block, sem.critical, Modifier.weight(1f))
                    KpiMetricCard("Parts Used", "$totalPartsQty", Icons.Filled.Inventory2, sem.warning, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiMetricCard("Total Reports", "$totalLogs", Icons.Filled.History, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        KpiMetricCard("Done", "$doneCount", Icons.Filled.CheckCircle, sem.operational, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiMetricCard("Rejected", "$rejectedCount", Icons.Filled.Block, sem.critical, Modifier.weight(1f))
                        KpiMetricCard("Parts Used", "$totalPartsQty", Icons.Filled.Inventory2, sem.warning, Modifier.weight(1f))
                    }
                }
            }
        }

        // Parameter Filter Panel (Collapsible)
        item(key = "report_filters") {
            CollapsibleFilterPanel(
                activeFilterCount = activeFilterCount,
                onResetFilters = {
                    activePeriod = ReportPeriodPreset.ALL
                    currentPage = 0
                    onUpdateReportFilter { ExportFilterParams() }
                },
                testTagPrefix = "reports"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Machine Filter
                    Box {
                        val selectedMachine = params.machineId?.let { machinesById[it] }
                        FilterChip(
                            selected = selectedMachine != null,
                            onClick = { showMachineMenu = true },
                            leadingIcon = { Icon(Icons.Filled.PrecisionManufacturing, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(selectedMachine?.assetCode ?: "All Machines") },
                            modifier = Modifier.testTag("report_filter_machine")
                        )
                        DropdownMenu(expanded = showMachineMenu, onDismissRequest = { showMachineMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All Machines") },
                                onClick = {
                                    currentPage = 0
                                    onUpdateReportFilter { it.copy(machineId = null) }
                                    showMachineMenu = false
                                }
                            )
                            state.machines.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.assetCode} · ${m.name}") },
                                    onClick = {
                                        currentPage = 0
                                        onUpdateReportFilter { it.copy(machineId = m.id) }
                                        showMachineMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // PIC Filter
                    Box {
                        FilterChip(
                            selected = !params.personInCharge.isNullOrBlank(),
                            onClick = { showPicMenu = true },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(params.personInCharge ?: "All PICs") },
                            modifier = Modifier.testTag("report_filter_pic")
                        )
                        DropdownMenu(expanded = showPicMenu, onDismissRequest = { showPicMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All Engineers") },
                                onClick = {
                                    currentPage = 0
                                    onUpdateReportFilter { it.copy(personInCharge = null) }
                                    showPicMenu = false
                                }
                            )
                            state.knownEngineers.forEach { pic ->
                                DropdownMenuItem(
                                    text = { Text(pic) },
                                    onClick = {
                                        currentPage = 0
                                        onUpdateReportFilter { it.copy(personInCharge = pic) }
                                        showPicMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Custom Group Filter
                    Box {
                        val selGroup = params.customGroupId?.let { gid -> state.groups.find { it.id == gid } }
                        FilterChip(
                            selected = selGroup != null,
                            onClick = { showGroupMenu = true },
                            label = { Text(selGroup?.name ?: "All Groups") },
                            modifier = Modifier.testTag("report_filter_group")
                        )
                        DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All Custom Groups") },
                                onClick = {
                                    currentPage = 0
                                    onUpdateReportFilter { it.copy(customGroupId = null) }
                                    showGroupMenu = false
                                }
                            )
                            state.groups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g.name) },
                                    onClick = {
                                        currentPage = 0
                                        onUpdateReportFilter { it.copy(customGroupId = g.id) }
                                        showGroupMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Entry Type & Period Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = params.entryType == null,
                        onClick = {
                            currentPage = 0
                            onUpdateReportFilter { it.copy(entryType = null) }
                        },
                        label = { Text("All Types") }
                    )
                    ServiceEntryType.entries.forEach { type ->
                        FilterChip(
                            selected = params.entryType == type,
                            onClick = {
                                currentPage = 0
                                onUpdateReportFilter {
                                    it.copy(entryType = if (it.entryType == type) null else type)
                                }
                            },
                            label = { Text(if (type == ServiceEntryType.PREVENTIVE_SERVICE) "Preventive" else "Malfunctions") }
                        )
                    }
                    ReportPeriodPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = activePeriod == preset,
                            onClick = {
                                activePeriod = preset
                                currentPage = 0
                                val start = preset.daysBack?.let {
                                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it)
                                }
                                onUpdateReportFilter { it.copy(startMillis = start, endMillis = null) }
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }

                // Outcome & Cadence Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = params.outcome == null,
                        onClick = {
                            currentPage = 0
                            onUpdateReportFilter { it.copy(outcome = null) }
                        },
                        label = { Text("All Outcomes") },
                        modifier = Modifier.testTag("report_outcome_all")
                    )
                    TaskOutcome.entries.forEach { out ->
                        FilterChip(
                            selected = params.outcome == out,
                            onClick = {
                                currentPage = 0
                                onUpdateReportFilter {
                                    it.copy(outcome = if (it.outcome == out) null else out)
                                }
                            },
                            label = { Text(out.label) },
                            modifier = Modifier.testTag("report_outcome_${out.name.lowercase()}")
                        )
                    }
                    FilterChip(
                        selected = params.cadence == null,
                        onClick = {
                            currentPage = 0
                            onUpdateReportFilter { it.copy(cadence = null) }
                        },
                        label = { Text("All Cadences") }
                    )
                    Cadence.entries.forEach { cad ->
                        FilterChip(
                            selected = params.cadence == cad,
                            onClick = {
                                currentPage = 0
                                onUpdateReportFilter {
                                    it.copy(cadence = if (it.cadence == cad) null else cad)
                                }
                            },
                            label = { Text(cad.label) }
                        )
                    }
                }
            }
        }

        if (dataset.logs.isEmpty()) {
            item(key = "empty_logs") {
                EmptyStateCard(
                    icon = Icons.Filled.Description,
                    title = "No historical reports matched",
                    subtitle = "Clear or adjust the machine, PIC, group, or date filters to inspect service logs."
                )
            }
        } else {
            items(pagedLogs.items, key = { "log_${it.log.id}" }) { item ->
                val machine = machinesById[item.log.machineId]
                ServiceHistoryReportCard(
                    item = item,
                    machine = machine,
                    onDelete = { onDeleteLog(item.log.id) }
                )
            }

            item(key = "reports_pagination_footer") {
                ListPaginationFooter(
                    startNumber = pagedLogs.startNumber,
                    endNumber = pagedLogs.endNumber,
                    totalItems = pagedLogs.totalItems,
                    safePageIndex = pagedLogs.safePageIndex,
                    totalPages = pagedLogs.totalPages,
                    pageSize = pageSize,
                    onPageSizeChange = { newSize ->
                        currentPage = 0
                        onSetListPageSize(newSize)
                    },
                    onPrevPage = { currentPage = (pagedLogs.safePageIndex - 1).coerceAtLeast(0) },
                    onNextPage = { currentPage = (pagedLogs.safePageIndex + 1).coerceAtMost(pagedLogs.totalPages - 1) },
                    testTagPrefix = "reports"
                )
            }
        }

        item(key = "reports_bottom_spacer") {
            Spacer(Modifier.height(72.dp))
        }
    }
}

@Composable
private fun ServiceHistoryReportCard(
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
            .testTag("report_log_card_${log.id}")
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
                if (isMalfunction && !isRejected) {
                    StatusPill("MALFUNCTION", sem.criticalContainer, sem.critical)
                    SeverityPill(log.severity)
                }
                log.cadenceSnapshot?.let { CadencePill(it) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_log_${log.id}")) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete log",
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
                    StatusPill(
                        text = if (log.deadlineWasMet) "On Time" else "Late",
                        containerColor = if (log.deadlineWasMet) sem.operationalContainer else sem.warningContainer,
                        contentColor = if (log.deadlineWasMet) sem.operational else sem.warning
                    )
                }
            }

            if (item.parts.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.parts.forEach { part ->
                        val idStr = if (part.partCode.isNotBlank()) "ID: ${part.partCode} · " else ""
                        val whStr = if (part.fromWarehouse) " · Warehouse" else ""
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = "$idStr${part.partName} × ${part.quantity} ${part.unit}$whStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (part.partDescription.isNotBlank()) {
                                    Text(
                                        text = part.partDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
