package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Engineering
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalMachinaColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.EngineerEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.TodoReminderEntity
import com.example.data.WarehousePartEntity
import com.example.engine.ExportFilterParams
import com.example.engine.Formatters
import com.example.engine.NotificationChannels
import com.example.ui.screens.EngineerEditorDialog
import com.example.ui.screens.EngineersScreen
import com.example.ui.screens.MachinesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.WarehousePartsScreen
import com.example.ui.sheets.AppUpdateAvailableDialog
import com.example.ui.sheets.CompleteTaskDialog
import com.example.ui.sheets.ExcelImportPreviewDialog
import com.example.ui.sheets.ExportReportDialog
import com.example.ui.sheets.ImportBackupPreviewDialog
import com.example.ui.sheets.MachineEditorDialog
import com.example.ui.sheets.ManageGroupsDialog
import com.example.ui.sheets.PcWebPortalDialog
import com.example.ui.sheets.RejectTaskDialog
import com.example.ui.sheets.TaskEditorDialog
import com.example.ui.sheets.TodoEditorDialog
import com.example.ui.sheets.WarehousePartEditorDialog
import kotlinx.coroutines.launch

const val ROUTE_TASKS = "tasks"
const val ROUTE_MACHINES = "machines"
const val ROUTE_WAREHOUSE = "warehouse"
const val ROUTE_ENGINEERS = "engineers"
const val ROUTE_REPORTS = "reports"
const val ROUTE_SETTINGS = "settings"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopDestinations = listOf(
    NavDestination(ROUTE_TASKS, R.string.nav_tasks, Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    NavDestination(ROUTE_MACHINES, R.string.nav_machines, Icons.Filled.PrecisionManufacturing, Icons.Outlined.PrecisionManufacturing),
    NavDestination(ROUTE_WAREHOUSE, R.string.nav_warehouse, Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    NavDestination(ROUTE_ENGINEERS, R.string.nav_engineers, Icons.Filled.Engineering, Icons.Outlined.Engineering),
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

    var tasksSubTab by rememberSaveable { mutableIntStateOf(0) }

    // Modal / Dialog State
    var showTaskEditor by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<MaintenanceTaskEntity?>(null) }
    var taskDefaultMachineId by remember { mutableStateOf<Long?>(null) }

    var completingTask by remember { mutableStateOf<MaintenanceTaskEntity?>(null) }
    var rejectingTask by remember { mutableStateOf<MaintenanceTaskEntity?>(null) }

    var showMachineEditor by remember { mutableStateOf(false) }
    var editingMachine by remember { mutableStateOf<MachineEntity?>(null) }

    var showManageGroups by remember { mutableStateOf(false) }

    var showTodoEditor by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoReminderEntity?>(null) }

    var showWarehousePartEditor by remember { mutableStateOf(false) }
    var editingWarehousePart by remember { mutableStateOf<WarehousePartEntity?>(null) }

    var showEngineerEditor by remember { mutableStateOf(false) }
    var editingEngineer by remember { mutableStateOf<EngineerEntity?>(null) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportPreferPdf by remember { mutableStateOf(false) }
    var pendingExportParams by remember { mutableStateOf(ExportFilterParams()) }

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
    val approachingOrOverdueTasksCount = remember(state.tasks, now) {
        countApproachingOrOverdueTasks(state.tasks, now)
    }

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
                    navigationIcon = {
                        if (currentRoute == ROUTE_SETTINGS) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.testTag("topbar_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            text = if (currentRoute == ROUTE_SETTINGS) "Settings" else stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        val sem = LocalMachinaColors.current
                        val pcOnline = state.webPortalStatus.isRunning
                        Surface(
                            onClick = { viewModel.setPcWebEnabled(!pcOnline) },
                            shape = RoundedCornerShape(999.dp),
                            color = if (pcOnline) sem.operationalContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (pcOnline) sem.operational else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (pcOnline) sem.operational.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("topbar_pc_web_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Computer,
                                    contentDescription = if (pcOnline) "Disable PC Web Access" else "Enable PC Web Access",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (pcOnline) "PC ON" else "PC OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                editingTask = null
                                taskDefaultMachineId = state.selectedMachineId ?: state.machines.firstOrNull()?.id
                                showTaskEditor = true
                            },
                            modifier = Modifier.testTag("topbar_add_task_button")
                        ) {
                            Icon(Icons.Filled.AddTask, contentDescription = stringResource(R.string.action_add_task))
                        }
                        IconButton(
                            onClick = {
                                if (currentRoute == ROUTE_SETTINGS) {
                                    navController.popBackStack()
                                } else {
                                    viewModel.refreshLocalBackups()
                                    navController.navigate(ROUTE_SETTINGS) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("topbar_sync_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.action_sync_backup),
                                tint = if (currentRoute == ROUTE_SETTINGS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail && currentRoute != ROUTE_SETTINGS) {
                    NavigationBar {
                        TopDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                ROUTE_TASKS -> approachingOrOverdueTasksCount
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
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${dest.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (currentRoute) {
                    ROUTE_TASKS -> {
                        if (tasksSubTab == 1) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    editingTodo = null
                                    showTodoEditor = true
                                },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_add_todo)) },
                                modifier = Modifier.testTag("fab_add_todo")
                            )
                        } else if (tasksSubTab == 0) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    editingTask = null
                                    taskDefaultMachineId = state.machines.firstOrNull()?.id
                                    showTaskEditor = true
                                },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                text = { Text(stringResource(R.string.action_add_task)) },
                                modifier = Modifier.testTag("fab_add_task")
                            )
                        }
                    }
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
                    ROUTE_ENGINEERS -> ExtendedFloatingActionButton(
                        onClick = {
                            editingEngineer = null
                            showEngineerEditor = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_engineer)) },
                        modifier = Modifier.testTag("fab_add_engineer")
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
                                ROUTE_TASKS -> approachingOrOverdueTasksCount
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
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                },
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
                                selectedSubTab = tasksSubTab,
                                onSelectSubTab = { tasksSubTab = it },
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
                                onRejectTask = { task -> rejectingTask = task },
                                onManageGroups = { showManageGroups = true },
                                onImportExcel = {
                                    openExcelLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "*/*"
                                        )
                                    )
                                },
                                onUpdateTodoFilter = viewModel::updateTodoFilter,
                                onSetListPageSize = viewModel::setListPageSize,
                                onToggleTodoComplete = viewModel::toggleTodoComplete,
                                onAddTodo = {
                                    editingTodo = null
                                    showTodoEditor = true
                                },
                                onEditTodo = { todo ->
                                    editingTodo = todo
                                    showTodoEditor = true
                                },
                                onDeleteTodo = { todo -> viewModel.deleteTodo(todo.id) },
                                onUpdateHistoryFilter = viewModel::updateHistoryFilter,
                                onDeleteLog = viewModel::deleteServiceLog
                            )
                        }
                        composable(ROUTE_MACHINES) {
                            MachinesScreen(
                                state = state,
                                isExpanded = isExpandedWidth,
                                onUpdateFilter = viewModel::updateMachineFilter,
                                onSetListPageSize = viewModel::setListPageSize,
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
                                onRejectTask = { task -> rejectingTask = task },
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
                                onSetListPageSize = viewModel::setListPageSize,
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
                        composable(ROUTE_ENGINEERS) {
                            EngineersScreen(
                                state = state,
                                onSetListPageSize = viewModel::setListPageSize,
                                onAddEngineer = {
                                    editingEngineer = null
                                    showEngineerEditor = true
                                },
                                onEditEngineer = { eng ->
                                    editingEngineer = eng
                                    showEngineerEditor = true
                                },
                                onDeleteEngineer = { eng -> viewModel.deleteEngineer(eng.id) },
                                onCompleteTask = { task -> completingTask = task },
                                onRejectTask = { task -> rejectingTask = task },
                                onEditTask = { task ->
                                    editingTask = task
                                    taskDefaultMachineId = task.machineId
                                    showTaskEditor = true
                                },
                                onViewEngineerTasksInTab = { engName ->
                                    viewModel.focusEngineerTasks(engName)
                                    tasksSubTab = 0
                                    navigateToTab(ROUTE_TASKS)
                                },
                                onViewEngineerHistoryInTab = { engName ->
                                    viewModel.focusEngineerHistory(engName)
                                    tasksSubTab = 2
                                    navigateToTab(ROUTE_TASKS)
                                }
                            )
                        }
                        composable(ROUTE_REPORTS) {
                            ReportsScreen(
                                state = state,
                                isExpanded = isExpandedWidth,
                                onSetListPageSize = viewModel::setListPageSize,
                                onUpdateReportFilter = viewModel::updateReportFilter,
                                onOpenExportSheet = { preferPdf ->
                                    exportPreferPdf = preferPdf
                                    pendingExportParams = state.reportFilter
                                    showExportDialog = true
                                },
                                onDeleteLog = viewModel::deleteServiceLog
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                state = state,
                                onNavigateBack = { navController.popBackStack() },
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
                                    viewModel.refreshPcWebAddresses()
                                    showPcWebDialog = true
                                },
                                onSetAutoCheckUpdates = viewModel::setAutoCheckUpdates,
                                onCheckForUpdates = { viewModel.checkForUpdates(manual = true) }
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
            engineers = state.engineers,
            onDismiss = { showTaskEditor = false },
            onDelete = { taskId ->
                viewModel.deleteTask(taskId)
                showTaskEditor = false
            },
            onSave = { id, uuid, machineId, groupId, title, desc, cadence, priority, deadline, estMin, pic, assistants, remOn, leadHrs, lastComp, machineStatus ->
                viewModel.saveTask(id, uuid, machineId, groupId, title, desc, cadence, priority, deadline, estMin, pic, assistants, remOn, leadHrs, lastComp, machineStatus)
                showTaskEditor = false
            }
        )
    }

    completingTask?.let { task ->
        val machineName = state.machines.find { it.id == task.machineId }?.let { "${it.assetCode} ${it.name}" } ?: "Machine"
        CompleteTaskDialog(
            task = task,
            machineName = machineName,
            engineers = state.engineers,
            warehouseCatalog = state.warehouseParts,
            onDismiss = { completingTask = null },
            onSubmit = { pic, assistants, actualMin, notes, spareParts ->
                viewModel.completeTask(task.id, pic, assistants, actualMin, notes, spareParts)
                completingTask = null
            }
        )
    }

    rejectingTask?.let { task ->
        val machineName = state.machines.find { it.id == task.machineId }?.let { "${it.assetCode} ${it.name}" } ?: "Machine"
        RejectTaskDialog(
            task = task,
            machineName = machineName,
            engineers = state.engineers,
            onDismiss = { rejectingTask = null },
            onSubmit = { pic, assistants, reason, reschedule ->
                viewModel.rejectTask(task.id, pic, assistants, reason, reschedule)
                rejectingTask = null
            }
        )
    }

    if (showEngineerEditor) {
        EngineerEditorDialog(
            initialEngineer = editingEngineer,
            onDismiss = { showEngineerEditor = false },
            onSave = { id, uuid, name, code, role, shift ->
                viewModel.saveEngineer(id, uuid, name, code, role, shift)
                showEngineerEditor = false
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
            engineers = state.engineers,
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

    state.availableUpdate?.let { updateInfo ->
        AppUpdateAvailableDialog(
            info = updateInfo,
            onDismiss = viewModel::dismissUpdateDialog
        )
    }
}
