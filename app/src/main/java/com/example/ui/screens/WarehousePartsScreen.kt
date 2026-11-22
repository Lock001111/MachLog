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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MachineEntity
import com.example.data.ServiceLogEntity
import com.example.data.ServiceLogWithParts
import com.example.data.SparePartUsedEntity
import com.example.data.WarehousePartEntity
import com.example.engine.Formatters
import com.example.ui.MachinaUiState
import com.example.ui.components.CompactSearchField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ListPaginationFooter
import com.example.ui.components.StatusPill
import com.example.ui.components.TaskOutcomePill

data class PartUsageRecord(
    val log: ServiceLogEntity,
    val machine: MachineEntity?,
    val usedPart: SparePartUsedEntity
)

fun findSparePartUsageHistory(
    part: WarehousePartEntity,
    serviceLogs: List<ServiceLogWithParts>,
    machinesById: Map<Long, MachineEntity>
): List<PartUsageRecord> {
    val code = part.partCode.trim()
    val name = part.partName.trim()
    return serviceLogs.flatMap { entry ->
        entry.parts.filter { used ->
            (code.isNotBlank() && used.partCode.equals(code, ignoreCase = true)) ||
                (name.isNotBlank() && used.partName.equals(name, ignoreCase = true))
        }.map { used ->
            PartUsageRecord(
                log = entry.log,
                machine = machinesById[entry.log.machineId],
                usedPart = used
            )
        }
    }.sortedByDescending { it.log.completedAtMillis }
}

fun filterWarehouseParts(
    parts: List<WarehousePartEntity>,
    query: String,
    minQueryLength: Int = 0
): List<WarehousePartEntity> {
    val cleanQuery = query.trim()
    if (minQueryLength > 0 && cleanQuery.length < minQueryLength) return emptyList()
    val tokens = cleanQuery.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return parts
    return parts.filter { part ->
        val haystack = "${part.partCode} ${part.partName} ${part.partDescription}".lowercase()
        tokens.all { token -> haystack.contains(token) }
    }
}

@Composable
fun WarehousePartsScreen(
    state: MachinaUiState,
    onSetListPageSize: (Int) -> Unit,
    onAddPart: () -> Unit,
    onEditPart: (WarehousePartEntity) -> Unit,
    onDeletePart: (WarehousePartEntity) -> Unit,
    onImportExcel: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<WarehousePartEntity?>(null) }
    var historyTargetPart by remember { mutableStateOf<WarehousePartEntity?>(null) }
    val machinesById = remember(state.machines) { state.machines.associateBy { it.id } }
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }

    val filteredParts = remember(state.warehouseParts, searchQuery) {
        filterWarehouseParts(state.warehouseParts, searchQuery)
    }
    val pagedParts = remember(filteredParts, currentPage, pageSize) {
        paginateItems(filteredParts, currentPage, pageSize)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Warehouse Catalog Summary Card (Codes & Descriptions — No Stock Quantities)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Warehouse Spare Parts Catalog",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${state.warehouseParts.size} part codes & descriptions registered · duplicates auto-checked on Excel import",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImportExcel,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("warehouse_import_excel_button")
                    ) {
                        Icon(Icons.Filled.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import Excel")
                    }
                    OutlinedButton(
                        onClick = onAddPart,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("warehouse_header_add_part_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Part Code")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Instant Search by Spare Part ID / Code or Description
        CompactSearchField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                currentPage = 0
            },
            placeholder = "Search spare part ID/code or description...",
            searchTag = "warehouse_search_input",
            modifier = Modifier.fillMaxWidth()
        )

        if (searchQuery.isNotBlank()) {
            Text(
                text = "Showing ${filteredParts.size} of ${state.warehouseParts.size} spare parts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (filteredParts.isEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Inventory2,
                        title = if (state.warehouseParts.isEmpty()) "No warehouse parts yet" else "No matching spare parts",
                        subtitle = if (state.warehouseParts.isEmpty()) {
                            "Add spare part IDs and descriptions manually or import a full catalog from Excel (.xlsx)."
                        } else {
                            "Try a shorter part ID, keyword, or clear your search filter."
                        },
                        actionLabel = "Add Spare Part",
                        onAction = onAddPart
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedParts.items, key = { it.id }) { part ->
                    WarehousePartCard(
                        part = part,
                        onHistory = { historyTargetPart = part },
                        onEdit = { onEditPart(part) },
                        onDelete = { pendingDelete = part }
                    )
                }

                item(key = "warehouse_pagination_footer") {
                    ListPaginationFooter(
                        startNumber = pagedParts.startNumber,
                        endNumber = pagedParts.endNumber,
                        totalItems = pagedParts.totalItems,
                        safePageIndex = pagedParts.safePageIndex,
                        totalPages = pagedParts.totalPages,
                        pageSize = pageSize,
                        onPageSizeChange = { newSize ->
                            currentPage = 0
                            onSetListPageSize(newSize)
                        },
                        onPrevPage = { currentPage = (pagedParts.safePageIndex - 1).coerceAtLeast(0) },
                        onNextPage = { currentPage = (pagedParts.safePageIndex + 1).coerceAtMost(pagedParts.totalPages - 1) },
                        testTagPrefix = "warehouse"
                    )
                }
            }
        }
    }

    historyTargetPart?.let { target ->
        val records = remember(target, state.serviceLogs, machinesById) {
            findSparePartUsageHistory(target, state.serviceLogs, machinesById)
        }
        WarehousePartHistoryDialog(
            part = target,
            records = records,
            onDismiss = { historyTargetPart = null }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Spare Part?") },
            text = {
                Text(
                    "Remove ${target.partCode}${if (target.partName.isNotBlank()) " · ${target.partName}" else ""} from the warehouse parts list? Existing historical service logs will keep their saved part records."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePart(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_warehouse_part_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WarehousePartCard(
    part: WarehousePartEntity,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("warehouse_part_card_${part.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        text = part.partCode,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    StatusPill(
                        text = part.defaultUnit,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (part.partName.isNotBlank()) {
                    Text(
                        text = part.partName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (part.partDescription.isNotBlank()) {
                    Text(
                        text = part.partDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onHistory,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("history_warehouse_part_${part.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Usage history for ${part.partCode}",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_warehouse_part_${part.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit ${part.partCode}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_warehouse_part_${part.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${part.partCode}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun WarehousePartHistoryDialog(
    part: WarehousePartEntity,
    records: List<PartUsageRecord>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${part.partCode} · Usage History (${records.size})")
                if (part.partName.isNotBlank() || part.partDescription.isNotBlank()) {
                    Text(
                        text = part.partName.ifBlank { part.partDescription },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (records.isEmpty()) {
                    Text(
                        text = "This spare part has not been recorded on any machine maintenance history yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    records.forEach { rec ->
                        val log = rec.log
                        val machine = rec.machine
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("part_usage_record_${log.id}_${rec.usedPart.id}")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    TaskOutcomePill(log.outcome)
                                    StatusPill(
                                        text = "×${rec.usedPart.quantity} ${rec.usedPart.unit}",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = machine?.let { "${it.assetCode} · ${it.name}" } ?: "Unknown Machine",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = log.summaryTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Used: ${Formatters.formatDateTime(log.completedAtMillis)} · Created: ${Formatters.formatDateTime(log.createdAtMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = buildString {
                                        append("PIC: ${log.personInCharge}")
                                        if (log.assistantEngineers.isNotBlank()) {
                                            append(" · Assistants: ${log.assistantEngineers}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_part_history_button")
            ) {
                Text("Close")
            }
        }
    )
}
