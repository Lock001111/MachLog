package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.AutoBackupFrequency
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.GroupScope
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceRepository
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogWithParts
import com.example.data.Severity
import com.example.data.SparePartDraft
import com.example.data.TodoReminderEntity
import com.example.data.UserPreferences
import com.example.data.WarehousePartEntity
import com.example.engine.BackupSyncManager
import com.example.engine.ExcelImportEngine
import com.example.engine.ExcelImportPreview
import com.example.engine.ExportDataset
import com.example.engine.ExportFilterParams
import com.example.engine.Formatters
import com.example.engine.LocalWebServerManager
import com.example.engine.MaintenanceReminderScheduler
import com.example.engine.NotificationChannels
import com.example.engine.PortableBackupPackage
import com.example.engine.ReportExportEngine
import com.example.engine.RestoreMode
import com.example.engine.WebPortalStatus
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class TaskGroupBy(val label: String) {
    CUSTOM_GROUP("Custom Group"),
    CADENCE("Cadence"),
    MACHINE("Machine"),
    PIC("Person in Charge"),
    NONE("Flat List")
}

enum class MachineGroupBy(val label: String) {
    CUSTOM_GROUP("Custom Group"),
    STATUS("Health Status"),
    NONE("Flat List")
}

data class TasksFilterState(
    val cadence: Cadence? = null,
    val overdueOnly: Boolean = false,
    val groupId: Long? = null,
    val picFilter: String? = null,
    val searchQuery: String = "",
    val groupBy: TaskGroupBy = TaskGroupBy.CUSTOM_GROUP
)

data class TodoFilterState(
    val repeatRule: RepeatRule? = null,
    val showCompleted: Boolean = false,
    val searchQuery: String = ""
)

data class MachineFilterState(
    val status: MachineStatus? = null,
    val groupId: Long? = null,
    val groupBy: MachineGroupBy = MachineGroupBy.CUSTOM_GROUP,
    val searchQuery: String = ""
)

data class MachinaUiState(
    val groups: List<CustomGroupEntity> = emptyList(),
    val machines: List<MachineEntity> = emptyList(),
    val tasks: List<MaintenanceTaskEntity> = emptyList(),
    val todos: List<TodoReminderEntity> = emptyList(),
    val serviceLogs: List<ServiceLogWithParts> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val tasksFilter: TasksFilterState = TasksFilterState(),
    val todoFilter: TodoFilterState = TodoFilterState(),
    val machineFilter: MachineFilterState = MachineFilterState(),
    val reportFilter: ExportFilterParams = ExportFilterParams(),
    val selectedMachineId: Long? = null,
    val localBackups: List<File> = emptyList(),
    val pendingImportPreview: PortableBackupPackage? = null,
    val pendingExcelImportPreview: ExcelImportPreview? = null,
    val warehouseParts: List<WarehousePartEntity> = emptyList(),
    val webPortalStatus: WebPortalStatus = WebPortalStatus(),
    val isBusy: Boolean = false
) {
    val knownEngineers: List<String>
        get() = (tasks.map { it.personInCharge } +
            todos.map { it.personInCharge } +
            serviceLogs.map { it.log.personInCharge } +
            listOf(preferences.defaultEngineerName))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
}

sealed class UiEvent {
    data class Snackbar(val message: String) : UiEvent()
    data class ShareFile(val uri: Uri, val mimeType: String, val chooserTitle: String) : UiEvent()
}

class MachinaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.machinaDao()
    private val prefs = AppPreferences(application)
    private val repo = MaintenanceRepository(dao, prefs)

    private val _tasksFilter = MutableStateFlow(TasksFilterState())
    private val _todoFilter = MutableStateFlow(TodoFilterState())
    private val _machineFilter = MutableStateFlow(MachineFilterState())
    private val _reportFilter = MutableStateFlow(ExportFilterParams())
    private val _selectedMachineId = MutableStateFlow<Long?>(null)
    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    private val _pendingImportPreview = MutableStateFlow<PortableBackupPackage?>(null)
    private val _pendingExcelImportPreview = MutableStateFlow<ExcelImportPreview?>(null)
    private val _isBusy = MutableStateFlow(false)

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<MachinaUiState> = combine(
        repo.groupsFlow,
        repo.machinesFlow,
        repo.tasksFlow,
        repo.todosFlow,
        repo.serviceLogsFlow,
        repo.preferencesFlow,
        _tasksFilter,
        _todoFilter,
        _machineFilter,
        _reportFilter,
        _selectedMachineId,
        _localBackups,
        _pendingImportPreview,
        _pendingExcelImportPreview,
        _isBusy,
        repo.warehousePartsFlow,
        LocalWebServerManager.status
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val groups = values[0] as List<CustomGroupEntity>
        @Suppress("UNCHECKED_CAST")
        val machines = values[1] as List<MachineEntity>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[2] as List<MaintenanceTaskEntity>
        @Suppress("UNCHECKED_CAST")
        val todos = values[3] as List<TodoReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = values[4] as List<ServiceLogWithParts>
        val userPrefs = values[5] as UserPreferences
        val tFilter = values[6] as TasksFilterState
        val tdFilter = values[7] as TodoFilterState
        val mFilter = values[8] as MachineFilterState
        val rFilter = values[9] as ExportFilterParams
        val selMachine = values[10] as Long?
        @Suppress("UNCHECKED_CAST")
        val backups = values[11] as List<File>
        val importPreview = values[12] as PortableBackupPackage?
        val excelPreview = values[13] as ExcelImportPreview?
        val busy = values[14] as Boolean
        @Suppress("UNCHECKED_CAST")
        val warehouseCatalog = values[15] as List<WarehousePartEntity>
        val webStatus = values[16] as WebPortalStatus

        val resolvedSelectedId = if (selMachine != null && machines.any { it.id == selMachine }) {
            selMachine
        } else {
            machines.firstOrNull()?.id
        }

        MachinaUiState(
            groups = groups,
            machines = machines,
            tasks = tasks,
            todos = todos,
            serviceLogs = logs,
            preferences = userPrefs,
            tasksFilter = tFilter,
            todoFilter = tdFilter,
            machineFilter = mFilter,
            reportFilter = rFilter,
            selectedMachineId = resolvedSelectedId,
            localBackups = backups,
            pendingImportPreview = importPreview,
            pendingExcelImportPreview = excelPreview,
            warehouseParts = warehouseCatalog,
            webPortalStatus = webStatus,
            isBusy = busy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MachinaUiState()
    )

    init {
        NotificationChannels.ensureCreated(application)
        viewModelScope.launch {
            repo.ensureSeeded()
            val settings = prefs.getSnapshot()
            LocalWebServerManager.updatePinConfig(settings.pcWebPinEnabled, settings.pcWebPin)
            if (settings.pcWebEnabled) {
                LocalWebServerManager.start(
                    context = application,
                    dao = dao,
                    repo = repo,
                    prefs = prefs,
                    pinEnabled = settings.pcWebPinEnabled,
                    pinCode = settings.pcWebPin
                )
            }
            MaintenanceReminderScheduler.scheduleReminders(application, settings.notificationsEnabled)
            MaintenanceReminderScheduler.scheduleAutoBackup(application, settings.autoBackupFrequency)
            refreshLocalBackups()
        }
    }

    fun refreshLocalBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _localBackups.value = BackupSyncManager.getLocalAutoBackups(getApplication())
        }
    }

    // --- Filter Mutations ---
    fun updateTasksFilter(transform: (TasksFilterState) -> TasksFilterState) {
        _tasksFilter.value = transform(_tasksFilter.value)
    }

    fun updateTodoFilter(transform: (TodoFilterState) -> TodoFilterState) {
        _todoFilter.value = transform(_todoFilter.value)
    }

    fun updateMachineFilter(transform: (MachineFilterState) -> MachineFilterState) {
        _machineFilter.value = transform(_machineFilter.value)
    }

    fun updateReportFilter(transform: (ExportFilterParams) -> ExportFilterParams) {
        _reportFilter.value = transform(_reportFilter.value)
    }

    fun selectMachine(machineId: Long?) {
        _selectedMachineId.value = machineId
    }

    // --- Custom Group CRUD ---
    fun saveGroup(id: Long, uuid: String?, name: String, scope: GroupScope, colorKey: String) {
        viewModelScope.launch {
            val entity = if (id == 0L || uuid == null) {
                CustomGroupEntity(name = name.trim(), scope = scope, colorKey = colorKey)
            } else {
                CustomGroupEntity(id = id, uuid = uuid, name = name.trim(), scope = scope, colorKey = colorKey)
            }
            repo.saveGroup(entity)
            _events.emit(UiEvent.Snackbar("Group \"${name.trim()}\" saved"))
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            repo.deleteGroup(groupId)
            _events.emit(UiEvent.Snackbar("Custom group deleted"))
        }
    }

    // --- Machine CRUD ---
    fun saveMachine(
        id: Long,
        uuid: String?,
        groupId: Long?,
        assetCode: String,
        name: String,
        locationZone: String,
        modelInfo: String,
        status: MachineStatus,
        notes: String
    ) {
        viewModelScope.launch {
            val entity = if (id == 0L || uuid == null) {
                MachineEntity(
                    groupId = groupId,
                    assetCode = assetCode.trim().uppercase(),
                    name = name.trim(),
                    locationZone = locationZone.trim(),
                    modelInfo = modelInfo.trim(),
                    status = status,
                    notes = notes.trim()
                )
            } else {
                MachineEntity(
                    id = id,
                    uuid = uuid,
                    groupId = groupId,
                    assetCode = assetCode.trim().uppercase(),
                    name = name.trim(),
                    locationZone = locationZone.trim(),
                    modelInfo = modelInfo.trim(),
                    status = status,
                    notes = notes.trim()
                )
            }
            val savedId = repo.saveMachine(entity)
            _selectedMachineId.value = savedId
            _events.emit(UiEvent.Snackbar("Machine ${entity.assetCode} saved"))
        }
    }

    fun updateMachineStatus(machineId: Long, status: MachineStatus) {
        viewModelScope.launch {
            repo.updateMachineStatus(machineId, status)
            _events.emit(UiEvent.Snackbar("Machine marked ${status.label}"))
        }
    }

    fun deleteMachine(machineId: Long) {
        viewModelScope.launch {
            repo.deleteMachine(machineId)
            if (_selectedMachineId.value == machineId) {
                _selectedMachineId.value = null
            }
            _events.emit(UiEvent.Snackbar("Machine removed"))
        }
    }

    // --- Task CRUD & Completion ---
    fun saveTask(
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
        reminderEnabled: Boolean,
        reminderLeadHours: Int,
        lastCompletedMillis: Long?
    ) {
        viewModelScope.launch {
            val entity = if (id == 0L || uuid == null) {
                MaintenanceTaskEntity(
                    machineId = machineId,
                    groupId = groupId,
                    title = title.trim(),
                    description = description.trim(),
                    cadence = cadence,
                    deadlineMillis = deadlineMillis,
                    estimatedMinutes = estimatedMinutes.coerceAtLeast(1),
                    personInCharge = personInCharge.trim(),
                    reminderEnabled = reminderEnabled,
                    reminderLeadHours = reminderLeadHours
                )
            } else {
                MaintenanceTaskEntity(
                    id = id,
                    uuid = uuid,
                    machineId = machineId,
                    groupId = groupId,
                    title = title.trim(),
                    description = description.trim(),
                    cadence = cadence,
                    deadlineMillis = deadlineMillis,
                    estimatedMinutes = estimatedMinutes.coerceAtLeast(1),
                    personInCharge = personInCharge.trim(),
                    reminderEnabled = reminderEnabled,
                    reminderLeadHours = reminderLeadHours,
                    lastCompletedMillis = lastCompletedMillis
                )
            }
            repo.saveTask(entity)
            MaintenanceReminderScheduler.triggerImmediateReminderCheck(getApplication())
            _events.emit(UiEvent.Snackbar("Task \"${entity.title}\" saved"))
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repo.deleteTask(taskId)
            _events.emit(UiEvent.Snackbar("Task deleted"))
        }
    }

    fun completeTask(
        taskId: Long,
        personInCharge: String,
        actualDurationMinutes: Int,
        notes: String,
        spareParts: List<SparePartDraft>,
        flagMalfunction: Boolean,
        malfunctionSeverity: Severity
    ) {
        viewModelScope.launch {
            repo.completeTask(
                taskId = taskId,
                personInCharge = personInCharge,
                actualDurationMinutes = actualDurationMinutes,
                notes = notes,
                spareParts = spareParts,
                flagMalfunction = flagMalfunction,
                malfunctionSeverity = malfunctionSeverity
            )
            _events.emit(UiEvent.Snackbar("Service logged & next cycle scheduled"))
        }
    }

    fun logServiceOrMalfunction(
        machineId: Long,
        entryType: ServiceEntryType,
        summaryTitle: String,
        details: String,
        severity: Severity,
        personInCharge: String,
        durationMinutes: Int,
        spareParts: List<SparePartDraft>,
        newMachineStatus: MachineStatus?
    ) {
        viewModelScope.launch {
            repo.logServiceOrMalfunction(
                machineId = machineId,
                entryType = entryType,
                summaryTitle = summaryTitle,
                details = details,
                severity = severity,
                personInCharge = personInCharge,
                durationMinutes = durationMinutes,
                spareParts = spareParts,
                newMachineStatus = newMachineStatus
            )
            _events.emit(UiEvent.Snackbar("${entryType.label} recorded"))
        }
    }

    fun deleteServiceLog(logId: Long) {
        viewModelScope.launch {
            repo.deleteServiceLog(logId)
            _events.emit(UiEvent.Snackbar("History entry deleted"))
        }
    }

    // --- Warehouse Spare Parts Catalog ---
    fun saveWarehousePart(
        id: Long,
        uuid: String?,
        partCode: String,
        partName: String,
        partDescription: String,
        defaultUnit: String
    ) {
        viewModelScope.launch {
            val entity = if (id == 0L || uuid == null) {
                WarehousePartEntity(
                    partCode = partCode.trim().uppercase(),
                    partName = partName.trim(),
                    partDescription = partDescription.trim(),
                    defaultUnit = defaultUnit.trim().ifEmpty { "pcs" }
                )
            } else {
                WarehousePartEntity(
                    id = id,
                    uuid = uuid,
                    partCode = partCode.trim().uppercase(),
                    partName = partName.trim(),
                    partDescription = partDescription.trim(),
                    defaultUnit = defaultUnit.trim().ifEmpty { "pcs" }
                )
            }
            repo.saveWarehousePart(entity)
            _events.emit(UiEvent.Snackbar("Spare part ${entity.partCode} saved"))
        }
    }

    fun deleteWarehousePart(partId: Long) {
        viewModelScope.launch {
            repo.deleteWarehousePart(partId)
            _events.emit(UiEvent.Snackbar("Spare part removed from warehouse list"))
        }
    }

    // --- To-Do Reminders ---
    fun saveTodo(
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
    ) {
        viewModelScope.launch {
            val entity = if (id == 0L || uuid == null) {
                TodoReminderEntity(
                    title = title.trim(),
                    notes = notes.trim(),
                    machineId = machineId,
                    groupId = groupId,
                    personInCharge = personInCharge.trim(),
                    repeatRule = repeatRule,
                    dueAtMillis = dueAtMillis,
                    reminderEnabled = reminderEnabled,
                    priority = priority
                )
            } else {
                TodoReminderEntity(
                    id = id,
                    uuid = uuid,
                    title = title.trim(),
                    notes = notes.trim(),
                    machineId = machineId,
                    groupId = groupId,
                    personInCharge = personInCharge.trim(),
                    repeatRule = repeatRule,
                    dueAtMillis = dueAtMillis,
                    reminderEnabled = reminderEnabled,
                    isCompleted = isCompleted,
                    priority = priority,
                    completedAtMillis = completedAtMillis
                )
            }
            repo.saveTodo(entity)
            MaintenanceReminderScheduler.triggerImmediateReminderCheck(getApplication())
            _events.emit(UiEvent.Snackbar("Reminder saved (${repeatRule.label})"))
        }
    }

    fun toggleTodoComplete(todo: TodoReminderEntity) {
        viewModelScope.launch {
            repo.toggleTodoComplete(todo.id)
            if (todo.repeatRule == RepeatRule.ONCE) {
                _events.emit(UiEvent.Snackbar(if (!todo.isCompleted) "Reminder completed" else "Reminder reopened"))
            } else {
                _events.emit(UiEvent.Snackbar("Completed! Next ${todo.repeatRule.label.lowercase()} reminder scheduled"))
            }
        }
    }

    fun deleteTodo(todoId: Long) {
        viewModelScope.launch {
            repo.deleteTodo(todoId)
            _events.emit(UiEvent.Snackbar("Reminder deleted"))
        }
    }

    // --- Report Export (Excel .xlsx & PDF .pdf) ---
    fun buildFilteredDataset(params: ExportFilterParams): ExportDataset {
        val state = uiState.value
        return ReportExportEngine.filterDataset(
            params = params,
            machines = state.machines,
            groups = state.groups,
            allTasks = state.tasks,
            allLogs = state.serviceLogs
        )
    }

    fun exportReportToUri(uri: Uri, isPdf: Boolean, params: ExportFilterParams) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dataset = buildFilteredDataset(params)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        if (isPdf) {
                            ReportExportEngine.writePdf(out, params, dataset)
                        } else {
                            ReportExportEngine.writeXlsx(out, params, dataset)
                        }
                    } ?: error("Unable to open output stream")
                }
            }
            _isBusy.value = false
            result.onSuccess {
                val fmt = if (isPdf) "PDF" else "Excel (.xlsx)"
                _events.emit(UiEvent.Snackbar("$fmt report saved"))
            }.onFailure { err ->
                _events.emit(UiEvent.Snackbar("Export failed: ${err.localizedMessage ?: "error"}"))
            }
        }
    }

    fun shareExportReport(isPdf: Boolean, params: ExportFilterParams) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dataset = buildFilteredDataset(params)
                    val stamp = Formatters.formatFileStamp()
                    val fileName = if (isPdf) "MachinaLog_Report_$stamp.pdf" else "MachinaLog_Report_$stamp.xlsx"
                    val file = ReportExportEngine.exportToCacheFile(app, fileName, isPdf, params, dataset)
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                }
            }
            _isBusy.value = false
            result.onSuccess { uri ->
                val mime = if (isPdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                val title = if (isPdf) "Share PDF Maintenance Report" else "Share Excel Maintenance Report"
                _events.emit(UiEvent.ShareFile(uri, mime, title))
            }.onFailure { err ->
                _events.emit(UiEvent.Snackbar("Share failed: ${err.localizedMessage ?: "error"}"))
            }
        }
    }

    // --- Backup & Portable Sync ---
    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupSyncManager.exportJsonString(dao)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to write backup file")
                }
            }
            _isBusy.value = false
            result.onSuccess {
                _events.emit(UiEvent.Snackbar("Portable backup file saved"))
            }.onFailure {
                _events.emit(UiEvent.Snackbar("Backup export failed"))
            }
        }
    }

    fun shareManualBackupFile() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val json = BackupSyncManager.exportJsonString(dao)
                    val dir = File(app.filesDir, "backups").apply { mkdirs() }
                    val file = File(dir, "machinalog_backup_${Formatters.formatFileStamp()}.json")
                    file.writeText(json, Charsets.UTF_8)
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                }
            }
            _isBusy.value = false
            refreshLocalBackups()
            result.onSuccess { uri ->
                _events.emit(UiEvent.ShareFile(uri, "application/json", "Send Backup File to PC or Device"))
            }.onFailure {
                _events.emit(UiEvent.Snackbar("Failed to prepare backup file"))
            }
        }
    }

    fun shareExistingLocalBackup(file: File) {
        val app = getApplication<Application>()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        viewModelScope.launch {
            _events.emit(UiEvent.ShareFile(uri, "application/json", "Share Auto-Backup Snapshot"))
        }
    }

    fun loadBackupPreview(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val pkg = withContext(Dispatchers.IO) {
                BackupSyncManager.readBackupFromUri(getApplication(), uri)
            }
            _isBusy.value = false
            if (pkg == null) {
                _events.emit(UiEvent.Snackbar("Invalid or unreadable MachinaLog backup file"))
            } else {
                _pendingImportPreview.value = pkg
            }
        }
    }

    fun loadBackupPreviewFromFile(file: File) {
        viewModelScope.launch {
            _isBusy.value = true
            val pkg = withContext(Dispatchers.IO) {
                BackupSyncManager.readBackupFromFile(file)
            }
            _isBusy.value = false
            if (pkg == null) {
                _events.emit(UiEvent.Snackbar("Could not read snapshot file"))
            } else {
                _pendingImportPreview.value = pkg
            }
        }
    }

    fun dismissImportPreview() {
        _pendingImportPreview.value = null
    }

    // --- Excel (.xlsx) Import & Duplicate Diff Check ---
    fun loadExcelImportPreview(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val defaultEng = uiState.value.preferences.defaultEngineerName
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val parsed = ExcelImportEngine.parseFromUri(getApplication(), uri, defaultEng)
                        ?: return@runCatching null
                    val diff = ExcelImportEngine.computeDiffSummary(
                        preview = parsed,
                        existingGroups = dao.getAllGroupsSync(),
                        existingMachines = dao.getAllMachinesSync(),
                        existingTasks = dao.getAllTasksSync(),
                        existingTodos = dao.getAllTodosSync(),
                        existingWarehouseParts = dao.getAllWarehousePartsSync(),
                        replaceExisting = false
                    )
                    parsed.copy(diffSummary = diff)
                }.getOrNull()
            }
            _isBusy.value = false
            if (preview == null) {
                _events.emit(UiEvent.Snackbar("Could not read .xlsx Excel workbook"))
            } else {
                _pendingExcelImportPreview.value = preview
            }
        }
    }

    fun dismissExcelImportPreview() {
        _pendingExcelImportPreview.value = null
    }

    fun confirmExcelImport(replaceExisting: Boolean) {
        val preview = _pendingExcelImportPreview.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val result = withContext(Dispatchers.IO) {
                ExcelImportEngine.commitImport(dao, prefs, preview, replaceExisting)
            }
            _pendingExcelImportPreview.value = null
            _isBusy.value = false
            val msg = if (replaceExisting) {
                "Replaced all data from Excel (${result.summary.totalNew} records imported)"
            } else {
                "Smart merge: ${result.formatSummaryMessage()}"
            }
            _events.emit(UiEvent.Snackbar(msg))
        }
    }

    fun saveExcelTemplateToUri(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        ExcelImportEngine.writeImportTemplateXlsx(out)
                    } ?: error("Cannot open output stream")
                }
            }
            _isBusy.value = false
            res.onSuccess {
                _events.emit(UiEvent.Snackbar("Excel import template saved"))
            }.onFailure {
                _events.emit(UiEvent.Snackbar("Failed to save template"))
            }
        }
    }

    fun confirmRestoreBackup(mode: RestoreMode) {
        val pkg = _pendingImportPreview.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            withContext(Dispatchers.IO) {
                BackupSyncManager.restoreBackup(dao, prefs, pkg, mode)
            }
            _pendingImportPreview.value = null
            _isBusy.value = false
            _events.emit(
                UiEvent.Snackbar(
                    if (mode == RestoreMode.MERGE) "Backup merged by UUID successfully"
                    else "Device data replaced from backup"
                )
            )
        }
    }

    fun triggerAutoBackupNow() {
        viewModelScope.launch {
            _isBusy.value = true
            withContext(Dispatchers.IO) {
                BackupSyncManager.runAutoBackup(getApplication())
            }
            refreshLocalBackups()
            _isBusy.value = false
            _events.emit(UiEvent.Snackbar("Auto-backup snapshot created"))
        }
    }

    // --- Settings & Preferences ---
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNotificationsEnabled(enabled)
            MaintenanceReminderScheduler.scheduleReminders(getApplication(), enabled)
            if (enabled) {
                MaintenanceReminderScheduler.triggerImmediateReminderCheck(getApplication())
            }
        }
    }

    fun triggerTestReminderCheck() {
        MaintenanceReminderScheduler.triggerImmediateReminderCheck(getApplication())
        viewModelScope.launch {
            _events.emit(UiEvent.Snackbar("Reminder check triggered"))
        }
    }

    fun setReminderLeadHours(hours: Int) {
        viewModelScope.launch { prefs.setReminderLeadHours(hours) }
    }

    fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
        viewModelScope.launch {
            prefs.setAutoBackupFrequency(frequency)
            MaintenanceReminderScheduler.scheduleAutoBackup(getApplication(), frequency)
            _events.emit(UiEvent.Snackbar("Backup schedule: ${frequency.label}"))
        }
    }

    fun setAutoBackupFolderUri(uri: Uri?) {
        val app = getApplication<Application>()
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                app.contentResolver.takePersistableUriPermission(uri, flags)
            }
        }
        viewModelScope.launch {
            prefs.setAutoBackupTreeUri(uri?.toString())
            if (uri != null) {
                _events.emit(UiEvent.Snackbar("Auto-backup folder linked"))
            }
        }
    }

    fun setDefaultEngineerName(name: String) {
        viewModelScope.launch {
            prefs.setDefaultEngineerName(name)
        }
    }

    fun resetSampleData() {
        viewModelScope.launch {
            repo.resetSampleData()
            _events.emit(UiEvent.Snackbar("Sample factory dataset restored"))
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            repo.clearAllRecords()
            _events.emit(UiEvent.Snackbar("All records cleared"))
        }
    }

    // --- PC Web Portal (Local Wi-Fi HTTP Server) ---
    fun setPcWebEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPcWebEnabled(enabled)
            val snapshot = prefs.getSnapshot()
            if (enabled) {
                LocalWebServerManager.start(
                    context = getApplication(),
                    dao = dao,
                    repo = repo,
                    prefs = prefs,
                    pinEnabled = snapshot.pcWebPinEnabled,
                    pinCode = snapshot.pcWebPin
                )
                val url = LocalWebServerManager.status.value.primaryUrl
                _events.emit(
                    UiEvent.Snackbar(
                        if (url != null) "PC Web Portal running at $url" else "PC Web Portal started"
                    )
                )
            } else {
                LocalWebServerManager.stop()
                _events.emit(UiEvent.Snackbar("PC Web Portal stopped"))
            }
        }
    }

    fun setPcWebPinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setPcWebPinEnabled(enabled)
            val snapshot = prefs.getSnapshot()
            LocalWebServerManager.updatePinConfig(enabled, snapshot.pcWebPin)
        }
    }

    fun regeneratePcWebPin() {
        viewModelScope.launch {
            val newPin = (1000 + Random.nextInt(9000)).toString()
            prefs.setPcWebPin(newPin)
            val snapshot = prefs.getSnapshot()
            LocalWebServerManager.updatePinConfig(snapshot.pcWebPinEnabled, newPin)
            _events.emit(UiEvent.Snackbar("New PC access PIN: $newPin"))
        }
    }

    fun refreshPcWebAddresses() {
        LocalWebServerManager.refreshNetworkAddresses()
    }
}
