package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SupplierEntity
import com.example.data.WarehousePartEntity
import com.example.engine.Formatters
import com.example.ui.MachinaUiState
import com.example.ui.components.CompactSearchField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ListPaginationFooter
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMachinaColors

@Composable
fun SuppliersScreen(
    state: MachinaUiState,
    onSelectSupplier: (Long) -> Unit,
    onSetListPageSize: (Int) -> Unit,
    onAddSupplier: () -> Unit,
    onEditSupplier: (SupplierEntity) -> Unit,
    onDeleteSupplier: (SupplierEntity) -> Unit,
    onEditPart: (WarehousePartEntity) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<SupplierEntity?>(null) }
    val pageSize = state.preferences.todoPageSize.let { if (it in listOf(25, 50, 100)) it else 25 }

    val partsBySupplier = remember(state.warehouseParts) {
        state.warehouseParts.groupBy { it.supplierId }
    }

    val filteredSuppliers = remember(state.suppliers, partsBySupplier, searchQuery) {
        val tokens = searchQuery.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            state.suppliers
        } else {
            state.suppliers.filter { sup ->
                val suppliedText = partsBySupplier[sup.id].orEmpty()
                    .joinToString(" ") { "${it.partCode} ${it.partName} ${it.supplierPartSku}" }
                val haystack = "${sup.companyName} ${sup.supplierCode} ${sup.contactPerson} ${sup.phone} ${sup.email} ${sup.address} $suppliedText".lowercase()
                tokens.all { haystack.contains(it) }
            }
        }
    }

    val pagedSuppliers = remember(filteredSuppliers, currentPage, pageSize) {
        paginateItems(filteredSuppliers, currentPage, pageSize)
    }

    val selectedSupplier = remember(state.suppliers, state.selectedSupplierId, filteredSuppliers) {
        state.suppliers.find { it.id == state.selectedSupplierId } ?: filteredSuppliers.firstOrNull()
    }
    val selectedSuppliedParts = remember(selectedSupplier, partsBySupplier) {
        selectedSupplier?.let { partsBySupplier[it.id].orEmpty() }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spare Part Suppliers (${state.suppliers.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${state.warehouseParts.count { it.supplierId != null }} of ${state.warehouseParts.size} warehouse parts linked to a supplier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAddSupplier,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("suppliers_header_add_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Supplier", maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        CompactSearchField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                currentPage = 0
            },
            placeholder = "Search company, contact person, or supplied part code...",
            searchTag = "suppliers_search_input",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        if (filteredSuppliers.isEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Storefront,
                        title = if (state.suppliers.isEmpty()) "No suppliers configured yet" else "No matching suppliers",
                        subtitle = if (state.suppliers.isEmpty()) {
                            "Add supplier companies, contact persons, and link warehouse spare parts with unit pricing."
                        } else {
                            "Try clearing or adjusting your search query."
                        },
                        actionLabel = "Add Supplier",
                        onAction = onAddSupplier
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pagedSuppliers.items, key = { it.id }) { supplier ->
                    val supplied = partsBySupplier[supplier.id].orEmpty()
                    val isSelected = selectedSupplier?.id == supplier.id
                    SupplierCard(
                        supplier = supplier,
                        suppliedParts = supplied,
                        isSelected = isSelected,
                        onSelect = { onSelectSupplier(supplier.id) },
                        onEdit = { onEditSupplier(supplier) },
                        onDelete = { pendingDelete = supplier },
                        onEditPart = onEditPart
                    )
                }

                if (selectedSupplier != null && selectedSuppliedParts.isNotEmpty() && pagedSuppliers.items.none { it.id == selectedSupplier.id }) {
                    item(key = "selected_supplier_summary_${selectedSupplier.id}") {
                        SupplierCard(
                            supplier = selectedSupplier,
                            suppliedParts = selectedSuppliedParts,
                            isSelected = true,
                            onSelect = { onSelectSupplier(selectedSupplier.id) },
                            onEdit = { onEditSupplier(selectedSupplier) },
                            onDelete = { pendingDelete = selectedSupplier },
                            onEditPart = onEditPart
                        )
                    }
                }

                item(key = "suppliers_pagination_footer") {
                    ListPaginationFooter(
                        startNumber = pagedSuppliers.startNumber,
                        endNumber = pagedSuppliers.endNumber,
                        totalItems = pagedSuppliers.totalItems,
                        safePageIndex = pagedSuppliers.safePageIndex,
                        totalPages = pagedSuppliers.totalPages,
                        pageSize = pageSize,
                        onPageSizeChange = { newSize ->
                            currentPage = 0
                            onSetListPageSize(newSize)
                        },
                        onPrevPage = { currentPage = (pagedSuppliers.safePageIndex - 1).coerceAtLeast(0) },
                        onNextPage = { currentPage = (pagedSuppliers.safePageIndex + 1).coerceAtMost(pagedSuppliers.totalPages - 1) },
                        testTagPrefix = "suppliers"
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        val linkedCount = partsBySupplier[target.id].orEmpty().size
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Supplier?") },
            text = {
                Text(
                    buildString {
                        append("Remove ${target.companyName} from the suppliers list?")
                        if (linkedCount > 0) {
                            append(" $linkedCount linked warehouse spare part(s) will remain in your catalog with their supplier cleared.")
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSupplier(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_supplier_button")
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
private fun SupplierCard(
    supplier: SupplierEntity,
    suppliedParts: List<WarehousePartEntity>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEditPart: (WarehousePartEntity) -> Unit
) {
    val sem = LocalMachinaColors.current
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_card_${supplier.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        if (supplier.supplierCode.isNotBlank()) {
                            StatusPill(
                                text = supplier.supplierCode,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        StatusPill(
                            text = "${suppliedParts.size} Part(s) Supplied",
                            containerColor = sem.operationalContainer,
                            contentColor = sem.operational
                        )
                    }
                    Text(
                        text = supplier.companyName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_supplier_${supplier.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${supplier.companyName}", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_supplier_${supplier.id}")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${supplier.companyName}", tint = MaterialTheme.colorScheme.error)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (supplier.contactPerson.isNotBlank()) {
                    SupplierInfoRow(icon = Icons.Filled.Person, text = "Contact: ${supplier.contactPerson}")
                }
                if (supplier.phone.isNotBlank()) {
                    SupplierInfoRow(icon = Icons.Filled.Phone, text = supplier.phone)
                }
                if (supplier.email.isNotBlank()) {
                    SupplierInfoRow(icon = Icons.Filled.Email, text = supplier.email)
                }
                if (supplier.address.isNotBlank()) {
                    SupplierInfoRow(icon = Icons.Filled.LocationOn, text = supplier.address)
                }
                if (supplier.notes.isNotBlank()) {
                    Text(
                        text = supplier.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Spare Parts Supplied by ${supplier.companyName} (${suppliedParts.size})",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (suppliedParts.isEmpty()) {
                    Text(
                        text = "No spare parts assigned yet. Edit any spare part in Parts to link this supplier and unit price.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    suppliedParts.forEach { part ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            onClick = { onEditPart(part) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("supplier_supplied_part_${part.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StatusPill(
                                            text = part.partCode,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        if (part.supplierPartSku.isNotBlank()) {
                                            StatusPill(
                                                text = "SKU: ${part.supplierPartSku}",
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = part.partName.ifBlank { part.partDescription },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val leadText = part.leadTimeDays?.let { " · Lead ${it}d" }.orEmpty()
                                    Text(
                                        text = "Unit: ${part.defaultUnit}$leadText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = Formatters.formatCurrencyPrice(part.unitPrice, part.currency),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "per ${part.defaultUnit}",
                                        style = MaterialTheme.typography.labelSmall,
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

@Composable
private fun SupplierInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SupplierDetailsDialog(
    part: WarehousePartEntity,
    supplier: SupplierEntity?,
    suppliedParts: List<WarehousePartEntity>,
    onDismiss: () -> Unit,
    onEditPart: (WarehousePartEntity) -> Unit,
    onOpenInSuppliersMenu: (Long?) -> Unit
) {
    val context = LocalContext.current
    val sem = LocalMachinaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${part.partCode} · Supplier Details")
                Text(
                    text = part.partName.ifBlank { part.partDescription },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
                // Part Pricing & Lead Time Summary
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Part Supply Pricing",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Unit Price:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (part.unitPrice != null) {
                                    "${Formatters.formatCurrencyPrice(part.unitPrice, part.currency)} / ${part.defaultUnit}"
                                } else {
                                    "Not set"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        if (part.supplierPartSku.isNotBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Supplier SKU:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(part.supplierPartSku, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                        if (part.leadTimeDays != null) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Lead Time:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${part.leadTimeDays} day(s)", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }

                if (supplier == null) {
                    Surface(
                        color = sem.warningContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, sem.warning.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "No supplier linked to this spare part yet.",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Assign a supplier and unit price in Edit Part, or configure your supplier directory in the Suppliers menu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { onEditPart(part) },
                                modifier = Modifier.testTag("supplier_dialog_assign_part_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Assign Supplier & Price")
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = supplier.companyName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                if (supplier.supplierCode.isNotBlank()) {
                                    StatusPill(
                                        text = supplier.supplierCode,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (supplier.contactPerson.isNotBlank()) {
                                SupplierInfoRow(Icons.Filled.Person, "Contact: ${supplier.contactPerson}")
                            }
                            if (supplier.phone.isNotBlank()) {
                                SupplierInfoRow(Icons.Filled.Phone, supplier.phone)
                            }
                            if (supplier.email.isNotBlank()) {
                                SupplierInfoRow(Icons.Filled.Email, supplier.email)
                            }
                            if (supplier.address.isNotBlank()) {
                                SupplierInfoRow(Icons.Filled.LocationOn, supplier.address)
                            }
                            if (supplier.notes.isNotBlank()) {
                                Text(
                                    text = supplier.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (supplier.phone.isNotBlank() || supplier.email.isNotBlank()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    if (supplier.phone.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${supplier.phone}")))
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Call")
                                        }
                                    }
                                    if (supplier.email.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${supplier.email}")))
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Email")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Other Parts Supplied (${suppliedParts.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    suppliedParts.forEach { sp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "${sp.partCode} · ${sp.partName.ifBlank { sp.partDescription }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = Formatters.formatCurrencyPrice(sp.unitPrice, sp.currency),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onOpenInSuppliersMenu(supplier?.id) },
                modifier = Modifier.testTag("redirect_to_suppliers_menu_button")
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Open Suppliers")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_supplier_details_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SupplierEditorDialog(
    initialSupplier: SupplierEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        uuid: String?,
        companyName: String,
        supplierCode: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        notes: String
    ) -> Unit
) {
    var companyName by remember { mutableStateOf(initialSupplier?.companyName ?: "") }
    var supplierCode by remember { mutableStateOf(initialSupplier?.supplierCode ?: "") }
    var contactPerson by remember { mutableStateOf(initialSupplier?.contactPerson ?: "") }
    var phone by remember { mutableStateOf(initialSupplier?.phone ?: "") }
    var email by remember { mutableStateOf(initialSupplier?.email ?: "") }
    var address by remember { mutableStateOf(initialSupplier?.address ?: "") }
    var notes by remember { mutableStateOf(initialSupplier?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialSupplier == null) "Add Supplier" else "Edit Supplier")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = {
                        companyName = it
                        nameError = false
                    },
                    label = { Text("Company Name *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Company name is required") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_company_input")
                )
                OutlinedTextField(
                    value = supplierCode,
                    onValueChange = { supplierCode = it },
                    label = { Text("Supplier Code (e.g. SUP-APX)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_code_input")
                )
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Contact Person") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_contact_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_phone_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_email_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_address_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Terms") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("supplier_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (companyName.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    onSave(
                        initialSupplier?.id ?: 0L,
                        initialSupplier?.uuid,
                        companyName.trim(),
                        supplierCode.trim().uppercase(),
                        contactPerson.trim(),
                        phone.trim(),
                        email.trim(),
                        address.trim(),
                        notes.trim()
                    )
                },
                modifier = Modifier.testTag("save_supplier_button")
            ) {
                Text("Save Supplier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
