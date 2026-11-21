package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.EngineerEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.Severity
import com.example.data.SparePartDraft
import com.example.data.WarehousePartEntity
import com.example.data.formatEngineerList
import com.example.data.parseEngineerList
import com.example.ui.components.AssistantEngineersSelector
import com.example.ui.components.DateTimePickerField
import com.example.ui.components.EngineerPickerField
import com.example.ui.components.StatusPill
import com.example.ui.screens.filterWarehouseParts
import java.util.concurrent.TimeUnit

@Composable
fun TaskEditorDialog(
    initialTask: MaintenanceTaskEntity?,
    defaultMachineId: Long?,
    defaultEngineerName: String,
    machines: List<MachineEntity>,
    groups: List<CustomGroupEntity>,
    engineers: List<EngineerEntity>,
    onDismiss: () -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onSave: (
        id: Long,
        uuid: String?,
        machineId: Long,
        groupId: Long?,
        title: String,
        description: String,
        cadence: Cadence,
        deadlineMillis: Long,
        estimatedMinutes: Int,
        personInCharge: String,
        assistantEngineers: String,
        reminderEnabled: Boolean,
        reminderLeadHours: Int,
        lastCompletedMillis: Long?
    ) -> Unit
) {
    var selectedMachineId by remember {
        mutableStateOf(initialTask?.machineId ?: defaultMachineId ?: machines.firstOrNull()?.id ?: 0L)
    }
    var selectedGroupId by remember { mutableStateOf(initialTask?.groupId) }
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var cadence by remember { mutableStateOf(initialTask?.cadence ?: Cadence.ONCE) }
    var deadlineMillis by remember {
        mutableStateOf(initialTask?.deadlineMillis ?: (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8)))
    }
    var estimatedMinutesText by remember { mutableStateOf((initialTask?.estimatedMinutes ?: 30).toString()) }

    val initialPic = remember(initialTask, defaultEngineerName, engineers) {
        val candidate = initialTask?.personInCharge ?: defaultEngineerName
        engineers.find { it.name.equals(candidate.trim(), ignoreCase = true) }?.name
            ?: engineers.firstOrNull()?.name
            ?: ""
    }
    var pic by remember { mutableStateOf(initialPic) }
    val selectedAssistants = remember {
        mutableStateListOf<String>().apply {
            addAll(
                parseEngineerList(initialTask?.assistantEngineers.orEmpty())
                    .filterNot { it.equals(initialPic, ignoreCase = true) }
            )
        }
    }

    val initialLeadHours = (initialTask?.reminderLeadHours ?: 24).coerceAtLeast(1)
    val startsAsDays = initialLeadHours >= 24 && initialLeadHours % 24 == 0
    var reminderEnabled by remember { mutableStateOf(initialTask?.reminderEnabled ?: true) }
    var useDaysUnit by remember { mutableStateOf(startsAsDays) }
    var leadValueText by remember {
        mutableStateOf((if (startsAsDays) initialLeadHours / 24 else initialLeadHours).toString())
    }

    var titleError by remember { mutableStateOf(false) }
    var picError by remember { mutableStateOf(false) }

    var showMachineDropdown by remember { mutableStateOf(false) }
    var showGroupDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTask == null) "New Maintenance Task" else "Edit Maintenance Task") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Machine Picker
                Box {
                    val m = machines.find { it.id == selectedMachineId }
                    OutlinedButton(
                        onClick = { showMachineDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("task_editor_machine_picker")
                    ) {
                        Text(
                            text = m?.let { "${it.assetCode} · ${it.name}" } ?: "Select Machine",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMachineDropdown, onDismissRequest = { showMachineDropdown = false }) {
                        machines.forEach { machine ->
                            DropdownMenuItem(
                                text = { Text("${machine.assetCode} · ${machine.name}") },
                                onClick = {
                                    selectedMachineId = machine.id
                                    showMachineDropdown = false
                                }
                            )
                        }
                    }
                }

                // Custom Group Picker
                Box {
                    val g = selectedGroupId?.let { gid -> groups.find { it.id == gid } }
                    OutlinedButton(
                        onClick = { showGroupDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = "Group: ${g?.name ?: "Inherit Machine Group"}",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showGroupDropdown, onDismissRequest = { showGroupDropdown = false }) {
                        DropdownMenuItem(
                            text = { Text("Inherit Machine Group / None") },
                            onClick = {
                                selectedGroupId = null
                                showGroupDropdown = false
                            }
                        )
                        groups.forEach { grp ->
                            DropdownMenuItem(
                                text = { Text(grp.name) },
                                onClick = {
                                    selectedGroupId = grp.id
                                    showGroupDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("Task Title *") },
                    isError = titleError,
                    supportingText = if (titleError) { { Text("Task title is required") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Service Checklist / Notes") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Repeat / Cadence", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Cadence.entries.forEach { cad ->
                        FilterChip(
                            selected = cadence == cad,
                            onClick = { cadence = cad },
                            label = { Text(cad.label) },
                            modifier = Modifier.testTag("task_editor_cadence_${cad.name.lowercase()}")
                        )
                    }
                }
                if (cadence != Cadence.ONCE) {
                    Text(
                        text = "Repeatable: Auto-recreates with its reminder for the next ${cadence.label.lowercase()} cycle after completion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DateTimePickerField(
                    label = "Service Deadline",
                    millis = deadlineMillis,
                    onChange = { deadlineMillis = it }
                )

                EngineerPickerField(
                    label = "Person In Charge (PIC) *",
                    selectedName = pic,
                    engineers = engineers,
                    onSelect = { chosen ->
                        pic = chosen
                        picError = false
                        selectedAssistants.removeAll { it.equals(chosen, ignoreCase = true) }
                    },
                    testTag = "task_pic_picker"
                )
                if (picError) {
                    Text(
                        text = if (engineers.isEmpty()) {
                            "No engineers registered. Add an engineer in the Engineers tab first."
                        } else {
                            "Please select a Person In Charge from registered engineers."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AssistantEngineersSelector(
                    selectedAssistants = selectedAssistants,
                    excludePic = pic,
                    engineers = engineers,
                    onToggleAssistant = { name ->
                        if (selectedAssistants.any { it.equals(name, ignoreCase = true) }) {
                            selectedAssistants.removeAll { it.equals(name, ignoreCase = true) }
                        } else {
                            selectedAssistants.add(name)
                        }
                    }
                )

                OutlinedTextField(
                    value = estimatedMinutesText,
                    onValueChange = { estimatedMinutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("Estimated Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Deadline Reminder", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Notify before deadline (in hours or days) & when overdue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        modifier = Modifier.testTag("task_reminder_switch")
                    )
                }

                if (reminderEnabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !useDaysUnit,
                            onClick = { useDaysUnit = false },
                            label = { Text("Hours Before") },
                            modifier = Modifier.testTag("reminder_unit_hours")
                        )
                        FilterChip(
                            selected = useDaysUnit,
                            onClick = { useDaysUnit = true },
                            label = { Text("Days Before") },
                            modifier = Modifier.testTag("reminder_unit_days")
                        )
                    }
                    OutlinedTextField(
                        value = leadValueText,
                        onValueChange = { leadValueText = it.filter { c -> c.isDigit() } },
                        label = {
                            Text(if (useDaysUnit) "Remind Days Before Deadline" else "Remind Hours Before Deadline")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_reminder_lead_input")
                    )
                }

                if (initialTask != null && onDelete != null) {
                    TextButton(
                        onClick = { onDelete(initialTask.id) },
                        modifier = Modifier.testTag("delete_task_dialog_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Task", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val validPic = engineers.find { it.name.equals(pic.trim(), ignoreCase = true) }?.name
                    if (validPic == null) {
                        picError = true
                        return@Button
                    }
                    if (selectedMachineId == 0L) return@Button
                    val rawLead = (leadValueText.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val computedLeadHours = if (useDaysUnit) rawLead * 24 else rawLead
                    onSave(
                        initialTask?.id ?: 0L,
                        initialTask?.uuid,
                        selectedMachineId,
                        selectedGroupId,
                        title,
                        description,
                        cadence,
                        deadlineMillis,
                        estimatedMinutesText.toIntOrNull() ?: 30,
                        validPic,
                        formatEngineerList(selectedAssistants, validPic),
                        reminderEnabled,
                        computedLeadHours,
                        initialTask?.lastCompletedMillis
                    )
                },
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("Save Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CompleteTaskDialog(
    task: MaintenanceTaskEntity,
    machineName: String,
    engineers: List<EngineerEntity>,
    warehouseCatalog: List<WarehousePartEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSubmit: (
        personInCharge: String,
        assistantEngineers: List<String>,
        actualDurationMinutes: Int,
        notes: String,
        spareParts: List<SparePartDraft>,
        flagMalfunction: Boolean,
        malfunctionSeverity: Severity
    ) -> Unit
) {
    val initialPic = remember(task, engineers) {
        engineers.find { it.name.equals(task.personInCharge.trim(), ignoreCase = true) }?.name
            ?: engineers.firstOrNull()?.name
            ?: ""
    }
    var pic by remember { mutableStateOf(initialPic) }
    val selectedAssistants = remember {
        mutableStateListOf<String>().apply {
            addAll(
                parseEngineerList(task.assistantEngineers)
                    .filterNot { it.equals(initialPic, ignoreCase = true) }
            )
        }
    }
    var durationText by remember { mutableStateOf(task.estimatedMinutes.toString()) }
    var notes by remember { mutableStateOf("") }
    var flagMalfunction by remember { mutableStateOf(false) }
    var severity by remember { mutableStateOf(Severity.MEDIUM) }
    var picError by remember { mutableStateOf(false) }
    val spareParts = remember { mutableStateListOf<SparePartDraft>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete Task & Generate Report") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${task.title} ($machineName)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                EngineerPickerField(
                    label = "Serviced By (PIC) *",
                    selectedName = pic,
                    engineers = engineers,
                    onSelect = { chosen ->
                        pic = chosen
                        picError = false
                        selectedAssistants.removeAll { it.equals(chosen, ignoreCase = true) }
                    },
                    testTag = "complete_task_pic_picker"
                )
                if (picError) {
                    Text(
                        text = "Please select a registered engineer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AssistantEngineersSelector(
                    selectedAssistants = selectedAssistants,
                    excludePic = pic,
                    engineers = engineers,
                    onToggleAssistant = { name ->
                        if (selectedAssistants.any { it.equals(name, ignoreCase = true) }) {
                            selectedAssistants.removeAll { it.equals(name, ignoreCase = true) }
                        } else {
                            selectedAssistants.add(name)
                        }
                    }
                )

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                    label = { Text("Actual Time (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complete_task_duration_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Service Findings / Work Done") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = flagMalfunction,
                        onCheckedChange = { flagMalfunction = it }
                    )
                    Text("Malfunction / Breakdown discovered", style = MaterialTheme.typography.bodyMedium)
                }

                if (flagMalfunction) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Severity.entries.forEach { sev ->
                            FilterChip(
                                selected = severity == sev,
                                onClick = { severity = sev },
                                label = { Text(sev.label) }
                            )
                        }
                    }
                }

                SparePartsEditorSection(
                    spareParts = spareParts,
                    warehouseCatalog = warehouseCatalog
                )

                if (task.cadence != Cadence.ONCE) {
                    Text(
                        text = "Note: Because cadence is ${task.cadence.label}, the next cycle task & reminder will be created automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validPic = engineers.find { it.name.equals(pic.trim(), ignoreCase = true) }?.name
                    if (validPic == null) {
                        picError = true
                        return@Button
                    }
                    onSubmit(
                        validPic,
                        selectedAssistants.filterNot { it.equals(validPic, ignoreCase = true) },
                        durationText.toIntOrNull() ?: task.estimatedMinutes,
                        notes,
                        spareParts.toList(),
                        flagMalfunction,
                        severity
                    )
                },
                modifier = Modifier.testTag("confirm_complete_task_button")
            ) {
                Text("Complete & Save Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RejectTaskDialog(
    task: MaintenanceTaskEntity,
    machineName: String,
    engineers: List<EngineerEntity>,
    onDismiss: () -> Unit,
    onSubmit: (
        personInCharge: String,
        assistantEngineers: List<String>,
        rejectionReason: String,
        rescheduleNextCycle: Boolean
    ) -> Unit
) {
    val initialPic = remember(task, engineers) {
        engineers.find { it.name.equals(task.personInCharge.trim(), ignoreCase = true) }?.name
            ?: engineers.firstOrNull()?.name
            ?: ""
    }
    var pic by remember { mutableStateOf(initialPic) }
    val selectedAssistants = remember {
        mutableStateListOf<String>().apply {
            addAll(
                parseEngineerList(task.assistantEngineers)
                    .filterNot { it.equals(initialPic, ignoreCase = true) }
            )
        }
    }
    var rejectionReason by remember { mutableStateOf("") }
    var rescheduleNextCycle by remember { mutableStateOf(false) }
    var reasonError by remember { mutableStateOf(false) }
    var picError by remember { mutableStateOf(false) }

    val quickReasons = listOf(
        "Machine In Production",
        "Awaiting Spare Parts",
        "Unsafe / Locked Out",
        "Duplicate Request"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Service Task") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${task.title} ($machineName)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Rejecting moves this task to Machine History & Reports as Rejected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                EngineerPickerField(
                    label = "Reviewed / Rejected By (PIC) *",
                    selectedName = pic,
                    engineers = engineers,
                    onSelect = { chosen ->
                        pic = chosen
                        picError = false
                        selectedAssistants.removeAll { it.equals(chosen, ignoreCase = true) }
                    },
                    testTag = "reject_task_pic_picker"
                )
                if (picError) {
                    Text(
                        text = "Please select a registered engineer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AssistantEngineersSelector(
                    selectedAssistants = selectedAssistants,
                    excludePic = pic,
                    engineers = engineers,
                    onToggleAssistant = { name ->
                        if (selectedAssistants.any { it.equals(name, ignoreCase = true) }) {
                            selectedAssistants.removeAll { it.equals(name, ignoreCase = true) }
                        } else {
                            selectedAssistants.add(name)
                        }
                    }
                )

                Text("Quick Reason", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    quickReasons.forEach { preset ->
                        FilterChip(
                            selected = rejectionReason == preset,
                            onClick = {
                                rejectionReason = preset
                                reasonError = false
                            },
                            label = { Text(preset) }
                        )
                    }
                }

                OutlinedTextField(
                    value = rejectionReason,
                    onValueChange = {
                        rejectionReason = it
                        reasonError = false
                    },
                    label = { Text("Rejection Reason *") },
                    isError = reasonError,
                    supportingText = if (reasonError) { { Text("Enter why this service cannot be performed") } } else null,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reject_task_reason_input")
                )

                if (task.cadence != Cadence.ONCE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Recreate next ${task.cadence.label.lowercase()} cycle", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Skip current window and schedule the next occurrence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rescheduleNextCycle,
                            onCheckedChange = { rescheduleNextCycle = it },
                            modifier = Modifier.testTag("reject_reschedule_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validPic = engineers.find { it.name.equals(pic.trim(), ignoreCase = true) }?.name
                    if (validPic == null) {
                        picError = true
                        return@Button
                    }
                    if (rejectionReason.isBlank()) {
                        reasonError = true
                        return@Button
                    }
                    onSubmit(
                        validPic,
                        selectedAssistants.filterNot { it.equals(validPic, ignoreCase = true) },
                        rejectionReason.trim(),
                        rescheduleNextCycle
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_reject_task_button")
            ) {
                Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reject & Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SparePartsEditorSection(
    spareParts: MutableList<SparePartDraft>,
    warehouseCatalog: List<WarehousePartEntity> = emptyList()
) {
    var usedWarehousePart by remember { mutableStateOf(spareParts.isNotEmpty()) }
    var showWarehousePicker by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var partCode by remember { mutableStateOf("") }
    var partName by remember { mutableStateOf("") }
    var partDescription by remember { mutableStateOf("") }
    var fromWarehouse by remember { mutableStateOf(true) }
    var qtyText by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("pcs") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Warehouse Spare Parts (${spareParts.size})",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Search & pick spare part ID/description if used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = usedWarehousePart,
                    onCheckedChange = { usedWarehousePart = it },
                    modifier = Modifier.testTag("warehouse_spare_part_switch")
                )
            }

            spareParts.forEachIndexed { idx, part ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val titleLine = buildString {
                                if (part.partCode.isNotBlank()) append("[${part.partCode}] ")
                                append(part.partName)
                            }
                            Text(
                                text = titleLine,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (part.partDescription.isNotBlank()) {
                                Text(
                                    text = part.partDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (part.quantity > 1) {
                                    spareParts[idx] = part.copy(quantity = part.quantity - 1)
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "${part.quantity} ${part.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                spareParts[idx] = part.copy(quantity = part.quantity + 1)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase quantity", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { spareParts.removeAt(idx) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove part", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (usedWarehousePart) {
                Button(
                    onClick = { showWarehousePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("open_warehouse_picker_button")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select from Warehouse Catalog (${warehouseCatalog.size})")
                }

                TextButton(
                    onClick = { showManualEntry = !showManualEntry },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_manual_spare_part_button")
                ) {
                    Text(if (showManualEntry) "Hide Manual Entry" else "Or enter custom part manually")
                }

                if (showManualEntry) {
                    OutlinedTextField(
                        value = partCode,
                        onValueChange = { partCode = it },
                        label = { Text("Spare Part ID / Number *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spare_part_id_input")
                    )

                    OutlinedTextField(
                        value = partName,
                        onValueChange = { partName = it },
                        label = { Text("Spare Part Name (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spare_part_name_input")
                    )

                    OutlinedTextField(
                        value = partDescription,
                        onValueChange = { partDescription = it },
                        label = { Text("Spare Part Description") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spare_part_description_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.6f)
                        )
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("Unit") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = fromWarehouse,
                            onCheckedChange = { fromWarehouse = it }
                        )
                        Text("Issued from Warehouse inventory", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedButton(
                        onClick = {
                            if (partName.isNotBlank() || partCode.isNotBlank()) {
                                spareParts.add(
                                    SparePartDraft(
                                        partName = partName.trim().ifEmpty { partCode.trim() },
                                        partCode = partCode.trim(),
                                        partDescription = partDescription.trim(),
                                        fromWarehouse = fromWarehouse,
                                        quantity = qtyText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                        unit = unit.trim().ifEmpty { "pcs" }
                                    )
                                )
                                partCode = ""
                                partName = ""
                                partDescription = ""
                                qtyText = "1"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("add_spare_part_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Custom Spare Part")
                    }
                }
            }
        }
    }

    if (showWarehousePicker) {
        SearchableWarehousePartPickerDialog(
            catalog = warehouseCatalog,
            alreadySelected = spareParts,
            onDismiss = { showWarehousePicker = false },
            onSelectPart = { selected ->
                val existingIdx = spareParts.indexOfFirst {
                    it.fromWarehouse && it.partCode.equals(selected.partCode, ignoreCase = true)
                }
                if (existingIdx >= 0) {
                    val cur = spareParts[existingIdx]
                    spareParts[existingIdx] = cur.copy(quantity = cur.quantity + 1)
                } else {
                    spareParts.add(
                        SparePartDraft(
                            partName = selected.partName.ifBlank { selected.partCode },
                            partCode = selected.partCode,
                            partDescription = selected.partDescription,
                            fromWarehouse = true,
                            quantity = 1,
                            unit = selected.defaultUnit.ifBlank { "pcs" }
                        )
                    )
                }
                showWarehousePicker = false
            }
        )
    }
}

@Composable
fun SearchableWarehousePartPickerDialog(
    catalog: List<WarehousePartEntity>,
    alreadySelected: List<SparePartDraft>,
    onDismiss: () -> Unit,
    onSelectPart: (WarehousePartEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(catalog, query) {
        filterWarehouseParts(catalog, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Warehouse Spare Part") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by ID/code or description...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warehouse_picker_search_input")
                )

                Text(
                    text = "Showing ${filtered.size} of ${catalog.size} warehouse parts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (filtered.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (catalog.isEmpty()) {
                                "Your Warehouse catalog is empty. Add parts in the Parts tab or import via Excel."
                            } else {
                                "No spare parts matched \"$query\". Try another part code or keyword."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            val existingQty = alreadySelected.firstOrNull {
                                it.fromWarehouse && it.partCode.equals(item.partCode, ignoreCase = true)
                            }?.quantity ?: 0

                            Surface(
                                onClick = { onSelectPart(item) },
                                color = if (existingQty > 0) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (existingQty > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("picker_warehouse_part_${item.id}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
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
                                                text = item.partCode,
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            StatusPill(
                                                text = item.defaultUnit,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (existingQty > 0) {
                                                StatusPill(
                                                    text = "Selected ×$existingQty",
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        if (item.partName.isNotBlank()) {
                                            Text(
                                                text = item.partName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (item.partDescription.isNotBlank()) {
                                            Text(
                                                text = item.partDescription,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (existingQty > 0) Icons.Filled.CheckCircle else Icons.Filled.Add,
                                        contentDescription = "Select ${item.partCode}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun WarehousePartEditorDialog(
    initialPart: WarehousePartEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        uuid: String?,
        partCode: String,
        partName: String,
        partDescription: String,
        defaultUnit: String
    ) -> Unit
) {
    var partCode by remember { mutableStateOf(initialPart?.partCode ?: "") }
    var partName by remember { mutableStateOf(initialPart?.partName ?: "") }
    var partDescription by remember { mutableStateOf(initialPart?.partDescription ?: "") }
    var defaultUnit by remember { mutableStateOf(initialPart?.defaultUnit ?: "pcs") }
    var codeError by remember { mutableStateOf(false) }
    var descError by remember { mutableStateOf(false) }

    val commonUnits = remember { listOf("pcs", "kit", "liters", "meters", "tubes", "set") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialPart == null) "Add Warehouse Spare Part" else "Edit Warehouse Spare Part")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = partCode,
                    onValueChange = {
                        partCode = it
                        codeError = false
                    },
                    label = { Text("Spare Part ID / Code *") },
                    isError = codeError,
                    supportingText = if (codeError) { { Text("Part code is required") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warehouse_part_code_input")
                )

                OutlinedTextField(
                    value = partName,
                    onValueChange = { partName = it },
                    label = { Text("Part Name (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warehouse_part_name_input")
                )

                OutlinedTextField(
                    value = partDescription,
                    onValueChange = {
                        partDescription = it
                        descError = false
                    },
                    label = { Text("Spare Part Description *") },
                    isError = descError,
                    supportingText = if (descError) { { Text("Description is required") } } else null,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warehouse_part_desc_input")
                )

                OutlinedTextField(
                    value = defaultUnit,
                    onValueChange = { defaultUnit = it },
                    label = { Text("Default Unit") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warehouse_part_unit_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    commonUnits.forEach { u ->
                        FilterChip(
                            selected = defaultUnit.equals(u, ignoreCase = true),
                            onClick = { defaultUnit = u },
                            label = { Text(u) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val codeMissing = partCode.isBlank()
                    val descMissing = partDescription.isBlank() && partName.isBlank()
                    if (codeMissing || descMissing) {
                        codeError = codeMissing
                        descError = descMissing
                        return@Button
                    }
                    onSave(
                        initialPart?.id ?: 0L,
                        initialPart?.uuid,
                        partCode.trim().uppercase(),
                        partName.trim(),
                        partDescription.trim().ifEmpty { partName.trim() },
                        defaultUnit.trim().ifEmpty { "pcs" }
                    )
                },
                modifier = Modifier.testTag("save_warehouse_part_button")
            ) {
                Text("Save Part")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

