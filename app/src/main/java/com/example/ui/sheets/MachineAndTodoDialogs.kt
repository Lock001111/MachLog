package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CustomGroupEntity
import com.example.data.GroupScope
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.TodoReminderEntity
import com.example.ui.components.DateTimePickerField
import com.example.ui.theme.groupColorForKey
import java.util.concurrent.TimeUnit

@Composable
fun MachineEditorDialog(
    initialMachine: MachineEntity?,
    groups: List<CustomGroupEntity>,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        uuid: String?,
        groupId: Long?,
        assetCode: String,
        name: String,
        locationZone: String,
        modelInfo: String,
        status: MachineStatus,
        notes: String
    ) -> Unit
) {
    var groupId by remember { mutableStateOf(initialMachine?.groupId) }
    var assetCode by remember { mutableStateOf(initialMachine?.assetCode ?: "") }
    var name by remember { mutableStateOf(initialMachine?.name ?: "") }
    var locationZone by remember { mutableStateOf(initialMachine?.locationZone ?: "") }
    var modelInfo by remember { mutableStateOf(initialMachine?.modelInfo ?: "") }
    var status by remember { mutableStateOf(initialMachine?.status ?: MachineStatus.OPERATIONAL) }
    var notes by remember { mutableStateOf(initialMachine?.notes ?: "") }
    var showError by remember { mutableStateOf(false) }
    var showGroupDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialMachine == null) "Add Factory Machine" else "Edit Machine") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = assetCode,
                    onValueChange = {
                        assetCode = it
                        showError = false
                    },
                    label = { Text("Asset Code * (e.g. MCH-601)") },
                    isError = showError && assetCode.isBlank(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("machine_asset_code_input")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text("Machine Name *") },
                    isError = showError && name.isBlank(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("machine_name_input")
                )

                OutlinedTextField(
                    value = locationZone,
                    onValueChange = { locationZone = it },
                    label = { Text("Line / Bay Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelInfo,
                    onValueChange = { modelInfo = it },
                    label = { Text("Model & Serial No.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    val grp = groupId?.let { gid -> groups.find { it.id == gid } }
                    OutlinedButton(
                        onClick = { showGroupDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Custom Group: ${grp?.name ?: "Ungrouped"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showGroupDropdown, onDismissRequest = { showGroupDropdown = false }) {
                        DropdownMenuItem(
                            text = { Text("Ungrouped") },
                            onClick = {
                                groupId = null
                                showGroupDropdown = false
                            }
                        )
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g.name) },
                                onClick = {
                                    groupId = g.id
                                    showGroupDropdown = false
                                }
                            )
                        }
                    }
                }

                Text("Operational Status", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    MachineStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Engineering Notes / Specs") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (assetCode.isBlank() || name.isBlank()) {
                        showError = true
                        return@Button
                    }
                    onSave(
                        initialMachine?.id ?: 0L,
                        initialMachine?.uuid,
                        groupId,
                        assetCode,
                        name,
                        locationZone.ifBlank { "Main Floor" },
                        modelInfo.ifBlank { "Standard Unit" },
                        status,
                        notes
                    )
                },
                modifier = Modifier.testTag("save_machine_button")
            ) {
                Text("Save Machine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ManageGroupsDialog(
    groups: List<CustomGroupEntity>,
    onDismiss: () -> Unit,
    onSaveGroup: (id: Long, uuid: String?, name: String, scope: GroupScope, colorKey: String) -> Unit,
    onDeleteGroup: (Long) -> Unit
) {
    val colorKeys = listOf("COBALT", "TEAL", "AMBER", "EMERALD", "CRIMSON", "VIOLET")
    var editingGroup by remember { mutableStateOf<CustomGroupEntity?>(null) }
    var groupName by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(GroupScope.ALL) }
    var colorKey by remember { mutableStateOf("COBALT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Custom Groups") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (groups.isEmpty()) {
                    Text(
                        "No custom groups yet. Create groups like Line A, CNC Bay, or Safety Critical.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groups.forEach { g ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(groupColorForKey(g.colorKey))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(g.name, style = MaterialTheme.typography.titleSmall)
                                    Text(g.scope.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    editingGroup = g
                                    groupName = g.name
                                    scope = g.scope
                                    colorKey = g.colorKey
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit group", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteGroup(g.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete group", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Text(
                    text = if (editingGroup == null) "Create New Group" else "Edit Group: ${editingGroup?.name}",
                    style = MaterialTheme.typography.labelLarge
                )
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name (e.g. Packaging Line B)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_group_name_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    GroupScope.entries.forEach { sc ->
                        FilterChip(
                            selected = scope == sc,
                            onClick = { scope = sc },
                            label = { Text(sc.label) }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorKeys.forEach { key ->
                        val c = groupColorForKey(key)
                        Surface(
                            color = c,
                            shape = CircleShape,
                            border = if (colorKey == key) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable { colorKey = key }
                        ) {}
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editingGroup != null) {
                        OutlinedButton(onClick = {
                            editingGroup = null
                            groupName = ""
                            scope = GroupScope.ALL
                            colorKey = "COBALT"
                        }) {
                            Text("Cancel Edit")
                        }
                    }
                    Button(
                        onClick = {
                            if (groupName.isNotBlank()) {
                                onSaveGroup(
                                    editingGroup?.id ?: 0L,
                                    editingGroup?.uuid,
                                    groupName,
                                    scope,
                                    colorKey
                                )
                                editingGroup = null
                                groupName = ""
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("save_custom_group_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (editingGroup == null) "Add Group" else "Update Group")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun TodoEditorDialog(
    initialTodo: TodoReminderEntity?,
    defaultEngineerName: String,
    machines: List<MachineEntity>,
    groups: List<CustomGroupEntity>,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        uuid: String?,
        title: String,
        notes: String,
        machineId: Long?,
        groupId: Long?,
        personInCharge: String,
        repeatRule: RepeatRule,
        dueAtMillis: Long,
        reminderEnabled: Boolean,
        priority: ReminderPriority,
        isCompleted: Boolean,
        completedAtMillis: Long?
    ) -> Unit
) {
    var title by remember { mutableStateOf(initialTodo?.title ?: "") }
    var notes by remember { mutableStateOf(initialTodo?.notes ?: "") }
    var machineId by remember { mutableStateOf(initialTodo?.machineId) }
    var groupId by remember { mutableStateOf(initialTodo?.groupId) }
    var pic by remember { mutableStateOf(initialTodo?.personInCharge ?: defaultEngineerName) }
    var repeatRule by remember { mutableStateOf(initialTodo?.repeatRule ?: RepeatRule.ONCE) }
    var dueAtMillis by remember {
        mutableStateOf(initialTodo?.dueAtMillis ?: (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)))
    }
    var reminderEnabled by remember { mutableStateOf(initialTodo?.reminderEnabled ?: true) }
    var priority by remember { mutableStateOf(initialTodo?.priority ?: ReminderPriority.NORMAL) }
    var titleError by remember { mutableStateOf(false) }

    var showMachineMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTodo == null) "New To-Do Reminder" else "Edit To-Do Reminder") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("Reminder Title *") },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("todo_title_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Details / Action Notes") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Repeat Trigger", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    RepeatRule.entries.forEach { rule ->
                        FilterChip(
                            selected = repeatRule == rule,
                            onClick = { repeatRule = rule },
                            label = { Text(rule.label) },
                            modifier = Modifier.testTag("todo_editor_repeat_${rule.name.lowercase()}")
                        )
                    }
                }

                DateTimePickerField(
                    label = "Trigger Date & Time",
                    millis = dueAtMillis,
                    onChange = { dueAtMillis = it }
                )

                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = pic,
                    onValueChange = { pic = it },
                    label = { Text("Person In Charge") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    val m = machineId?.let { mid -> machines.find { it.id == mid } }
                    OutlinedButton(
                        onClick = { showMachineMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Machine: ${m?.let { "${it.assetCode} ${it.name}" } ?: "None (General)"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMachineMenu, onDismissRequest = { showMachineMenu = false }) {
                        DropdownMenuItem(text = { Text("None (General)") }, onClick = {
                            machineId = null
                            showMachineMenu = false
                        })
                        machines.forEach { machine ->
                            DropdownMenuItem(
                                text = { Text("${machine.assetCode} · ${machine.name}") },
                                onClick = {
                                    machineId = machine.id
                                    showMachineMenu = false
                                }
                            )
                        }
                    }
                }

                Box {
                    val g = groupId?.let { gid -> groups.find { it.id == gid } }
                    OutlinedButton(
                        onClick = { showGroupMenu = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Group: ${g?.name ?: "None"}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = {
                            groupId = null
                            showGroupMenu = false
                        })
                        groups.forEach { grp ->
                            DropdownMenuItem(
                                text = { Text(grp.name) },
                                onClick = {
                                    groupId = grp.id
                                    showGroupMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Android Notification", modifier = Modifier.weight(1f))
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
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
                    onSave(
                        initialTodo?.id ?: 0L,
                        initialTodo?.uuid,
                        title,
                        notes,
                        machineId,
                        groupId,
                        pic,
                        repeatRule,
                        dueAtMillis,
                        reminderEnabled,
                        priority,
                        initialTodo?.isCompleted ?: false,
                        initialTodo?.completedAtMillis
                    )
                },
                modifier = Modifier.testTag("save_todo_button")
            ) {
                Text("Save Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
