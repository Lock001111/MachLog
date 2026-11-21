package com.example.engine

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.GroupScope
import com.example.data.MachinaDao
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogEntity
import com.example.data.Severity
import com.example.data.SparePartUsedEntity
import com.example.data.TodoReminderEntity
import com.example.data.WarehousePartEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

enum class RestoreMode {
    MERGE,
    REPLACE
}

@JsonClass(generateAdapter = true)
data class GroupBackupDto(
    val uuid: String,
    val name: String,
    val scope: String,
    val colorKey: String,
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class MachineBackupDto(
    val uuid: String,
    val groupUuid: String?,
    val assetCode: String,
    val name: String,
    val locationZone: String,
    val modelInfo: String,
    val status: String,
    val notes: String,
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class TaskBackupDto(
    val uuid: String,
    val machineUuid: String,
    val groupUuid: String?,
    val title: String,
    val description: String,
    val cadence: String,
    val deadlineMillis: Long,
    val estimatedMinutes: Int,
    val personInCharge: String,
    val reminderEnabled: Boolean,
    val reminderLeadHours: Int,
    val lastCompletedMillis: Long?,
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class TodoBackupDto(
    val uuid: String,
    val title: String,
    val notes: String,
    val machineUuid: String?,
    val groupUuid: String?,
    val personInCharge: String,
    val repeatRule: String,
    val dueAtMillis: Long,
    val reminderEnabled: Boolean,
    val isCompleted: Boolean,
    val priority: String,
    val completedAtMillis: Long?,
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class SparePartBackupDto(
    val uuid: String,
    val partName: String,
    val partCode: String,
    val partDescription: String = "",
    val fromWarehouse: Boolean = true,
    val quantity: Int,
    val unit: String
)

@JsonClass(generateAdapter = true)
data class ServiceLogBackupDto(
    val uuid: String,
    val machineUuid: String,
    val taskUuid: String?,
    val entryType: String,
    val cadenceSnapshot: String?,
    val summaryTitle: String,
    val malfunctionDetails: String,
    val severity: String,
    val personInCharge: String,
    val serviceDurationMinutes: Int,
    val completedAtMillis: Long,
    val deadlineWasMet: Boolean,
    val updatedAtMillis: Long,
    val spareParts: List<SparePartBackupDto>
)

@JsonClass(generateAdapter = true)
data class WarehousePartBackupDto(
    val uuid: String,
    val partCode: String,
    val partName: String = "",
    val partDescription: String = "",
    val defaultUnit: String = "pcs",
    val updatedAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class PortableBackupPackage(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val deviceLabel: String,
    val groups: List<GroupBackupDto>,
    val machines: List<MachineBackupDto>,
    val tasks: List<TaskBackupDto>,
    val todos: List<TodoBackupDto>,
    val serviceLogs: List<ServiceLogBackupDto>,
    val warehouseParts: List<WarehousePartBackupDto> = emptyList()
)

object BackupSyncManager {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(PortableBackupPackage::class.java).indent("  ")

    suspend fun createBackupPackage(dao: MachinaDao): PortableBackupPackage {
        val groups = dao.getAllGroupsSync()
        val machines = dao.getAllMachinesSync()
        val tasks = dao.getAllTasksSync()
        val todos = dao.getAllTodosSync()
        val logsWithParts = dao.getAllServiceLogsWithPartsSync()
        val warehouseParts = dao.getAllWarehousePartsSync()

        val groupUuidById = groups.associate { it.id to it.uuid }
        val machineUuidById = machines.associate { it.id to it.uuid }
        val taskUuidById = tasks.associate { it.id to it.uuid }

        return PortableBackupPackage(
            schemaVersion = 1,
            exportedAtMillis = System.currentTimeMillis(),
            deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}",
            groups = groups.map {
                GroupBackupDto(
                    uuid = it.uuid,
                    name = it.name,
                    scope = it.scope.name,
                    colorKey = it.colorKey,
                    updatedAtMillis = it.updatedAtMillis
                )
            },
            machines = machines.map {
                MachineBackupDto(
                    uuid = it.uuid,
                    groupUuid = it.groupId?.let { gid -> groupUuidById[gid] },
                    assetCode = it.assetCode,
                    name = it.name,
                    locationZone = it.locationZone,
                    modelInfo = it.modelInfo,
                    status = it.status.name,
                    notes = it.notes,
                    updatedAtMillis = it.updatedAtMillis
                )
            },
            tasks = tasks.map {
                TaskBackupDto(
                    uuid = it.uuid,
                    machineUuid = machineUuidById[it.machineId] ?: "",
                    groupUuid = it.groupId?.let { gid -> groupUuidById[gid] },
                    title = it.title,
                    description = it.description,
                    cadence = it.cadence.name,
                    deadlineMillis = it.deadlineMillis,
                    estimatedMinutes = it.estimatedMinutes,
                    personInCharge = it.personInCharge,
                    reminderEnabled = it.reminderEnabled,
                    reminderLeadHours = it.reminderLeadHours,
                    lastCompletedMillis = it.lastCompletedMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            },
            todos = todos.map {
                TodoBackupDto(
                    uuid = it.uuid,
                    title = it.title,
                    notes = it.notes,
                    machineUuid = it.machineId?.let { mid -> machineUuidById[mid] },
                    groupUuid = it.groupId?.let { gid -> groupUuidById[gid] },
                    personInCharge = it.personInCharge,
                    repeatRule = it.repeatRule.name,
                    dueAtMillis = it.dueAtMillis,
                    reminderEnabled = it.reminderEnabled,
                    isCompleted = it.isCompleted,
                    priority = it.priority.name,
                    completedAtMillis = it.completedAtMillis,
                    updatedAtMillis = it.updatedAtMillis
                )
            },
            serviceLogs = logsWithParts.map { item ->
                val l = item.log
                ServiceLogBackupDto(
                    uuid = l.uuid,
                    machineUuid = machineUuidById[l.machineId] ?: "",
                    taskUuid = l.taskId?.let { tid -> taskUuidById[tid] },
                    entryType = l.entryType.name,
                    cadenceSnapshot = l.cadenceSnapshot?.name,
                    summaryTitle = l.summaryTitle,
                    malfunctionDetails = l.malfunctionDetails,
                    severity = l.severity.name,
                    personInCharge = l.personInCharge,
                    serviceDurationMinutes = l.serviceDurationMinutes,
                    completedAtMillis = l.completedAtMillis,
                    deadlineWasMet = l.deadlineWasMet,
                    updatedAtMillis = l.updatedAtMillis,
                    spareParts = item.parts.map { p ->
                        SparePartBackupDto(
                            uuid = p.uuid,
                            partName = p.partName,
                            partCode = p.partCode,
                            partDescription = p.partDescription,
                            fromWarehouse = p.fromWarehouse,
                            quantity = p.quantity,
                            unit = p.unit
                        )
                    }
                )
            },
            warehouseParts = warehouseParts.map { w ->
                WarehousePartBackupDto(
                    uuid = w.uuid,
                    partCode = w.partCode,
                    partName = w.partName,
                    partDescription = w.partDescription,
                    defaultUnit = w.defaultUnit,
                    updatedAtMillis = w.updatedAtMillis
                )
            }
        )
    }

    suspend fun exportJsonString(dao: MachinaDao): String {
        return adapter.toJson(createBackupPackage(dao))
    }

    fun parseBackupJson(json: String): PortableBackupPackage? {
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    fun readBackupFromUri(context: Context, uri: Uri): PortableBackupPackage? {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return null
        return parseBackupJson(text)
    }

    fun readBackupFromFile(file: File): PortableBackupPackage? {
        if (!file.exists()) return null
        return runCatching { parseBackupJson(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    suspend fun restoreBackup(
        dao: MachinaDao,
        prefs: AppPreferences,
        pkg: PortableBackupPackage,
        mode: RestoreMode
    ) {
        if (mode == RestoreMode.REPLACE) {
            dao.clearAllData()
        }

        val existingGroups = dao.getAllGroupsSync().associateBy { it.uuid }.toMutableMap()
        val groupUuidToId = mutableMapOf<String, Long>()
        existingGroups.values.forEach { groupUuidToId[it.uuid] = it.id }

        for (g in pkg.groups) {
            val existing = existingGroups[g.uuid]
            val scope = runCatching { GroupScope.valueOf(g.scope) }.getOrDefault(GroupScope.ALL)
            if (existing == null) {
                val id = dao.insertGroup(
                    CustomGroupEntity(
                        uuid = g.uuid,
                        name = g.name,
                        scope = scope,
                        colorKey = g.colorKey,
                        updatedAtMillis = g.updatedAtMillis
                    )
                )
                groupUuidToId[g.uuid] = id
            } else if (g.updatedAtMillis > existing.updatedAtMillis) {
                dao.updateGroup(
                    existing.copy(
                        name = g.name,
                        scope = scope,
                        colorKey = g.colorKey,
                        updatedAtMillis = g.updatedAtMillis
                    )
                )
            }
        }

        val existingMachines = dao.getAllMachinesSync().associateBy { it.uuid }.toMutableMap()
        val machineUuidToId = mutableMapOf<String, Long>()
        existingMachines.values.forEach { machineUuidToId[it.uuid] = it.id }

        for (m in pkg.machines) {
            val existing = existingMachines[m.uuid]
            val groupId = m.groupUuid?.let { groupUuidToId[it] }
            val status = runCatching { MachineStatus.valueOf(m.status) }.getOrDefault(MachineStatus.OPERATIONAL)
            if (existing == null) {
                val id = dao.insertMachine(
                    MachineEntity(
                        uuid = m.uuid,
                        groupId = groupId,
                        assetCode = m.assetCode,
                        name = m.name,
                        locationZone = m.locationZone,
                        modelInfo = m.modelInfo,
                        status = status,
                        notes = m.notes,
                        updatedAtMillis = m.updatedAtMillis
                    )
                )
                machineUuidToId[m.uuid] = id
            } else if (m.updatedAtMillis > existing.updatedAtMillis) {
                dao.updateMachine(
                    existing.copy(
                        groupId = groupId,
                        assetCode = m.assetCode,
                        name = m.name,
                        locationZone = m.locationZone,
                        modelInfo = m.modelInfo,
                        status = status,
                        notes = m.notes,
                        updatedAtMillis = m.updatedAtMillis
                    )
                )
            }
        }

        val existingTasks = dao.getAllTasksSync().associateBy { it.uuid }.toMutableMap()
        val taskUuidToId = mutableMapOf<String, Long>()
        existingTasks.values.forEach { taskUuidToId[it.uuid] = it.id }

        for (t in pkg.tasks) {
            val machineId = machineUuidToId[t.machineUuid] ?: continue
            val groupId = t.groupUuid?.let { groupUuidToId[it] }
            val cadence = runCatching { Cadence.valueOf(t.cadence) }.getOrDefault(Cadence.DAILY)
            val existing = existingTasks[t.uuid]
            if (existing == null) {
                val id = dao.insertTask(
                    MaintenanceTaskEntity(
                        uuid = t.uuid,
                        machineId = machineId,
                        groupId = groupId,
                        title = t.title,
                        description = t.description,
                        cadence = cadence,
                        deadlineMillis = t.deadlineMillis,
                        estimatedMinutes = t.estimatedMinutes,
                        personInCharge = t.personInCharge,
                        reminderEnabled = t.reminderEnabled,
                        reminderLeadHours = t.reminderLeadHours,
                        lastCompletedMillis = t.lastCompletedMillis,
                        updatedAtMillis = t.updatedAtMillis
                    )
                )
                taskUuidToId[t.uuid] = id
            } else if (t.updatedAtMillis > existing.updatedAtMillis) {
                dao.updateTask(
                    existing.copy(
                        machineId = machineId,
                        groupId = groupId,
                        title = t.title,
                        description = t.description,
                        cadence = cadence,
                        deadlineMillis = t.deadlineMillis,
                        estimatedMinutes = t.estimatedMinutes,
                        personInCharge = t.personInCharge,
                        reminderEnabled = t.reminderEnabled,
                        reminderLeadHours = t.reminderLeadHours,
                        lastCompletedMillis = t.lastCompletedMillis,
                        updatedAtMillis = t.updatedAtMillis
                    )
                )
            }
        }

        val existingTodos = dao.getAllTodosSync().associateBy { it.uuid }
        for (td in pkg.todos) {
            val machineId = td.machineUuid?.let { machineUuidToId[it] }
            val groupId = td.groupUuid?.let { groupUuidToId[it] }
            val repeatRule = runCatching { RepeatRule.valueOf(td.repeatRule) }.getOrDefault(RepeatRule.ONCE)
            val priority = runCatching { ReminderPriority.valueOf(td.priority) }.getOrDefault(ReminderPriority.NORMAL)
            val existing = existingTodos[td.uuid]
            if (existing == null) {
                dao.insertTodo(
                    TodoReminderEntity(
                        uuid = td.uuid,
                        title = td.title,
                        notes = td.notes,
                        machineId = machineId,
                        groupId = groupId,
                        personInCharge = td.personInCharge,
                        repeatRule = repeatRule,
                        dueAtMillis = td.dueAtMillis,
                        reminderEnabled = td.reminderEnabled,
                        isCompleted = td.isCompleted,
                        priority = priority,
                        completedAtMillis = td.completedAtMillis,
                        updatedAtMillis = td.updatedAtMillis
                    )
                )
            } else if (td.updatedAtMillis > existing.updatedAtMillis) {
                dao.updateTodo(
                    existing.copy(
                        title = td.title,
                        notes = td.notes,
                        machineId = machineId,
                        groupId = groupId,
                        personInCharge = td.personInCharge,
                        repeatRule = repeatRule,
                        dueAtMillis = td.dueAtMillis,
                        reminderEnabled = td.reminderEnabled,
                        isCompleted = td.isCompleted,
                        priority = priority,
                        completedAtMillis = td.completedAtMillis,
                        updatedAtMillis = td.updatedAtMillis
                    )
                )
            }
        }

        val existingLogs = dao.getAllServiceLogsWithPartsSync().associateBy { it.log.uuid }
        for (lg in pkg.serviceLogs) {
            val machineId = machineUuidToId[lg.machineUuid] ?: continue
            val taskId = lg.taskUuid?.let { taskUuidToId[it] }
            val entryType = runCatching { ServiceEntryType.valueOf(lg.entryType) }
                .getOrDefault(ServiceEntryType.PREVENTIVE_SERVICE)
            val cadenceSnap = lg.cadenceSnapshot?.let { runCatching { Cadence.valueOf(it) }.getOrNull() }
            val severity = runCatching { Severity.valueOf(lg.severity) }.getOrDefault(Severity.LOW)

            val existing = existingLogs[lg.uuid]
            if (existing == null) {
                val logEntity = ServiceLogEntity(
                    uuid = lg.uuid,
                    machineId = machineId,
                    taskId = taskId,
                    entryType = entryType,
                    cadenceSnapshot = cadenceSnap,
                    summaryTitle = lg.summaryTitle,
                    malfunctionDetails = lg.malfunctionDetails,
                    severity = severity,
                    personInCharge = lg.personInCharge,
                    serviceDurationMinutes = lg.serviceDurationMinutes,
                    completedAtMillis = lg.completedAtMillis,
                    deadlineWasMet = lg.deadlineWasMet,
                    updatedAtMillis = lg.updatedAtMillis
                )
                val partEntities = lg.spareParts.map { p ->
                    SparePartUsedEntity(
                        uuid = p.uuid,
                        serviceLogId = 0L,
                        partName = p.partName,
                        partCode = p.partCode,
                        partDescription = p.partDescription,
                        fromWarehouse = p.fromWarehouse,
                        quantity = p.quantity,
                        unit = p.unit
                    )
                }
                dao.insertLogWithParts(logEntity, partEntities)
            } else if (lg.updatedAtMillis > existing.log.updatedAtMillis) {
                val updatedLog = existing.log.copy(
                    machineId = machineId,
                    taskId = taskId,
                    entryType = entryType,
                    cadenceSnapshot = cadenceSnap,
                    summaryTitle = lg.summaryTitle,
                    malfunctionDetails = lg.malfunctionDetails,
                    severity = severity,
                    personInCharge = lg.personInCharge,
                    serviceDurationMinutes = lg.serviceDurationMinutes,
                    completedAtMillis = lg.completedAtMillis,
                    deadlineWasMet = lg.deadlineWasMet,
                    updatedAtMillis = lg.updatedAtMillis
                )
                dao.insertServiceLog(updatedLog)
                dao.deletePartsForLog(existing.log.id)
                dao.insertSpareParts(
                    lg.spareParts.map { p ->
                        SparePartUsedEntity(
                            uuid = p.uuid,
                            serviceLogId = existing.log.id,
                            partName = p.partName,
                            partCode = p.partCode,
                            partDescription = p.partDescription,
                            fromWarehouse = p.fromWarehouse,
                            quantity = p.quantity,
                            unit = p.unit
                        )
                    }
                )
            }
        }

        val existingWarehouse = dao.getAllWarehousePartsSync().associateBy { it.uuid }
        for (w in pkg.warehouseParts) {
            val existing = existingWarehouse[w.uuid]
            if (existing == null) {
                dao.insertWarehousePart(
                    WarehousePartEntity(
                        uuid = w.uuid,
                        partCode = w.partCode.trim().uppercase(),
                        partName = w.partName.trim(),
                        partDescription = w.partDescription.trim(),
                        defaultUnit = w.defaultUnit.trim().ifEmpty { "pcs" },
                        updatedAtMillis = w.updatedAtMillis
                    )
                )
            } else if (w.updatedAtMillis > existing.updatedAtMillis) {
                dao.updateWarehousePart(
                    existing.copy(
                        partCode = w.partCode.trim().uppercase(),
                        partName = w.partName.trim(),
                        partDescription = w.partDescription.trim(),
                        defaultUnit = w.defaultUnit.trim().ifEmpty { "pcs" },
                        updatedAtMillis = w.updatedAtMillis
                    )
                )
            }
        }

        prefs.setSampleDataSeeded(true)
    }

    fun getLocalAutoBackups(context: Context): List<File> {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun runAutoBackup(context: Context): Boolean {
        val db = AppDatabase.getInstance(context)
        val dao = db.machinaDao()
        val prefs = AppPreferences(context)
        val snapshot = prefs.getSnapshot()
        val json = exportJsonString(dao)
        val now = System.currentTimeMillis()
        val fileName = "machinalog_autobackup_${Formatters.formatFileStamp(now)}.json"

        // Always store a rolling local copy in app files/backups (max 7 snapshots)
        val localDir = File(context.filesDir, "backups").apply { mkdirs() }
        File(localDir, fileName).writeText(json, Charsets.UTF_8)
        localDir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(7)
            ?.forEach { it.delete() }

        // Also write to user-selected SAF folder if configured
        val treeUriStr = snapshot.autoBackupTreeUri
        if (!treeUriStr.isNullOrBlank()) {
            runCatching {
                val treeUri = Uri.parse(treeUriStr)
                val folder = DocumentFile.fromTreeUri(context, treeUri)
                val created = folder?.createFile("application/json", fileName)
                if (created != null) {
                    context.contentResolver.openOutputStream(created.uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }

        prefs.setLastAutoBackupMillis(now)
        return true
    }
}
