package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.TodoReminderEntity
import com.example.data.WarehousePartEntity
import com.example.engine.ExportFilterParams
import com.example.engine.Formatters
import com.example.engine.NotificationChannels
import com.example.ui.screens.MachinesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TodoScreen
import com.example.ui.screens.WarehousePartsScreen
import com.example.ui.sheets.CompleteTaskDialog
import com.example.ui.sheets.ExcelImportPreviewDialog
import com.example.ui.sheets.ExportReportDialog
import com.example.ui.sheets.ImportBackupPreviewDialog
import com.example.ui.sheets.MachineEditorDialog
import com.example.ui.sheets.MalfunctionLogDialog
import com.example.ui.sheets.ManageGroupsDialog
import com.example.ui.sheets.PcWebPortalDialog
import com.example.ui.sheets.SyncBackupSettingsDialog
import com.example.ui.sheets.TaskEditorDialog
import com.example.ui.sheets.TodoEditorDialog
import com.example.ui.sheets.WarehousePartEditorDialog
import kotlinx.coroutines.launch

const val ROUTE_TASKS = "tasks"
const val ROUTE_TODO = "todo"
const val ROUTE_MACHINES = "machines"
const val ROUTE_WAREHOUSE = "warehouse"
const val ROUTE_REPORTS = "reports"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopDestinations = listOf(
    NavDestination(ROUTE_TASKS, R.string.nav_tasks, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    NavDestination(ROUTE_TODO, R.string.nav_todo, Icons.Filled.Checklist, Icons.Outlined.Checklist),
    NavDestination(ROUTE_MACHINES, R.string.nav_machines, Icons.Filled.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing),
    NavDestination(ROUTE_WAREHOUSE, R.string.nav_warehouse, Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    NavDestination(ROUTE_REPORTS, R.string.nav_reports, Icons.Filled.Assessment, Icons.Outlined.Assessment)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinaApp(viewModel: MachinaViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: ROUTE_TASKS
    val snackbarHostState = remember { SnackbarHostState() }

    // Modal / Dialog State
    var showTaskEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<MaintenanceTaskEntity?>(null) }
    var taskDefaultMachineId by remember { mutableStateOf<Long?>(null) }

    var completingTask by remember { mutableStateOf<MaintenanceTaskEntity?>(null) }

    var showMalfunctionDialog by remember { mutableStateOf(false) }
    var malfunctionDefaultMachineId by remember { mutableStateOf<Long?>(null) }

    var showMachineEditor by remember { mutableStateOf(false) }
    var editingMachine by remember { mutableStateOf<MachineEntity?>(null) }

    var showManageGroups by remember { mutableStateOf(false) }

    var showTodoEditor by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoReminderEntity?>(null) }

    var showWarehousePartEditor by remember { mutableStateOf(false) }
    var editingWarehousePart by remember { mutableStateOf<WarehousePartEntity?>(null) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPreferPdf by remember { mutableStateOf(false) }
    var pendingExportParams by remember { mutableStateOf(ExportFilterParams()) }

    var showSyncSettings by remember { mutableStateOf(false) }
    var showPcWebDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Activity Result Launchers (Zero-permission SAF + Notification Permission)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }

    val createExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.exportReportToUri(uri, isPdf = false, params = pendingExportParams)
            showExportDialog = false
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportReportToUri(uri, isPdf = true, params = pendingExportParams)
            showExportDialog = false
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadBackupPreview(uri)
        }
    }

    val openExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadExcelImportPreview(uri)
        }
    }

    val createExcelTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.saveExcelTemplateToUri(uri)
        }
    }

    val pickAutoBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.setAutoBackupFolderUri(uri)
        }
    }

    // Request notification permission nicely once if on API 33+ and enabled
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            state.preferences.notificationsEnabled &&
            !NotificationChannels.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
                is UiEvent.ShareFile -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, event.chooserTitle))
                }
            }
        }
    }

    val now = System.currentTimeMillis()
    val overdueTasksCount = remember(state.tasks, now) { state.tasks.count { it.deadlineMillis < now } }
    val overdueTodosCount = remember(state.todos, now) { state.todos.count { !it.isCompleted && it.dueAtMillis < now } }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp
        val isExpandedWidth = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.refreshPcWebAddresses()
                                showPcWebDialog = true
                            },
                            modifier = Modifier.testTag("topbar_pc_web_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.webPortalStatus.isRunning) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Computer,
                                    contentDescription = stringResource(R.string.action_pc_web),
                                    tint = if (state.webPortalStatus.isRunning) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                malfunctionDefaultMachineId = state.selectedMachineId ?: state.machines.firstOrNull()?.id
                                showMalfunctionDialog = true
                            },
                            modifier = Modifier.testTag("topbar_log_malfunction_button")
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.action_log_malfunction))
                        }
                        IconButton(
                            onClick = {
                                viewModel.refreshLocalBackups()
                                showSyncSettings = true
                            },
                            modifier = Modifier.testTag("topbar_sync_settings_button")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = stringResource(R.string.action_sync_backup))
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                ROUTE_TASKS -> overdueTasksCount
                                ROUTE_TODO -> overdueTodosCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text("$badgeCount") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (currentRoute) {
                    ROUTE_TASKS -> ExtendedFloatingActionButton(
                        onClick = {
                            editingTask = null
                            taskDefaultMachineId = state.machines.firstOrNull()?.id
                            showTaskEditor = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_task)) },
                        modifier = Modifier.testTag("fab_add_task")
                    )
                    ROUTE_TODO -> ExtendedFloatingActionButton(
                        onClick = {
                            editingTodo = null
                            showTodoEditor = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_todo)) },
                        modifier = Modifier.testTag("fab_add_todo")
                    )
                    ROUTE_MACHINES -> ExtendedFloatingActionButton(
                        onClick = {
                            editingMachine = null
                            showMachineEditor = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_machine)) },
                        modifier = Modifier.testTag("fab_add_machine")
                    )
                    ROUTE_WAREHOUSE -> ExtendedFloatingActionButton(
                        onClick = {
                            editingWarehousePart = null
                            showWarehousePartEditor = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_warehouse_part)) },
                        modifier = Modifier.testTag("fab_add_warehouse_part")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.padding(top = 8.dp))
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                ROUTE_TASKS -> overdueTasksCount
                                ROUTE_TODO -> overdueTodosCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(dest.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text("$badgeCount") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_tab_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TASKS,
                        modifier = Modifier
                            .widthIn(max = if (isExpandedWidth) 1320.dp else 720.dp)
                            .fillMaxSize()
                    ) {
                        composable(ROUTE_TASKS) {
                            TasksScreen(
                                state = state,
                                onUpdateFilter = viewModel::updateTasksFilter,
                                onAddTask = {
                                    editingTask = null
                                    taskDefaultMachineId = state.machines.firstOrNull()?.id
                                    showTaskEditor = true
                                },
                                onEditTask = { task ->
                                    editingTask = task
                                    taskDefaultMachineId = task.machineId
                                    showTaskEditor = true
                                },
                                onCompleteTask = { task -> completingTask = task },
                                onManageGroups = { showManageGroups = true },
                                onImportExcel = {
                                    openExcelLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "*/*"
                                        )
                                    )
                                }
                            )
                        }
                        composable(ROUTE_TODO) {
                            TodoScreen(
                                state = state,
                                onUpdateFilter = viewModel::updateTodoFilter,
                                onToggleComplete = viewModel::toggleTodoComplete,
                                onAddTodo = {
                                    editingTodo = null
                                    showTodoEditor = true
                                },
                                onEditTodo = { todo ->
                                    editingTodo = todo
                                    showTodoEditor = true
                                },
                                onDeleteTodo = { todo -> viewModel.deleteTodo(todo.id) }
                            )
                        }
                        composable(ROUTE_MACHINES) {
                            MachinesScreen(
                                state = state,
                                isExpanded = isExpandedWidth,
                                onUpdateFilter = viewModel::updateMachineFilter,
                                onSelectMachine = viewModel::selectMachine,
                                onAddMachine = {
                                    editingMachine = null
                                    showMachineEditor = true
                                },
                                onEditMachine = { m ->
                                    editingMachine = m
                                    showMachineEditor = true
                                },
                                onDeleteMachine = { m -> viewModel.deleteMachine(m.id) },
                                onUpdateMachineStatus = viewModel::updateMachineStatus,
                                onAddTaskForMachine = { machineId ->
                                    editingTask = null
                                    taskDefaultMachineId = machineId
                                    showTaskEditor = true
                                },
                                onCompleteTask = { task -> completingTask = task },
                                onLogMalfunctionForMachine = { machineId ->
                                    malfunctionDefaultMachineId = machineId
                                    showMalfunctionDialog = true
                                },
                                onManageGroups = { showManageGroups = true },
                                onImportExcel = {
                                    openExcelLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "*/*"
                                        )
                                    )
                                }
                            )
                        }
                        composable(ROUTE_WAREHOUSE) {
                            WarehousePartsScreen(
                                state = state,
                                onAddPart = {
                                    editingWarehousePart = null
                                    showWarehousePartEditor = true
                                },
                                onEditPart = { part ->
                                    editingWarehousePart = part
                                    showWarehousePartEditor = true
                                },
                                onDeletePart = { part -> viewModel.deleteWarehousePart(part.id) },
                                onImportExcel = {
                                    openExcelLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "*/*"
                                        )
                                    )
                                }
                            )
                        }
                        composable(ROUTE_REPORTS) {
                            ReportsScreen(
                                state = state,
                                isExpanded = isExpandedWidth,
                                onUpdateReportFilter = viewModel::updateReportFilter,
                                onOpenExportSheet = { preferPdf ->
                                    exportPreferPdf = preferPdf
                                    pendingExportParams = state.reportFilter
                                    showExportDialog = true
                                },
                                onDeleteLog = viewModel::deleteServiceLog
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs & Sheets ---
    if (showTaskEditor) {
        TaskEditorDialog(
            initialTask = editingTask,
            defaultMachineId = taskDefaultMachineId,
            defaultEngineerName = state.preferences.defaultEngineerName,
            machines = state.machines,
            groups = state.groups,
            onDismiss = { showTaskEditor = false },
            onDelete = { taskId ->
                viewModel.deleteTask(taskId)
                showTaskEditor = false
            },
            onSave = { id, uuid, machineId, groupId, title, desc, cadence, deadline, estMin, pic, remOn, leadHrs, lastComp ->
                viewModel.saveTask(id, uuid, machineId, groupId, title, desc, cadence, deadline, estMin, pic, remOn, leadHrs, lastComp)
                showTaskEditor = false
            }
        )
    }

    completingTask?.let { task ->
        val machineName = state.machines.find { it.id == task.machineId }?.let { "${it.assetCode} ${it.name}" } ?: "Machine"
        CompleteTaskDialog(
            task = task,
            machineName = machineName,
            warehouseCatalog = state.warehouseParts,
            onDismiss = { completingTask = null },
            onSubmit = { pic, actualMin, notes, spareParts, flagMalfunction, severity ->
                viewModel.completeTask(task.id, pic, actualMin, notes, spareParts, flagMalfunction, severity)
                completingTask = null
            }
        )
    }

    if (showMalfunctionDialog) {
        MalfunctionLogDialog(
            machines = state.machines,
            defaultMachineId = malfunctionDefaultMachineId,
            defaultEngineerName = state.preferences.defaultEngineerName,
            warehouseCatalog = state.warehouseParts,
            onDismiss = { showMalfunctionDialog = false },
            onSubmit = { machineId, entryType, summary, details, severity, pic, durationMin, parts, newStatus ->
                viewModel.logServiceOrMalfunction(machineId, entryType, summary, details, severity, pic, durationMin, parts, newStatus)
                showMalfunctionDialog = false
            }
        )
    }

    if (showWarehousePartEditor) {
        WarehousePartEditorDialog(
            initialPart = editingWarehousePart,
            onDismiss = { showWarehousePartEditor = false },
            onSave = { id, uuid, partCode, partName, partDescription, defaultUnit ->
                viewModel.saveWarehousePart(id, uuid, partCode, partName, partDescription, defaultUnit)
                showWarehousePartEditor = false
            }
        )
    }

    if (showMachineEditor) {
        MachineEditorDialog(
            initialMachine = editingMachine,
            groups = state.groups,
            onDismiss = { showMachineEditor = false },
            onSave = { id, uuid, groupId, assetCode, name, zone, model, status, notes ->
                viewModel.saveMachine(id, uuid, groupId, assetCode, name, zone, model, status, notes)
                showMachineEditor = false
            }
        )
    }

    if (showManageGroups) {
        ManageGroupsDialog(
            groups = state.groups,
            onDismiss = { showManageGroups = false },
            onSaveGroup = viewModel::saveGroup,
            onDeleteGroup = viewModel::deleteGroup
        )
    }

    if (showTodoEditor) {
        TodoEditorDialog(
            initialTodo = editingTodo,
            defaultEngineerName = state.preferences.defaultEngineerName,
            machines = state.machines,
            groups = state.groups,
            onDismiss = { showTodoEditor = false },
            onSave = { id, uuid, title, notes, machineId, groupId, pic, rule, dueAt, remOn, priority, isDone, doneAt ->
                viewModel.saveTodo(id, uuid, title, notes, machineId, groupId, pic, rule, dueAt, remOn, priority, isDone, doneAt)
                showTodoEditor = false
            }
        )
    }

    if (showExportDialog) {
        ExportReportDialog(
            state = state,
            initialParams = state.reportFilter,
            preferPdf = exportPreferPdf,
            onDismiss = { showExportDialog = false },
            onUpdateParams = { updated ->
                pendingExportParams = updated
                viewModel.updateReportFilter { updated }
            },
            onSaveToDevice = { isPdf, params ->
                pendingExportParams = params
                val stamp = Formatters.formatFileStamp()
                if (isPdf) {
                    createPdfLauncher.launch("MachinaLog_Report_$stamp.pdf")
                } else {
                    createExcelLauncher.launch("MachinaLog_Report_$stamp.xlsx")
                }
            },
            onShareReport = { isPdf, params ->
                viewModel.shareExportReport(isPdf, params)
                showExportDialog = false
            }
        )
    }

    if (showSyncSettings) {
        SyncBackupSettingsDialog(
            state = state,
            onDismiss = { showSyncSettings = false },
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
            onTriggerReminderCheck = viewModel::triggerTestReminderCheck,
            onSetAutoBackupFrequency = viewModel::setAutoBackupFrequency,
            onPickAutoBackupFolder = { pickAutoBackupFolderLauncher.launch(null) },
            onRunAutoBackupNow = viewModel::triggerAutoBackupNow,
            onExportManualBackupFile = {
                createBackupLauncher.launch("machinalog_backup_${Formatters.formatFileStamp()}.json")
            },
            onShareManualBackupFile = viewModel::shareManualBackupFile,
            onImportBackupFile = {
                openBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onRestoreLocalBackup = viewModel::loadBackupPreviewFromFile,
            onShareLocalBackup = viewModel::shareExistingLocalBackup,
            onImportExcelFile = {
                openExcelLauncher.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "*/*"
                    )
                )
            },
            onSaveExcelTemplate = {
                createExcelTemplateLauncher.launch("MachinaLog_Import_Template.xlsx")
            },
            onSetEngineerName = viewModel::setDefaultEngineerName,
            onResetSampleData = viewModel::resetSampleData,
            onOpenPcWebPortal = {
                showSyncSettings = false
                viewModel.refreshPcWebAddresses()
                showPcWebDialog = true
            }
        )
    }

    if (showPcWebDialog) {
        PcWebPortalDialog(
            status = state.webPortalStatus,
            onDismiss = { showPcWebDialog = false },
            onSetEnabled = viewModel::setPcWebEnabled,
            onSetPinEnabled = viewModel::setPcWebPinEnabled,
            onRegeneratePin = viewModel::regeneratePcWebPin,
            onRefreshNetwork = viewModel::refreshPcWebAddresses,
            onCopiedUrl = { url ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Copied URL: $url")
                }
            }
        )
    }

    state.pendingImportPreview?.let { pkg ->
        ImportBackupPreviewDialog(
            pkg = pkg,
            onDismiss = viewModel::dismissImportPreview,
            onConfirm = viewModel::confirmRestoreBackup
        )
    }

    state.pendingExcelImportPreview?.let { preview ->
        ExcelImportPreviewDialog(
            preview = preview,
            onDismiss = viewModel::dismissExcelImportPreview,
            onConfirm = viewModel::confirmExcelImport
        )
    }
}
