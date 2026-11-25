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
import com.example.data.EngineerEntity
import com.example.data.GroupScope
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceRepository
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.ServiceLogWithParts
import com.example.data.Severity
import com.example.data.SparePartDraft
import com.example.data.SupplierEntity
import com.example.data.TaskOutcome
import com.example.data.TodoReminderEntity
import com.example.data.UserPreferences
import com.example.data.WarehousePartEntity
import com.example.BuildConfig
import com.example.engine.AppUpdateChecker
import com.example.engine.AppUpdateInfo
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

import java.util.concurrent.TimeUnit

enum class TaskGroupBy(val label: String) {
    CUSTOM_GROUP("Custom Group"),
    CADENCE("Cadence"),
    MACHINE("Machine"),
    PIC("Person in Charge"),
    NONE("Flat List")
}

enum class TodoGroupBy(val label: String) {
    SOURCE("Task vs Plain Reminder"),
    PRIORITY("Priority"),
    PIC("Person in Charge"),
    NONE("Flat List")
}

enum class HistoryGroupBy(val label: String) {
    CUSTOM_GROUP("Custom Group"),
    OUTCOME("Outcome (Done / Rejected)"),
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

fun isTaskApproachingOrOverdue(task: MaintenanceTaskEntity, nowMillis: Long): Boolean {
    if (task.deadlineMillis < nowMillis) return true
    val remaining = task.deadlineMillis - nowMillis
    if (remaining <= TimeUnit.HOURS.toMillis(24)) return true
    if (task.reminderEnabled) {
        val leadMs = TimeUnit.HOURS.toMillis(task.reminderLeadHours.coerceAtLeast(1).toLong())
        if (remaining <= leadMs) return true
    }
    return false
}

fun countApproachingOrOverdueTasks(
    tasks: List<MaintenanceTaskEntity>,
    nowMillis: Long = System.currentTimeMillis()
): Int = tasks.count { isTaskApproachingOrOverdue(it, nowMillis) }

data class TasksFilterState(
    val cadence: Cadence? = null,
    val priority: ReminderPriority? = null,
    val overdueOnly: Boolean = false,
    val groupId: Long? = null,
    val picFilter: String? = null,
    val searchQuery: String = "",
    val groupBy: TaskGroupBy = TaskGroupBy.CUSTOM_GROUP,
    val sortAscending: Boolean = true,
    val currentPage: Int = 0
)

data class TodoFilterState(
    val repeatRule: RepeatRule? = null,
    val priority: ReminderPriority? = null,
    val groupId: Long? = null,
    val picFilter: String? = null,
    val showCompleted: Boolean = false,
    val searchQuery: String = "",
    val groupBy: TodoGroupBy = TodoGroupBy.SOURCE,
    val sortAscending: Boolean = true,
    val currentPage: Int = 0
)

data class HistoryFilterState(
    val outcome: TaskOutcome? = null,
    val priority: ReminderPriority? = null,
    val cadence: Cadence? = null,
    val groupId: Long? = null,
    val picFilter: String? = null,
    val searchQuery: String = "",
    val groupBy: HistoryGroupBy = HistoryGroupBy.CUSTOM_GROUP,
    val sortAscending: Boolean = false,
    val currentPage: Int = 0
)

data class MachineFilterState(
    val status: MachineStatus? = null,
    val groupId: Long? = null,
    val groupBy: MachineGroupBy = MachineGroupBy.CUSTOM_GROUP,
    val searchQuery: String = "",
    val currentPage: Int = 0
)

data class UpdateUiBundle(
    val availableUpdate: AppUpdateInfo? = null,
    val latestDetectedUpdate: AppUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val updateStatusText: String? = null
)

private data class FilterUiBundle(
    val tasks: TasksFilterState,
    val todo: TodoFilterState,
    val history: HistoryFilterState,
    val machine: MachineFilterState,
    val report: ExportFilterParams
)

private data class CatalogUiBundle(
    val warehouseParts: List<WarehousePartEntity>,
    val suppliers: List<SupplierEntity>,
    val selectedSupplierId: Long?
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
    val historyFilter: HistoryFilterState = HistoryFilterState(),
    val machineFilter: MachineFilterState = MachineFilterState(),
    val reportFilter: ExportFilterParams = ExportFilterParams(),
    val selectedMachineId: Long? = null,
    val localBackups: List<File> = emptyList(),
    val pendingImportPreview: PortableBackupPackage? = null,
    val pendingExcelImportPreview: ExcelImportPreview? = null,
    val warehouseParts: List<WarehousePartEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList(),
    val selectedSupplierId: Long? = null,
    val engineers: List<EngineerEntity> = emptyList(),
    val webPortalStatus: WebPortalStatus = WebPortalStatus(),
    val availableUpdate: AppUpdateInfo? = null,
    val latestDetectedUpdate: AppUpdateInfo? = null,
    val isCheckingUpdate: Boolean = false,
    val updateStatusText: String? = null,
    val isBusy: Boolean = false
) {
    val knownEngineers: List<String>
        get() = engineers.map { it.name.trim() }
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
    private val _historyFilter = MutableStateFlow(HistoryFilterState())
    private val _machineFilter = MutableStateFlow(MachineFilterState())
    private val _reportFilter = MutableStateFlow(ExportFilterParams())
    private val _filterBundleFlow = combine(
        _tasksFilter,
        _todoFilter,
        _historyFilter,
        _machineFilter,
        _reportFilter
    ) { tasks, todo, history, machine, report ->
        FilterUiBundle(tasks, todo, history, machine, report)
    }
    private val _selectedMachineId = MutableStateFlow<Long?>(null)
    private val _selectedSupplierId = MutableStateFlow<Long?>(null)
    private val _catalogBundleFlow = combine(
        repo.warehousePartsFlow,
        repo.suppliersFlow,
        _selectedSupplierId
    ) { parts, sups, selSup ->
        CatalogUiBundle(parts, sups, selSup)
    }
    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    private val _pendingImportPreview = MutableStateFlow<PortableBackupPackage?>(null)
    private val _pendingExcelImportPreview = MutableStateFlow<ExcelImportPreview?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    private val _latestDetectedUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _updateStatusText = MutableStateFlow<String?>(null)
    private val _updateBundleFlow = combine(
        _availableUpdate,
        _latestDetectedUpdate,
        _isCheckingUpdate,
        _updateStatusText
    ) { avail, latest, checking, status ->
        UpdateUiBundle(avail, latest, checking, status)
    }

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val uiState: StateFlow<MachinaUiState> = combine(
        repo.groupsFlow,
        repo.machinesFlow,
        repo.tasksFlow,
        repo.todosFlow,
        repo.serviceLogsFlow,
        repo.preferencesFlow,
        _filterBundleFlow,
        _selectedMachineId,
        _localBackups,
        _pendingImportPreview,
        _pendingExcelImportPreview,
        _isBusy,
        _catalogBundleFlow,
        LocalWebServerManager.status,
        repo.engineersFlow,
        _updateBundleFlow
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
        val filters = values[6] as FilterUiBundle
        val selMachine = values[7] as Long?
        @Suppress("UNCHECKED_CAST")
        val backups = values[8] as List<File>
        val importPreview = values[9] as PortableBackupPackage?
        val excelPreview = values[10] as ExcelImportPreview?
        val busy = values[11] as Boolean
        val catalogBundle = values[12] as CatalogUiBundle
        val webStatus = values[13] as WebPortalStatus
        @Suppress("UNCHECKED_CAST")
        val engineersList = values[14] as List<EngineerEntity>
        val updateBundle = values[15] as UpdateUiBundle

        val resolvedSelectedId = if (selMachine != null && machines.any { it.id == selMachine }) {
            selMachine
        } else {
            machines.firstOrNull()?.id
        }
        val resolvedSelectedSupplierId = if (catalogBundle.selectedSupplierId != null &&
            catalogBundle.suppliers.any { it.id == catalogBundle.selectedSupplierId }
        ) {
            catalogBundle.selectedSupplierId
        } else {
            catalogBundle.suppliers.firstOrNull()?.id
        }

        MachinaUiState(
            groups = groups,
            machines = machines,
            tasks = tasks,
            todos = todos,
            serviceLogs = logs,
            preferences = userPrefs,
            tasksFilter = filters.tasks,
            todoFilter = filters.todo,
            historyFilter = filters.history,
            machineFilter = filters.machine,
            reportFilter = filters.report,
            selectedMachineId = resolvedSelectedId,
            localBackups = backups,
            pendingImportPreview = importPreview,
            pendingExcelImportPreview = excelPreview,
            warehouseParts = catalogBundle.warehouseParts,
            suppliers = catalogBundle.suppliers,
            selectedSupplierId = resolvedSelectedSupplierId,
            engineers = engineersList,
            webPortalStatus = webStatus,
            availableUpdate = updateBundle.availableUpdate,
            latestDetectedUpdate = updateBundle.latestDetectedUpdate,
            isCheckingUpdate = updateBundle.isCheckingUpdate,
            updateStatusText = updateBundle.updateStatusText,
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
            if (settings.autoCheckUpdates) {
                checkForUpdates(manual = false)
            }
        }
    }

    fun refreshLocalBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _localBackups.value = BackupSyncManager.getLocalAutoBackups(getApplication())
        }
    }

    fun checkForUpdates(manual: Boolean) {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val settings = prefs.getSnapshot()
            val outcome = AppUpdateChecker.checkLatestRelease(
                currentVersionName = BuildConfig.VERSION_NAME,
                currentVersionCode = BuildConfig.VERSION_CODE,
                githubRepo = settings.githubRepo,
                githubToken = settings.githubToken
            )
            _isCheckingUpdate.value = false
            val now = System.currentTimeMillis()
            val info = outcome.info
            if (info != null) {
                prefs.recordUpdateCheck(now, info.latestTag)
                if (info.isUpdateAvailable) {
                    _latestDetectedUpdate.value = info
                    _availableUpdate.value = info
                    val apkNote = if (info.apkDownloadUrl != null) "APK ready" else "Open Release page"
                    _updateStatusText.value = "Update available: ${info.latestTag} (installed v${BuildConfig.VERSION_NAME}, Build ${BuildConfig.VERSION_CODE}) — $apkNote"
                } else {
                    _latestDetectedUpdate.value = null
                    val msg = outcome.errorMessage ?: run {
                        val apkNote = if (info.apkDownloadUrl != null) ".apk attached" else "no .apk attached"
                        "Up to date — installed v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • GitHub latest: ${info.latestTag} ($apkNote)"
                    }
                    _updateStatusText.value = msg
                    if (manual) {
                        _events.emit(UiEvent.Snackbar(msg))
                    }
                }
            } else {
                _latestDetectedUpdate.value = null
                val err = outcome.errorMessage ?: "Unable to check updates right now."
                _updateStatusText.value = err
                if (manual) {
                    _events.emit(UiEvent.Snackbar(err))
                }
            }
        }
    }

    fun openLatestUpdateDialog() {
        _latestDetectedUpdate.value?.let { _availableUpdate.value = it }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoCheckUpdates(enabled)
        }
    }

    fun setGithubUpdateConfig(repo: String, token: String) {
        viewModelScope.launch {
            prefs.setGithubRepo(repo)
            prefs.setGithubToken(token)
            checkForUpdates(manual = true)
        }
    }

    // --- Filter Mutations ---
    fun updateTasksFilter(transform: (TasksFilterState) -> TasksFilterState) {
        _tasksFilter.value = transform(_tasksFilter.value)
    }

    fun focusEngineerTasks(engineerName: String) {
        _tasksFilter.value = TasksFilterState(picFilter = engineerName)
    }

    fun updateTodoFilter(transform: (TodoFilterState) -> TodoFilterState) {
        _todoFilter.value = transform(_todoFilter.value)
    }

    fun updateHistoryFilter(transform: (HistoryFilterState) -> HistoryFilterState) {
        _historyFilter.value = transform(_historyFilter.value)
    }

    fun focusEngineerHistory(engineerName: String) {
        _historyFilter.value = HistoryFilterState(picFilter = engineerName)
    }

    fun setTodoPageSize(size: Int) {
        setListPageSize(size)
    }

    fun setListPageSize(size: Int) {
        viewModelScope.launch {
            prefs.setTodoPageSize(size)
            _tasksFilter.value = _tasksFilter.value.copy(currentPage = 0)
            _todoFilter.value = _todoFilter.value.copy(currentPage = 0)
            _historyFilter.value = _historyFilter.value.copy(currentPage = 0)
            _machineFilter.value = _machineFilter.value.copy(currentPage = 0)
        }
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

    fun selectSupplier(supplierId: Long?) {
        _selectedSupplierId.value = supplierId
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

    // --- Engineers CRUD ---
    fun saveEngineer(
        id: Long,
        uuid: String?,
        name: String,
        employeeCode: String,
        role: String,
        shift: String
    ) {
        viewModelScope.launch {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) return@launch
            val entity = if (id == 0L || uuid == null) {
                EngineerEntity(
                    name = cleanName,
                    employeeCode = employeeCode.trim().uppercase(),
                    role = role.trim().ifEmpty { "Maintenance Engineer" },
                    shift = shift.trim().ifEmpty { "Day Shift" }
                )
            } else {
                EngineerEntity(
                    id = id,
                    uuid = uuid,
                    name = cleanName,
                    employeeCode = employeeCode.trim().uppercase(),
                    role = role.trim().ifEmpty { "Maintenance Engineer" },
                    shift = shift.trim().ifEmpty { "Day Shift" }
                )
            }
            repo.saveEngineer(entity)
            _events.emit(UiEvent.Snackbar("Engineer \"$cleanName\" saved"))
        }
    }

    fun deleteEngineer(engineerId: Long) {
        viewModelScope.launch {
            repo.deleteEngineer(engineerId)
            _events.emit(UiEvent.Snackbar("Engineer removed"))
        }
    }

    // --- Task CRUD, Completion & Rejection ---
    fun saveTask(
        id: Long,
        uuid: String?,
        machineId: Long,
        groupId: Long?,
        title: String,
        description: String,
        cadence: Cadence,
        priority: ReminderPriority,
        deadlineMillis: Long,
        estimatedMinutes: Int,
        personInCharge: String,
        assistantEngineers: String,
        reminderEnabled: Boolean,
        reminderLeadHours: Int,
        lastCompletedMillis: Long?,
        machineStatus: MachineStatus? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existingCreatedAt = if (id != 0L) {
                uiState.value.tasks.find { it.id == id }?.createdAtMillis
            } else null
            val entity = if (id == 0L || uuid == null) {
                MaintenanceTaskEntity(
                    machineId = machineId,
                    groupId = groupId,
                    title = title.trim(),
                    description = description.trim(),
                    cadence = cadence,
                    priority = priority,
                    deadlineMillis = deadlineMillis,
                    estimatedMinutes = estimatedMinutes.coerceAtLeast(1),
                    personInCharge = personInCharge.trim(),
                    assistantEngineers = assistantEngineers.trim(),
                    reminderEnabled = reminderEnabled,
                    reminderLeadHours = reminderLeadHours.coerceAtLeast(1),
                    createdAtMillis = now
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
                    priority = priority,
                    deadlineMillis = deadlineMillis,
                    estimatedMinutes = estimatedMinutes.coerceAtLeast(1),
                    personInCharge = personInCharge.trim(),
                    assistantEngineers = assistantEngineers.trim(),
                    reminderEnabled = reminderEnabled,
                    reminderLeadHours = reminderLeadHours.coerceAtLeast(1),
                    lastCompletedMillis = lastCompletedMillis,
                    createdAtMillis = existingCreatedAt ?: now
                )
            }
            repo.saveTask(entity, machineStatus)
            if (id == 0L && uiState.value.preferences.notificationsEnabled) {
                val m = uiState.value.machines.find { it.id == machineId }
                val mLabel = m?.let { "${it.assetCode} · ${it.name}" }.orEmpty()
                NotificationChannels.notifyNewTaskCreated(
                    context = getApplication(),
                    title = entity.title,
                    machineLabel = mLabel,
                    personInCharge = entity.personInCharge,
                    deadlineMillis = entity.deadlineMillis
                )
            }
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
        assistantEngineers: List<String>,
        actualDurationMinutes: Int,
        notes: String,
        spareParts: List<SparePartDraft>,
        flagMalfunction: Boolean = false,
        malfunctionSeverity: Severity = Severity.LOW
    ) {
        viewModelScope.launch {
            val task = uiState.value.tasks.find { it.id == taskId }
            repo.completeTask(
                taskId = taskId,
                personInCharge = personInCharge,
                assistantEngineers = assistantEngineers,
                actualDurationMinutes = actualDurationMinutes,
                notes = notes,
                spareParts = spareParts,
                flagMalfunction = flagMalfunction,
                malfunctionSeverity = malfunctionSeverity
            )
            MaintenanceReminderScheduler.triggerImmediateReminderCheck(getApplication())
            val msg = if (task != null && task.cadence != Cadence.ONCE) {
                "Task done · Report generated & next ${task.cadence.label.lowercase()} task recreated"
            } else {
                "Task completed & report generated"
            }
            _events.emit(UiEvent.Snackbar(msg))
        }
    }

    fun rejectTask(
        taskId: Long,
        personInCharge: String,
        assistantEngineers: List<String>,
        rejectionReason: String,
        rescheduleNextCycle: Boolean
    ) {
        viewModelScope.launch {
            repo.rejectTask(
                taskId = taskId,
                personInCharge = personInCharge,
                assistantEngineers = assistantEngineers,
                rejectionReason = rejectionReason,
                rescheduleNextCycle = rescheduleNextCycle
            )
            MaintenanceReminderScheduler.triggerImmediateReminderCheck(getApplication())
            _events.emit(UiEvent.Snackbar("Task rejected & historical report recorded"))
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
        defaultUnit: String,
        supplierId: Long? = null,
        supplierPartSku: String = "",
        unitPrice: Double? = null,
        currency: String = "USD",
        leadTimeDays: Int? = null
    ) {
        viewModelScope.launch {
            val entity = if (id == 0L || uuid == null) {
                WarehousePartEntity(
                    partCode = partCode.trim().uppercase(),
                    partName = partName.trim(),
                    partDescription = partDescription.trim(),
                    defaultUnit = defaultUnit.trim().ifEmpty { "pcs" },
                    supplierId = supplierId,
                    supplierPartSku = supplierPartSku.trim(),
                    unitPrice = unitPrice,
                    currency = currency.trim().uppercase().ifEmpty { "USD" },
                    leadTimeDays = leadTimeDays
                )
            } else {
                WarehousePartEntity(
                    id = id,
                    uuid = uuid,
                    partCode = partCode.trim().uppercase(),
                    partName = partName.trim(),
                    partDescription = partDescription.trim(),
                    defaultUnit = defaultUnit.trim().ifEmpty { "pcs" },
                    supplierId = supplierId,
                    supplierPartSku = supplierPartSku.trim(),
                    unitPrice = unitPrice,
                    currency = currency.trim().uppercase().ifEmpty { "USD" },
                    leadTimeDays = leadTimeDays
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

    // --- Suppliers Directory ---
    fun saveSupplier(
        id: Long,
        uuid: String?,
        companyName: String,
        supplierCode: String,
        contactPerson: String,
        phone: String,
        email: String,
        address: String,
        notes: String
    ) {
        viewModelScope.launch {
            val cleanName = companyName.trim()
            if (cleanName.isEmpty()) return@launch
            val entity = if (id == 0L || uuid == null) {
                SupplierEntity(
                    companyName = cleanName,
                    supplierCode = supplierCode.trim().uppercase(),
                    contactPerson = contactPerson.trim(),
                    phone = phone.trim(),
                    email = email.trim(),
                    address = address.trim(),
                    notes = notes.trim()
                )
            } else {
                SupplierEntity(
                    id = id,
                    uuid = uuid,
                    companyName = cleanName,
                    supplierCode = supplierCode.trim().uppercase(),
                    contactPerson = contactPerson.trim(),
                    phone = phone.trim(),
                    email = email.trim(),
                    address = address.trim(),
                    notes = notes.trim()
                )
            }
            val savedId = repo.saveSupplier(entity)
            _selectedSupplierId.value = savedId
            _events.emit(UiEvent.Snackbar("Supplier \"$cleanName\" saved"))
        }
    }

    fun deleteSupplier(supplierId: Long) {
        viewModelScope.launch {
            repo.deleteSupplier(supplierId)
            if (_selectedSupplierId.value == supplierId) {
                _selectedSupplierId.value = null
            }
            _events.emit(UiEvent.Snackbar("Supplier removed"))
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
            val now = System.currentTimeMillis()
            val existingCreatedAt = if (id != 0L) {
                uiState.value.todos.find { it.id == id }?.createdAtMillis
            } else null
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
                    priority = priority,
                    createdAtMillis = now
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
                    completedAtMillis = completedAtMillis,
                    createdAtMillis = existingCreatedAt ?: now
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
            if (result.summary.tasks.newCount > 0 && uiState.value.preferences.notificationsEnabled) {
                val firstNew = preview.tasks.firstOrNull()
                if (firstNew != null) {
                    NotificationChannels.notifyNewTaskCreated(
                        context = getApplication(),
                        title = firstNew.title,
                        machineLabel = firstNew.assetCode,
                        personInCharge = firstNew.personInCharge,
                        deadlineMillis = firstNew.deadlineMillis,
                        extraCount = (result.summary.tasks.newCount - 1).coerceAtLeast(0)
                    )
                }
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
