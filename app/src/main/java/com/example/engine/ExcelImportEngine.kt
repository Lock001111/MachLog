package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.AppPreferences
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.EngineerEntity
import com.example.data.GroupScope
import com.example.data.MachinaDao
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.TodoReminderEntity
import com.example.data.WarehousePartEntity
import com.example.data.parseEngineerList
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

data class ImportedGroupRow(
    val name: String,
    val scope: GroupScope,
    val colorKey: String
)

data class ImportedMachineRow(
    val assetCode: String,
    val name: String,
    val locationZone: String,
    val modelInfo: String,
    val status: MachineStatus,
    val groupName: String,
    val notes: String
)

data class ImportedTaskRow(
    val assetCode: String,
    val title: String,
    val description: String,
    val cadence: Cadence,
    val deadlineMillis: Long,
    val estimatedMinutes: Int,
    val personInCharge: String,
    val assistantEngineers: String = "",
    val groupName: String,
    val reminderEnabled: Boolean,
    val reminderLeadHours: Int = 24,
    val hasExplicitDeadline: Boolean = true
)

data class ImportedTodoRow(
    val title: String,
    val notes: String,
    val repeatRule: RepeatRule,
    val dueAtMillis: Long,
    val priority: ReminderPriority,
    val personInCharge: String,
    val assetCode: String,
    val hasExplicitDue: Boolean = true
)

data class ImportedWarehousePartRow(
    val partCode: String,
    val partName: String,
    val partDescription: String,
    val defaultUnit: String
)

data class CategoryDiffStats(
    val newCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedSkippedCount: Int = 0
) {
    val totalInWorkbook: Int get() = newCount + updatedCount + unchangedSkippedCount
    val totalApplied: Int get() = newCount + updatedCount
}

data class ExcelDiffSummary(
    val groups: CategoryDiffStats = CategoryDiffStats(),
    val machines: CategoryDiffStats = CategoryDiffStats(),
    val tasks: CategoryDiffStats = CategoryDiffStats(),
    val todos: CategoryDiffStats = CategoryDiffStats(),
    val warehouseParts: CategoryDiffStats = CategoryDiffStats(),
    val missingMachineCodesForTasks: List<String> = emptyList()
) {
    val totalNew: Int
        get() = groups.newCount + machines.newCount + tasks.newCount + todos.newCount + warehouseParts.newCount
    val totalUpdated: Int
        get() = groups.updatedCount + machines.updatedCount + tasks.updatedCount + todos.updatedCount + warehouseParts.updatedCount
    val totalSkippedDuplicates: Int
        get() = groups.unchangedSkippedCount + machines.unchangedSkippedCount + tasks.unchangedSkippedCount + todos.unchangedSkippedCount + warehouseParts.unchangedSkippedCount
    val totalChanges: Int
        get() = totalNew + totalUpdated
}

data class ExcelCommitResult(
    val summary: ExcelDiffSummary
) {
    fun formatSummaryMessage(): String =
        "Added ${summary.totalNew}, Updated ${summary.totalUpdated}, Skipped ${summary.totalSkippedDuplicates} duplicates"
}

data class ExcelImportPreview(
    val groups: List<ImportedGroupRow>,
    val machines: List<ImportedMachineRow>,
    val tasks: List<ImportedTaskRow>,
    val todos: List<ImportedTodoRow>,
    val warehouseParts: List<ImportedWarehousePartRow> = emptyList(),
    val warnings: List<String>,
    val diffSummary: ExcelDiffSummary? = null
) {
    val totalRecords: Int get() = groups.size + machines.size + tasks.size + todos.size + warehouseParts.size
}

object ExcelImportEngine {

    fun parseFromUri(context: Context, uri: Uri, defaultEngineer: String): ExcelImportPreview? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            parseXlsx(stream, defaultEngineer)
        }
    }

    fun parseXlsx(inputStream: InputStream, defaultEngineer: String = "Service Engineer"): ExcelImportPreview {
        val zipEntries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zipEntries[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        val sharedStrings = zipEntries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetTargets = resolveWorkbookSheets(zipEntries)

        val groups = mutableListOf<ImportedGroupRow>()
        val machines = mutableListOf<ImportedMachineRow>()
        val tasks = mutableListOf<ImportedTaskRow>()
        val todos = mutableListOf<ImportedTodoRow>()
        val warehouseParts = mutableListOf<ImportedWarehousePartRow>()
        val warnings = mutableListOf<String>()

        for ((sheetName, path) in sheetTargets) {
            val bytes = zipEntries[path] ?: continue
            val rows = parseWorksheetRows(bytes, sharedStrings)
            if (rows.size < 2) continue

            val headers = rows.first().map { it.trim().lowercase() }
            val lowerName = sheetName.lowercase()

            when {
                lowerName.contains("summary") || lowerName.contains("history") || lowerName.contains("spare parts used") -> {
                    // Exported summary/history sheets are not master-list import tables
                }
                lowerName.contains("group") -> parseGroupsSheet(rows, headers, groups)
                lowerName.contains("machine") && !lowerName.contains("task") ->
                    parseMachinesSheet(rows, headers, machines, warnings)
                lowerName.contains("task") || lowerName.contains("pm") || lowerName.contains("schedule") ->
                    parseTasksSheet(rows, headers, defaultEngineer, tasks, warnings)
                lowerName.contains("todo") || lowerName.contains("to-do") || lowerName.contains("reminder") ->
                    parseTodosSheet(rows, headers, defaultEngineer, todos)
                (lowerName.contains("warehouse") || lowerName.contains("inventory") || lowerName.contains("spare") || lowerName.contains("part")) &&
                    !lowerName.contains("used") ->
                    parseWarehousePartsSheet(rows, headers, warehouseParts)
                else -> {
                    // Infer by headers if user used a generic sheet name like Sheet1
                    when {
                        headers.any {
                            it.contains("part code") || it.contains("part id") ||
                                it.contains("part no") || it.contains("part number") ||
                                it.contains("spare part") || it.contains("sku")
                        } -> parseWarehousePartsSheet(rows, headers, warehouseParts)
                        headers.any { it.contains("cadence") || it.contains("task") || it.contains("deadline") } &&
                            !headers.any { it.contains("repeat") } ->
                            parseTasksSheet(rows, headers, defaultEngineer, tasks, warnings)
                        headers.any { it.contains("repeat") } ->
                            parseTodosSheet(rows, headers, defaultEngineer, todos)
                        headers.any { it.contains("asset") || it.contains("model") || it.contains("machine") } ->
                            parseMachinesSheet(rows, headers, machines, warnings)
                        headers.any { it.contains("group") } ->
                            parseGroupsSheet(rows, headers, groups)
                    }
                }
            }
        }

        if (groups.isEmpty() && machines.isEmpty() && tasks.isEmpty() && todos.isEmpty() && warehouseParts.isEmpty()) {
            warnings += "No recognizable MachinaLog columns found. Download the Excel Template for expected headers."
        }

        return ExcelImportPreview(
            groups = groups,
            machines = machines,
            tasks = tasks,
            todos = todos,
            warehouseParts = warehouseParts,
            warnings = warnings
        )
    }

    private data class DedupedList<T>(
        val items: List<T>,
        val intraFileDuplicatesSkipped: Int
    )

    private fun <T> deduplicateByKey(
        rows: List<T>,
        keySelector: (T) -> String
    ): DedupedList<T> {
        val ordered = LinkedHashMap<String, T>()
        var intraDuplicates = 0
        for (row in rows) {
            val key = keySelector(row)
            if (ordered.containsKey(key)) {
                intraDuplicates++
            }
            ordered[key] = row
        }
        return DedupedList(ordered.values.toList(), intraDuplicates)
    }

    fun computeDiffSummary(
        preview: ExcelImportPreview,
        existingGroups: List<CustomGroupEntity>,
        existingMachines: List<MachineEntity>,
        existingTasks: List<MaintenanceTaskEntity>,
        existingTodos: List<TodoReminderEntity>,
        existingWarehouseParts: List<WarehousePartEntity>,
        replaceExisting: Boolean = false
    ): ExcelDiffSummary {
        if (replaceExisting) {
            val dGroups = deduplicateByKey(preview.groups) { it.name.trim().lowercase() }
            val dMachines = deduplicateByKey(preview.machines) { it.assetCode.trim().uppercase() }
            val machineCodes = dMachines.items.map { it.assetCode.trim().uppercase() }.toSet()
            val machineNames = dMachines.items.associate { it.name.trim().lowercase() to it.assetCode.trim().uppercase() }
            val validTasks = preview.tasks.filter {
                val c = it.assetCode.trim().uppercase()
                c in machineCodes || it.assetCode.trim().lowercase() in machineNames
            }
            val missingCodes = preview.tasks
                .filter {
                    val c = it.assetCode.trim().uppercase()
                    c !in machineCodes && it.assetCode.trim().lowercase() !in machineNames
                }
                .map { it.assetCode.trim().uppercase() }
                .distinct()
            val dTasks = deduplicateByKey(validTasks) {
                val resolved = if (it.assetCode.trim().uppercase() in machineCodes) {
                    it.assetCode.trim().uppercase()
                } else {
                    machineNames[it.assetCode.trim().lowercase()] ?: it.assetCode.trim().uppercase()
                }
                "$resolved|${it.title.trim().lowercase()}|${it.cadence.name}"
            }
            val dTodos = deduplicateByKey(preview.todos) {
                "${it.assetCode.trim().uppercase()}|${it.title.trim().lowercase()}|${it.repeatRule.name}"
            }
            val dParts = deduplicateByKey(preview.warehouseParts) { it.partCode.trim().uppercase() }

            return ExcelDiffSummary(
                groups = CategoryDiffStats(newCount = dGroups.items.size, unchangedSkippedCount = dGroups.intraFileDuplicatesSkipped),
                machines = CategoryDiffStats(newCount = dMachines.items.size, unchangedSkippedCount = dMachines.intraFileDuplicatesSkipped),
                tasks = CategoryDiffStats(newCount = dTasks.items.size, unchangedSkippedCount = dTasks.intraFileDuplicatesSkipped),
                todos = CategoryDiffStats(newCount = dTodos.items.size, unchangedSkippedCount = dTodos.intraFileDuplicatesSkipped),
                warehouseParts = CategoryDiffStats(newCount = dParts.items.size, unchangedSkippedCount = dParts.intraFileDuplicatesSkipped),
                missingMachineCodesForTasks = missingCodes
            )
        }

        // 1. Groups diff
        val existingGroupByLower = existingGroups.associateBy { it.name.trim().lowercase() }
        val existingGroupNameById = existingGroups.associate { it.id to it.name.trim().lowercase() }
        val dGroups = deduplicateByKey(preview.groups) { it.name.trim().lowercase() }
        var gNew = 0
        var gUpd = 0
        var gSkip = dGroups.intraFileDuplicatesSkipped
        for (g in dGroups.items) {
            val ex = existingGroupByLower[g.name.trim().lowercase()]
            if (ex == null) {
                gNew++
            } else if (ex.scope != g.scope || !ex.colorKey.equals(g.colorKey, ignoreCase = true)) {
                gUpd++
            } else {
                gSkip++
            }
        }

        // 2. Machines diff
        val existingMachineByCode = existingMachines.associateBy { it.assetCode.trim().uppercase() }
        val existingMachineByName = existingMachines.associateBy { it.name.trim().lowercase() }
        val dMachines = deduplicateByKey(preview.machines) { it.assetCode.trim().uppercase() }
        val incomingMachineCodeSet = dMachines.items.map { it.assetCode.trim().uppercase() }.toSet()
        val incomingMachineNameToCode = dMachines.items.associate { it.name.trim().lowercase() to it.assetCode.trim().uppercase() }
        var mNew = 0
        var mUpd = 0
        var mSkip = dMachines.intraFileDuplicatesSkipped
        for (m in dMachines.items) {
            val code = m.assetCode.trim().uppercase()
            val ex = existingMachineByCode[code]
            if (ex == null) {
                mNew++
            } else {
                val targetGroupLower = m.groupName.trim().lowercase().takeIf { it.isNotEmpty() && it != "ungrouped" }
                val exGroupLower = ex.groupId?.let { existingGroupNameById[it] }
                val groupChanged = targetGroupLower != null && targetGroupLower != exGroupLower
                val changed = groupChanged ||
                    m.name.trim() != ex.name.trim() ||
                    m.locationZone.trim() != ex.locationZone.trim() ||
                    m.modelInfo.trim() != ex.modelInfo.trim() ||
                    m.status != ex.status ||
                    m.notes.trim() != ex.notes.trim()
                if (changed) mUpd++ else mSkip++
            }
        }

        // Helper to resolve Asset Code or Machine Name -> canonical Asset Code
        fun resolveTaskMachineCode(rawMachineRef: String): Pair<String?, MachineEntity?> {
            val upper = rawMachineRef.trim().uppercase()
            val lower = rawMachineRef.trim().lowercase()
            existingMachineByCode[upper]?.let { return upper to it }
            existingMachineByName[lower]?.let { return it.assetCode.trim().uppercase() to it }
            if (upper in incomingMachineCodeSet) return upper to null
            incomingMachineNameToCode[lower]?.let { return it to null }
            return null to null
        }

        // 3. Maintenance Tasks diff
        val existingTaskByKey = existingTasks.associateBy { t ->
            "${t.machineId}|${t.title.trim().lowercase()}|${t.cadence.name}"
        }
        val missingMachineCodes = mutableSetOf<String>()
        val resolvedTasks = preview.tasks.mapNotNull { t ->
            val (code, existingMachine) = resolveTaskMachineCode(t.assetCode)
            if (code == null) {
                if (t.assetCode.isNotBlank()) missingMachineCodes += t.assetCode.trim().uppercase()
                null
            } else {
                Triple(code, existingMachine, t)
            }
        }
        val dTasks = deduplicateByKey(resolvedTasks) { (code, _, t) ->
            "$code|${t.title.trim().lowercase()}|${t.cadence.name}"
        }
        var tNew = 0
        var tUpd = 0
        var tSkip = dTasks.intraFileDuplicatesSkipped
        for ((_, existingMachine, t) in dTasks.items) {
            if (existingMachine == null) {
                tNew++
            } else {
                val ex = existingTaskByKey["${existingMachine.id}|${t.title.trim().lowercase()}|${t.cadence.name}"]
                if (ex == null) {
                    tNew++
                } else {
                    val targetGroupLower = t.groupName.trim().lowercase().takeIf { it.isNotEmpty() && it != "ungrouped" }
                    val exGroupLower = ex.groupId?.let { existingGroupNameById[it] }
                    val groupChanged = targetGroupLower != null && targetGroupLower != exGroupLower
                    val deadlineChanged = t.hasExplicitDeadline &&
                        kotlin.math.abs(t.deadlineMillis - ex.deadlineMillis) > 60_000L
                    val changed = groupChanged ||
                        deadlineChanged ||
                        t.description.trim() != ex.description.trim() ||
                        t.estimatedMinutes.coerceAtLeast(1) != ex.estimatedMinutes ||
                        t.personInCharge.trim() != ex.personInCharge.trim() ||
                        t.assistantEngineers.trim() != ex.assistantEngineers.trim() ||
                        t.reminderEnabled != ex.reminderEnabled ||
                        t.reminderLeadHours != ex.reminderLeadHours
                    if (changed) tUpd++ else tSkip++
                }
            }
        }

        // 4. To-Do Reminders diff
        val existingMachineCodeById = existingMachines.associate { it.id to it.assetCode.trim().uppercase() }
        val existingTodoByKey = existingTodos.associateBy { td ->
            val mCode = td.machineId?.let { existingMachineCodeById[it] }.orEmpty()
            "$mCode|${td.title.trim().lowercase()}|${td.repeatRule.name}"
        }
        val dTodos = deduplicateByKey(preview.todos) { td ->
            val (code, _) = if (td.assetCode.isBlank()) ("" to null) else resolveTaskMachineCode(td.assetCode)
            "${code.orEmpty()}|${td.title.trim().lowercase()}|${td.repeatRule.name}"
        }
        var tdNew = 0
        var tdUpd = 0
        var tdSkip = dTodos.intraFileDuplicatesSkipped
        for (td in dTodos.items) {
            val (code, _) = if (td.assetCode.isBlank()) ("" to null) else resolveTaskMachineCode(td.assetCode)
            val key = "${code.orEmpty()}|${td.title.trim().lowercase()}|${td.repeatRule.name}"
            val ex = existingTodoByKey[key]
            if (ex == null) {
                tdNew++
            } else {
                val dueChanged = td.hasExplicitDue &&
                    kotlin.math.abs(td.dueAtMillis - ex.dueAtMillis) > 60_000L
                val changed = dueChanged ||
                    td.notes.trim() != ex.notes.trim() ||
                    td.priority != ex.priority ||
                    td.personInCharge.trim() != ex.personInCharge.trim()
                if (changed) tdUpd++ else tdSkip++
            }
        }

        // 5. Warehouse Spare Parts diff
        val existingPartByCode = existingWarehouseParts.associateBy { it.partCode.trim().uppercase() }
        val dParts = deduplicateByKey(preview.warehouseParts) { it.partCode.trim().uppercase() }
        var wpNew = 0
        var wpUpd = 0
        var wpSkip = dParts.intraFileDuplicatesSkipped
        for (wp in dParts.items) {
            val ex = existingPartByCode[wp.partCode.trim().uppercase()]
            if (ex == null) {
                wpNew++
            } else {
                val targetName = wp.partName.trim().ifEmpty { ex.partName.trim() }
                val targetDesc = wp.partDescription.trim().ifEmpty { ex.partDescription.trim() }
                val targetUnit = wp.defaultUnit.trim().ifEmpty { ex.defaultUnit.trim() }
                val changed = targetName != ex.partName.trim() ||
                    targetDesc != ex.partDescription.trim() ||
                    targetUnit != ex.defaultUnit.trim()
                if (changed) wpUpd++ else wpSkip++
            }
        }

        return ExcelDiffSummary(
            groups = CategoryDiffStats(gNew, gUpd, gSkip),
            machines = CategoryDiffStats(mNew, mUpd, mSkip),
            tasks = CategoryDiffStats(tNew, tUpd, tSkip),
            todos = CategoryDiffStats(tdNew, tdUpd, tdSkip),
            warehouseParts = CategoryDiffStats(wpNew, wpUpd, wpSkip),
            missingMachineCodesForTasks = missingMachineCodes.sorted()
        )
    }

    suspend fun commitImport(
        dao: MachinaDao,
        prefs: AppPreferences,
        preview: ExcelImportPreview,
        replaceExisting: Boolean
    ): ExcelCommitResult {
        if (replaceExisting) {
            dao.clearAllData()
        }

        val now = System.currentTimeMillis()
        val existingGroupsList = dao.getAllGroupsSync()
        val existingGroups = existingGroupsList.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val groupIdByLowerName = existingGroups.mapValues { it.value.id }.toMutableMap()
        val groupNameById = existingGroupsList.associate { it.id to it.name.trim().lowercase() }.toMutableMap()

        suspend fun ensureGroupId(name: String): Long? {
            val clean = name.trim()
            if (clean.isEmpty() || clean.equals("Ungrouped", ignoreCase = true)) return null
            val key = clean.lowercase()
            groupIdByLowerName[key]?.let { return it }
            val newGroup = CustomGroupEntity(
                name = clean,
                scope = GroupScope.ALL,
                colorKey = "COBALT",
                updatedAtMillis = now
            )
            val id = dao.insertGroup(newGroup)
            existingGroups[key] = newGroup.copy(id = id)
            groupIdByLowerName[key] = id
            groupNameById[id] = key
            return id
        }

        // 1. Custom Groups
        val dGroups = deduplicateByKey(preview.groups) { it.name.trim().lowercase() }
        var gNew = 0
        var gUpd = 0
        var gSkip = dGroups.intraFileDuplicatesSkipped
        for (g in dGroups.items) {
            val key = g.name.trim().lowercase()
            val existing = existingGroups[key]
            if (existing == null) {
                val entity = CustomGroupEntity(
                    name = g.name.trim(),
                    scope = g.scope,
                    colorKey = g.colorKey,
                    updatedAtMillis = now
                )
                val id = dao.insertGroup(entity)
                existingGroups[key] = entity.copy(id = id)
                groupIdByLowerName[key] = id
                groupNameById[id] = key
                gNew++
            } else if (existing.scope != g.scope || !existing.colorKey.equals(g.colorKey, ignoreCase = true)) {
                val updated = existing.copy(scope = g.scope, colorKey = g.colorKey, updatedAtMillis = now)
                dao.updateGroup(updated)
                existingGroups[key] = updated
                gUpd++
            } else {
                gSkip++
            }
        }

        // 2. Machines
        val existingMachinesList = dao.getAllMachinesSync()
        val existingMachines = existingMachinesList.associateBy { it.assetCode.trim().uppercase() }.toMutableMap()
        val machineIdByCode = existingMachines.mapValues { it.value.id }.toMutableMap()
        val machineIdByName = existingMachinesList.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
        val machineCodeById = existingMachinesList.associate { it.id to it.assetCode.trim().uppercase() }.toMutableMap()

        val dMachines = deduplicateByKey(preview.machines) { it.assetCode.trim().uppercase() }
        var mNew = 0
        var mUpd = 0
        var mSkip = dMachines.intraFileDuplicatesSkipped
        for (m in dMachines.items) {
            val code = m.assetCode.trim().uppercase()
            val existing = existingMachines[code]
            if (existing == null) {
                val gid = ensureGroupId(m.groupName)
                val entity = MachineEntity(
                    groupId = gid,
                    assetCode = code,
                    name = m.name.trim(),
                    locationZone = m.locationZone.trim(),
                    modelInfo = m.modelInfo.trim(),
                    status = m.status,
                    notes = m.notes.trim(),
                    updatedAtMillis = now
                )
                val id = dao.insertMachine(entity)
                val saved = entity.copy(id = id)
                existingMachines[code] = saved
                machineIdByCode[code] = id
                machineIdByName[saved.name.trim().lowercase()] = id
                machineCodeById[id] = code
                mNew++
            } else {
                val targetGroupLower = m.groupName.trim().lowercase().takeIf { it.isNotEmpty() && it != "ungrouped" }
                val exGroupLower = existing.groupId?.let { groupNameById[it] }
                val groupChanged = targetGroupLower != null && targetGroupLower != exGroupLower
                val changed = groupChanged ||
                    m.name.trim() != existing.name.trim() ||
                    m.locationZone.trim() != existing.locationZone.trim() ||
                    m.modelInfo.trim() != existing.modelInfo.trim() ||
                    m.status != existing.status ||
                    m.notes.trim() != existing.notes.trim()

                if (changed) {
                    val gid = if (m.groupName.isNotBlank()) ensureGroupId(m.groupName) else existing.groupId
                    val updated = existing.copy(
                        groupId = gid ?: existing.groupId,
                        name = m.name.trim(),
                        locationZone = m.locationZone.trim(),
                        modelInfo = m.modelInfo.trim(),
                        status = m.status,
                        notes = m.notes.trim(),
                        updatedAtMillis = now
                    )
                    dao.updateMachine(updated)
                    existingMachines[code] = updated
                    machineIdByName[updated.name.trim().lowercase()] = updated.id
                    mUpd++
                } else {
                    mSkip++
                }
            }
        }

        // 3. Maintenance Tasks
        val existingTasksByKey = dao.getAllTasksSync().associateBy { t ->
            "${t.machineId}|${t.title.trim().lowercase()}|${t.cadence.name}"
        }.toMutableMap()

        val missingMachineCodes = mutableSetOf<String>()
        val validTasks = preview.tasks.mapNotNull { t ->
            val code = t.assetCode.trim().uppercase()
            val mid = machineIdByCode[code] ?: machineIdByName[t.assetCode.trim().lowercase()]
            if (mid == null) {
                if (code.isNotEmpty()) missingMachineCodes += code
                null
            } else {
                val canonicalCode = machineCodeById[mid] ?: code
                Triple(canonicalCode, mid, t)
            }
        }
        val dTasks = deduplicateByKey(validTasks) { (code, _, t) ->
            "$code|${t.title.trim().lowercase()}|${t.cadence.name}"
        }
        var tNew = 0
        var tUpd = 0
        var tSkip = dTasks.intraFileDuplicatesSkipped
        for ((_, machineId, t) in dTasks.items) {
            val taskKey = "$machineId|${t.title.trim().lowercase()}|${t.cadence.name}"
            val existing = existingTasksByKey[taskKey]
            if (existing == null) {
                val gid = ensureGroupId(t.groupName)
                val entity = MaintenanceTaskEntity(
                    machineId = machineId,
                    groupId = gid,
                    title = t.title.trim(),
                    description = t.description.trim(),
                    cadence = t.cadence,
                    deadlineMillis = t.deadlineMillis,
                    estimatedMinutes = t.estimatedMinutes.coerceAtLeast(1),
                    personInCharge = t.personInCharge.trim(),
                    assistantEngineers = t.assistantEngineers.trim(),
                    reminderEnabled = t.reminderEnabled,
                    reminderLeadHours = t.reminderLeadHours.coerceAtLeast(1),
                    updatedAtMillis = now
                )
                val id = dao.insertTask(entity)
                existingTasksByKey[taskKey] = entity.copy(id = id)
                tNew++
            } else {
                val targetGroupLower = t.groupName.trim().lowercase().takeIf { it.isNotEmpty() && it != "ungrouped" }
                val exGroupLower = existing.groupId?.let { groupNameById[it] }
                val groupChanged = targetGroupLower != null && targetGroupLower != exGroupLower
                val deadlineChanged = t.hasExplicitDeadline &&
                    kotlin.math.abs(t.deadlineMillis - existing.deadlineMillis) > 60_000L
                val changed = groupChanged ||
                    deadlineChanged ||
                    t.description.trim() != existing.description.trim() ||
                    t.estimatedMinutes.coerceAtLeast(1) != existing.estimatedMinutes ||
                    t.personInCharge.trim() != existing.personInCharge.trim() ||
                    t.assistantEngineers.trim() != existing.assistantEngineers.trim() ||
                    t.reminderEnabled != existing.reminderEnabled ||
                    t.reminderLeadHours != existing.reminderLeadHours

                if (changed) {
                    val gid = if (t.groupName.isNotBlank()) ensureGroupId(t.groupName) else existing.groupId
                    val updated = existing.copy(
                        groupId = gid ?: existing.groupId,
                        description = t.description.trim(),
                        deadlineMillis = if (t.hasExplicitDeadline) t.deadlineMillis else existing.deadlineMillis,
                        estimatedMinutes = t.estimatedMinutes.coerceAtLeast(1),
                        personInCharge = t.personInCharge.trim(),
                        assistantEngineers = t.assistantEngineers.trim(),
                        reminderEnabled = t.reminderEnabled,
                        reminderLeadHours = t.reminderLeadHours.coerceAtLeast(1),
                        updatedAtMillis = now
                    )
                    dao.updateTask(updated)
                    existingTasksByKey[taskKey] = updated
                    tUpd++
                } else {
                    tSkip++
                }
            }
        }

        // 4. To-Do Reminders
        val existingTodoByKey = dao.getAllTodosSync().associateBy { td ->
            val mCode = td.machineId?.let { machineCodeById[it] }.orEmpty()
            "$mCode|${td.title.trim().lowercase()}|${td.repeatRule.name}"
        }.toMutableMap()
        val dTodos = deduplicateByKey(preview.todos) { td ->
            val mid = td.assetCode.trim().takeIf { it.isNotEmpty() }?.let {
                machineIdByCode[it.uppercase()] ?: machineIdByName[it.lowercase()]
            }
            val canonicalCode = mid?.let { machineCodeById[it] } ?: td.assetCode.trim().uppercase()
            "$canonicalCode|${td.title.trim().lowercase()}|${td.repeatRule.name}"
        }
        var tdNew = 0
        var tdUpd = 0
        var tdSkip = dTodos.intraFileDuplicatesSkipped
        for (td in dTodos.items) {
            val mid = td.assetCode.trim().takeIf { it.isNotEmpty() }?.let {
                machineIdByCode[it.uppercase()] ?: machineIdByName[it.lowercase()]
            }
            val canonicalCode = mid?.let { machineCodeById[it] } ?: td.assetCode.trim().uppercase()
            val todoKey = "$canonicalCode|${td.title.trim().lowercase()}|${td.repeatRule.name}"
            val existing = existingTodoByKey[todoKey]
            if (existing == null) {
                val entity = TodoReminderEntity(
                    title = td.title.trim(),
                    notes = td.notes.trim(),
                    machineId = mid,
                    personInCharge = td.personInCharge.trim(),
                    repeatRule = td.repeatRule,
                    dueAtMillis = td.dueAtMillis,
                    priority = td.priority,
                    reminderEnabled = true,
                    updatedAtMillis = now
                )
                val id = dao.insertTodo(entity)
                existingTodoByKey[todoKey] = entity.copy(id = id)
                tdNew++
            } else {
                val dueChanged = td.hasExplicitDue &&
                    kotlin.math.abs(td.dueAtMillis - existing.dueAtMillis) > 60_000L
                val changed = dueChanged ||
                    td.notes.trim() != existing.notes.trim() ||
                    td.priority != existing.priority ||
                    td.personInCharge.trim() != existing.personInCharge.trim()
                if (changed) {
                    val updated = existing.copy(
                        notes = td.notes.trim(),
                        dueAtMillis = if (td.hasExplicitDue) td.dueAtMillis else existing.dueAtMillis,
                        priority = td.priority,
                        personInCharge = td.personInCharge.trim(),
                        updatedAtMillis = now
                    )
                    dao.updateTodo(updated)
                    existingTodoByKey[todoKey] = updated
                    tdUpd++
                } else {
                    tdSkip++
                }
            }
        }

        // 5. Warehouse Spare Parts
        val existingWarehouse = dao.getAllWarehousePartsSync().associateBy { it.partCode.trim().uppercase() }.toMutableMap()
        val dParts = deduplicateByKey(preview.warehouseParts) { it.partCode.trim().uppercase() }
        var wpNew = 0
        var wpUpd = 0
        var wpSkip = dParts.intraFileDuplicatesSkipped
        for (wp in dParts.items) {
            val code = wp.partCode.trim().uppercase()
            val existing = existingWarehouse[code]
            if (existing == null) {
                val inserted = WarehousePartEntity(
                    partCode = code,
                    partName = wp.partName.trim(),
                    partDescription = wp.partDescription.trim(),
                    defaultUnit = wp.defaultUnit.trim().ifEmpty { "pcs" },
                    updatedAtMillis = now
                )
                val id = dao.insertWarehousePart(inserted)
                existingWarehouse[code] = inserted.copy(id = id)
                wpNew++
            } else {
                val targetName = wp.partName.trim().ifEmpty { existing.partName.trim() }
                val targetDesc = wp.partDescription.trim().ifEmpty { existing.partDescription.trim() }
                val targetUnit = wp.defaultUnit.trim().ifEmpty { existing.defaultUnit.trim() }
                val changed = targetName != existing.partName.trim() ||
                    targetDesc != existing.partDescription.trim() ||
                    targetUnit != existing.defaultUnit.trim()
                if (changed) {
                    val updated = existing.copy(
                        partName = targetName,
                        partDescription = targetDesc,
                        defaultUnit = targetUnit,
                        updatedAtMillis = now
                    )
                    dao.updateWarehousePart(updated)
                    existingWarehouse[code] = updated
                    wpUpd++
                } else {
                    wpSkip++
                }
            }
        }

        // Auto-register any newly seen engineers from imported tasks/todos so strict PIC selection works seamlessly
        val existingEngineers = dao.getAllEngineersSync().map { it.name.trim().lowercase() }.toMutableSet()
        val importedNames = buildSet {
            dTasks.items.forEach { (_, _, t) ->
                if (t.personInCharge.isNotBlank()) add(t.personInCharge.trim())
                parseEngineerList(t.assistantEngineers).forEach { add(it) }
            }
            dTodos.items.forEach { td -> if (td.personInCharge.isNotBlank()) add(td.personInCharge.trim()) }
        }
        for (name in importedNames) {
            if (name.lowercase() !in existingEngineers) {
                dao.insertEngineer(EngineerEntity(name = name, updatedAtMillis = now))
                existingEngineers += name.lowercase()
            }
        }

        prefs.setSampleDataSeeded(true)

        return ExcelCommitResult(
            summary = ExcelDiffSummary(
                groups = CategoryDiffStats(gNew, gUpd, gSkip),
                machines = CategoryDiffStats(mNew, mUpd, mSkip),
                tasks = CategoryDiffStats(tNew, tUpd, tSkip),
                todos = CategoryDiffStats(tdNew, tdUpd, tdSkip),
                warehouseParts = CategoryDiffStats(wpNew, wpUpd, wpSkip),
                missingMachineCodesForTasks = missingMachineCodes.sorted()
            )
        )
    }

    fun writeImportTemplateXlsx(out: OutputStream) {
        val groupRows = listOf(
            listOf("Group Name", "Scope (ALL / MACHINE / TASK)", "Color (COBALT / TEAL / AMBER / EMERALD / CRIMSON / VIOLET)"),
            listOf("Assembly Line B", "ALL", "COBALT"),
            listOf("Boiler & Cooling", "ALL", "AMBER")
        )
        val machineRows = listOf(
            listOf("Asset Code", "Machine Name", "Location Zone", "Model & SN", "Status (OPERATIONAL / MAINTENANCE_DUE / MALFUNCTION)", "Custom Group", "Notes"),
            listOf("MCH-601", "Laser Cutter Trumpf 3030", "Bay 3", "TruLaser 3030 SN-4412", "OPERATIONAL", "Assembly Line B", "Check assist gas pressure daily"),
            listOf("MCH-602", "Chiller Unit Carrier 30XA", "Utility Roof", "Carrier 30XA SN-8810", "OPERATIONAL", "Boiler & Cooling", "Glycol 30% mix")
        )
        val taskRows = listOf(
            listOf("Asset Code", "Task Title", "Description", "Cadence (ONCE / DAILY / WEEKLY / MONTHLY / YEARLY)", "Deadline (yyyy-MM-dd HH:mm)", "Est. Minutes", "Person In Charge", "Assistant Engineers", "Custom Group", "Reminder (YES/NO)", "Reminder Lead Hours"),
            listOf("MCH-601", "Optics Lens & Nozzle Inspection", "Clean protective glass and inspect copper nozzle tip", "DAILY", "2026-12-01 08:00", "20", "Elena Vance", "Marcus Chen", "Assembly Line B", "YES", "2"),
            listOf("MCH-602", "Condenser Coil Wash", "Wash fin coils and verify approach temperature", "MONTHLY", "2026-12-15 10:00", "60", "Marcus Chen", "", "Boiler & Cooling", "YES", "48")
        )
        val todoRows = listOf(
            listOf("Title", "Notes", "Repeat Rule (ONCE / DAILY / WEEKLY / MONTHLY / YEARLY)", "Trigger Date (yyyy-MM-dd HH:mm)", "Priority (NORMAL / HIGH / URGENT)", "Person In Charge", "Asset Code (Optional)"),
            listOf("Restock Laser Nozzle Tips 1.5mm", "Check min bin count in warehouse", "ONCE", "2026-12-02 09:00", "HIGH", "Elena Vance", "MCH-601"),
            listOf("Weekly Chiller Log Audit", "Review glycol pressure trends", "WEEKLY", "2026-12-05 08:30", "NORMAL", "Marcus Chen", "MCH-602")
        )
        val warehousePartRows = listOf(
            listOf("Part Code / ID", "Part Name", "Description", "Default Unit"),
            listOf("TR-NZL-15", "Copper Laser Nozzle 1.5mm", "Single-layer chrome plated cutting nozzle Dia 1.5mm", "pcs"),
            listOf("CAR-FLT-30XA", "Chiller Oil Filter Element", "Screw compressor internal oil filter cartridge", "pcs")
        )

        ZipOutputStream(out).use { zip ->
            writeZip(zip, "[Content_Types].xml", templateContentTypesXml())
            writeZip(zip, "_rels/.rels", templateRootRelsXml())
            writeZip(zip, "xl/workbook.xml", templateWorkbookXml())
            writeZip(zip, "xl/_rels/workbook.xml.rels", templateWorkbookRelsXml())
            writeZip(zip, "xl/styles.xml", templateStylesXml())
            writeZip(zip, "xl/worksheets/sheet1.xml", buildSimpleSheetXml(groupRows))
            writeZip(zip, "xl/worksheets/sheet2.xml", buildSimpleSheetXml(machineRows))
            writeZip(zip, "xl/worksheets/sheet3.xml", buildSimpleSheetXml(taskRows))
            writeZip(zip, "xl/worksheets/sheet4.xml", buildSimpleSheetXml(todoRows))
            writeZip(zip, "xl/worksheets/sheet5.xml", buildSimpleSheetXml(warehousePartRows))
        }
    }

    private fun parseGroupsSheet(
        rows: List<List<String>>,
        headers: List<String>,
        out: MutableList<ImportedGroupRow>
    ) {
        val nameCol = findCol(headers, "group name", "name", "group").coerceAtLeast(0)
        val scopeCol = findCol(headers, "scope")
        val colorCol = findCol(headers, "color")

        rows.drop(1).forEach { row ->
            val name = row.getOrNull(nameCol)?.trim().orEmpty()
            if (name.isEmpty()) return@forEach
            val scopeRaw = row.getOrNull(scopeCol)?.trim()?.uppercase().orEmpty()
            val scope = runCatching { GroupScope.valueOf(scopeRaw) }.getOrDefault(GroupScope.ALL)
            val color = row.getOrNull(colorCol)?.trim()?.uppercase()?.ifEmpty { "COBALT" } ?: "COBALT"
            out += ImportedGroupRow(name = name, scope = scope, colorKey = color)
        }
    }

    private fun parseMachinesSheet(
        rows: List<List<String>>,
        headers: List<String>,
        out: MutableList<ImportedMachineRow>,
        warnings: MutableList<String>
    ) {
        val codeCol = findCol(headers, "asset code", "asset", "machine id", "code")
        val nameCol = findCol(headers, "machine name", "name", "machine")
        val zoneCol = findCol(headers, "location zone", "location", "zone", "bay", "line")
        val modelCol = findCol(headers, "model", "serial", "sn")
        val statusCol = findCol(headers, "status")
        val groupCol = findCol(headers, "custom group", "group")
        val notesCol = findCol(headers, "notes", "note", "remark")

        if (codeCol < 0 && nameCol < 0) {
            warnings += "Machines sheet is missing 'Asset Code' or 'Machine Name' columns."
            return
        }

        rows.drop(1).forEachIndexed { idx, row ->
            val rawName = row.getOrNull(if (nameCol >= 0) nameCol else codeCol)?.trim().orEmpty()
            val rawCode = row.getOrNull(if (codeCol >= 0) codeCol else nameCol)?.trim().orEmpty()
            if (rawName.isEmpty() && rawCode.isEmpty()) return@forEachIndexed
            val assetCode = (rawCode.ifEmpty { "MCH-${100 + idx}" }).uppercase()
            val machineName = rawName.ifEmpty { assetCode }
            val statusStr = row.getOrNull(statusCol)?.trim()?.uppercase().orEmpty()
            val status = when {
                statusStr.contains("MALFUNCTION") || statusStr.contains("DOWN") -> MachineStatus.MALFUNCTION
                statusStr.contains("DUE") -> MachineStatus.MAINTENANCE_DUE
                else -> MachineStatus.OPERATIONAL
            }
            out += ImportedMachineRow(
                assetCode = assetCode,
                name = machineName,
                locationZone = row.getOrNull(zoneCol)?.trim()?.ifEmpty { "Main Floor" } ?: "Main Floor",
                modelInfo = row.getOrNull(modelCol)?.trim()?.ifEmpty { "Standard Unit" } ?: "Standard Unit",
                status = status,
                groupName = row.getOrNull(groupCol)?.trim().orEmpty(),
                notes = row.getOrNull(notesCol)?.trim().orEmpty()
            )
        }
    }

    private fun parseTasksSheet(
        rows: List<List<String>>,
        headers: List<String>,
        defaultEngineer: String,
        out: MutableList<ImportedTaskRow>,
        warnings: MutableList<String>
    ) {
        val codeCol = findCol(headers, "asset code", "asset", "machine code", "machine id", "machine", "equipment")
        val titleCol = findCol(headers, "task title", "title", "task", "activity")
        val descCol = findCol(headers, "description", "checklist", "notes")
        val cadenceCol = findCol(headers, "cadence", "frequency", "period")
        val deadlineCol = findCol(headers, "deadline", "due date", "due")
        val estCol = findCol(headers, "est. minutes", "est", "minutes", "time")
        val picCol = findCol(headers, "person in charge", "pic", "engineer", "technician", "assigned")
        val assistantCol = findCol(headers, "assistant", "helpers", "co-engineer")
        val groupCol = findCol(headers, "custom group", "group")
        val remCol = findCol(headers, "reminder")
        val leadHoursCol = findCol(headers, "reminder lead hours", "lead hours", "lead time")

        if (titleCol < 0 || codeCol < 0) {
            warnings += "Tasks sheet requires 'Asset Code' (or 'Machine') and 'Task Title' columns."
            return
        }

        val defaultDeadline = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8)
        rows.drop(1).forEach { row ->
            val assetCode = row.getOrNull(codeCol)?.trim().orEmpty()
            val title = row.getOrNull(titleCol)?.trim().orEmpty()
            if (assetCode.isEmpty() || title.isEmpty()) return@forEach

            val cadRaw = row.getOrNull(cadenceCol)?.trim()?.uppercase().orEmpty()
            val cadence = when {
                cadRaw.contains("ONCE") || cadRaw.contains("ONE") || cadRaw.contains("SINGLE") -> Cadence.ONCE
                cadRaw.contains("YEAR") || cadRaw.contains("ANNUAL") -> Cadence.YEARLY
                cadRaw.contains("MONTH") -> Cadence.MONTHLY
                cadRaw.contains("WEEK") -> Cadence.WEEKLY
                else -> Cadence.DAILY
            }

            val parsedDeadline = parseFlexibleDateMillis(row.getOrNull(deadlineCol))
            val estMin = row.getOrNull(estCol)?.trim()?.toDoubleOrNull()?.toInt()?.coerceAtLeast(1) ?: 30
            val pic = row.getOrNull(picCol)?.trim()?.ifEmpty { defaultEngineer } ?: defaultEngineer
            val assistants = row.getOrNull(assistantCol)?.trim().orEmpty()
            val remStr = row.getOrNull(remCol)?.trim()?.uppercase().orEmpty()
            val remOn = !(remStr == "NO" || remStr == "FALSE" || remStr == "OFF" || remStr == "0")
            val leadHours = row.getOrNull(leadHoursCol)?.trim()?.toDoubleOrNull()?.toInt()?.coerceAtLeast(1) ?: 24

            out += ImportedTaskRow(
                assetCode = assetCode.uppercase(),
                title = title,
                description = row.getOrNull(descCol)?.trim().orEmpty(),
                cadence = cadence,
                deadlineMillis = parsedDeadline ?: defaultDeadline,
                estimatedMinutes = estMin,
                personInCharge = pic,
                assistantEngineers = assistants,
                groupName = row.getOrNull(groupCol)?.trim().orEmpty(),
                reminderEnabled = remOn,
                reminderLeadHours = leadHours,
                hasExplicitDeadline = parsedDeadline != null
            )
        }
    }

    private fun parseTodosSheet(
        rows: List<List<String>>,
        headers: List<String>,
        defaultEngineer: String,
        out: MutableList<ImportedTodoRow>
    ) {
        val titleCol = findCol(headers, "title", "reminder")
        val notesCol = findCol(headers, "notes", "detail")
        val repeatCol = findCol(headers, "repeat", "frequency")
        val dueCol = findCol(headers, "trigger", "due", "date")
        val prioCol = findCol(headers, "priority")
        val picCol = findCol(headers, "person in charge", "pic", "engineer")
        val codeCol = findCol(headers, "asset code", "asset", "machine")

        if (titleCol < 0) return

        val defaultDue = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(4)
        rows.drop(1).forEach { row ->
            val title = row.getOrNull(titleCol)?.trim().orEmpty()
            if (title.isEmpty()) return@forEach

            val repRaw = row.getOrNull(repeatCol)?.trim()?.uppercase().orEmpty()
            val repeatRule = when {
                repRaw.contains("DAY") || repRaw.contains("DAILY") -> RepeatRule.DAILY
                repRaw.contains("WEEK") -> RepeatRule.WEEKLY
                repRaw.contains("MONTH") -> RepeatRule.MONTHLY
                repRaw.contains("YEAR") -> RepeatRule.YEARLY
                else -> RepeatRule.ONCE
            }
            val prioRaw = row.getOrNull(prioCol)?.trim()?.uppercase().orEmpty()
            val priority = when {
                prioRaw.contains("URGENT") -> ReminderPriority.URGENT
                prioRaw.contains("HIGH") -> ReminderPriority.HIGH
                else -> ReminderPriority.NORMAL
            }
            val parsedDue = parseFlexibleDateMillis(row.getOrNull(dueCol))
            out += ImportedTodoRow(
                title = title,
                notes = row.getOrNull(notesCol)?.trim().orEmpty(),
                repeatRule = repeatRule,
                dueAtMillis = parsedDue ?: defaultDue,
                priority = priority,
                personInCharge = row.getOrNull(picCol)?.trim()?.ifEmpty { defaultEngineer } ?: defaultEngineer,
                assetCode = row.getOrNull(codeCol)?.trim().orEmpty(),
                hasExplicitDue = parsedDue != null
            )
        }
    }

    private fun parseWarehousePartsSheet(
        rows: List<List<String>>,
        headers: List<String>,
        out: MutableList<ImportedWarehousePartRow>
    ) {
        val codeCol = findCol(headers, "part code", "part id", "part no", "part number", "spare part id", "sku", "code")
        val nameCol = findCol(headers, "part name", "name", "title")
        val descCol = findCol(headers, "description", "part description", "spec", "detail")
        val unitCol = findCol(headers, "default unit", "unit", "uom")

        if (codeCol < 0 && descCol < 0) return

        rows.drop(1).forEachIndexed { idx, row ->
            val rawCode = row.getOrNull(if (codeCol >= 0) codeCol else descCol)?.trim().orEmpty()
            val rawDesc = row.getOrNull(descCol)?.trim().orEmpty()
            val rawName = row.getOrNull(nameCol)?.trim().orEmpty()
            if (rawCode.isEmpty() && rawDesc.isEmpty() && rawName.isEmpty()) return@forEachIndexed
            val code = (rawCode.ifEmpty { "PART-${1000 + idx}" }).uppercase()
            val unit = row.getOrNull(unitCol)?.trim()?.ifEmpty { "pcs" } ?: "pcs"
            out += ImportedWarehousePartRow(
                partCode = code,
                partName = rawName,
                partDescription = rawDesc.ifEmpty { rawName },
                defaultUnit = unit
            )
        }
    }

    private fun findCol(headers: List<String>, vararg needles: String): Int {
        for (needle in needles) {
            val exact = headers.indexOfFirst { it == needle }
            if (exact >= 0) return exact
            val partial = headers.indexOfFirst { it.contains(needle) }
            if (partial >= 0) return partial
        }
        return -1
    }

    private fun parseFlexibleDateMillis(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        // Handle Excel numeric serial date (days since 1899-12-30)
        text.toDoubleOrNull()?.let { serial ->
            if (serial > 20000.0 && serial < 90000.0) {
                val cal = Calendar.getInstance().apply {
                    set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val days = serial.toInt()
                val fracMs = ((serial - days) * 86_400_000.0).toLong()
                cal.add(Calendar.DAY_OF_YEAR, days)
                return cal.timeInMillis + fracMs
            }
        }

        val formats = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy",
            "MM/dd/yyyy HH:mm",
            "MM/dd/yyyy",
            "dd MMM yyyy, HH:mm",
            "dd MMM yyyy"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(text)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseXmlBytes(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
    }

    // --- OOXML Readers ---
    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        parseXmlBytes(
            bytes,
            object : DefaultHandler() {
                private var inSi = false
                private var inT = false
                private val currentSi = StringBuilder()

                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    val tag = localName?.ifEmpty { null } ?: qName.orEmpty()
                    when (tag) {
                        "si" -> {
                            inSi = true
                            currentSi.clear()
                        }
                        "t" -> if (inSi) inT = true
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inSi && inT) currentSi.appendRange(ch, start, start + length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val tag = localName?.ifEmpty { null } ?: qName.orEmpty()
                    when (tag) {
                        "t" -> inT = false
                        "si" -> {
                            inSi = false
                            result += currentSi.toString()
                        }
                    }
                }
            }
        )
        return result
    }

    private fun resolveWorkbookSheets(entries: Map<String, ByteArray>): List<Pair<String, String>> {
        val relsBytes = entries["xl/_rels/workbook.xml.rels"]
        val wbBytes = entries["xl/workbook.xml"]
        if (relsBytes == null || wbBytes == null) {
            return entries.keys
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
                .map { it.substringAfterLast('/') to it }
        }

        val targetByRelId = mutableMapOf<String, String>()
        parseXmlBytes(
            relsBytes,
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    val tag = localName?.ifEmpty { null } ?: qName.orEmpty()
                    if (tag == "Relationship" && attributes != null) {
                        val id = attributes.getValue("Id")
                        val target = attributes.getValue("Target")
                        if (id != null && target != null) {
                            val norm = if (target.startsWith("/")) target.drop(1) else if (target.startsWith("xl/")) target else "xl/$target"
                            targetByRelId[id] = norm
                        }
                    }
                }
            }
        )

        val sheets = mutableListOf<Pair<String, String>>()
        parseXmlBytes(
            wbBytes,
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    val tag = localName?.ifEmpty { null } ?: qName.orEmpty()
                    if (tag == "sheet" && attributes != null) {
                        val name = attributes.getValue("name") ?: "Sheet"
                        val rid = attributes.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                            ?: attributes.getValue("r:id")
                        val path = rid?.let { targetByRelId[it] }
                        if (path != null) sheets += name to path
                    }
                }
            }
        )
        return sheets
    }

    private fun parseWorksheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        parseXmlBytes(
            bytes,
            object : DefaultHandler() {
                private var currentRow = mutableMapOf<Int, String>()
                private var currentCol = -1
                private var currentType: String? = null
                private var inV = false
                private var inIs = false
                private var inT = false
                private val textBuf = StringBuilder()

                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    val tag = localName?.ifEmpty { null } ?: qName.orEmpty()
                    when (tag) {
                        "row" -> currentRow = mutableMapOf()
                        "c" -> {
                            val ref = attributes?.getValue("r")
                            currentCol = if (ref != null) parseColIndex(ref) else (currentRow.keys.maxOrNull() ?: -1) + 1
                            currentType = attributes?.getValue("t")
                            textBuf.clear()
                        }
                        "v" -> {
                            inV = true
                            textBuf.clear()
                        }
                        "is" -> {
                            inIs = true
                            textBuf.clear()
                        }
                        "t" -> if (inIs) inT = true
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inV || (inIs && inT)) textBuf.appendRange(ch, start, start + length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    val tag = localName?.ifEmpty { null } ?: qName.orEmpty()
                    when (tag) {
                        "v" -> inV = false
                        "t" -> inT = false
                        "is" -> inIs = false
                        "c" -> {
                            val raw = textBuf.toString()
                            val value = when (currentType) {
                                "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                                else -> raw
                            }
                            if (currentCol >= 0) currentRow[currentCol] = value
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                val maxCol = currentRow.keys.maxOrNull() ?: 0
                                val dense = (0..maxCol).map { currentRow[it].orEmpty() }
                                if (dense.any { it.isNotBlank() }) rows += dense
                            }
                        }
                    }
                }
            }
        )
        return rows
    }

    private fun parseColIndex(cellRef: String): Int {
        var idx = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                idx = idx * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                idx = idx * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return idx - 1
    }

    // --- Template Writer Helpers ---
    private fun writeZip(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun colLetter(idx: Int): String {
        var n = idx
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun buildSimpleSheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            val rNum = rIdx + 1
            val style = if (rNum == 1) """ s="1"""" else ""
            sb.append("""<row r="$rNum">""")
            row.forEachIndexed { cIdx, v ->
                val ref = "${colLetter(cIdx)}$rNum"
                sb.append("""<c r="$ref" t="inlineStr"$style><is><t>${esc(v)}</t></is></c>""")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun templateContentTypesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet5.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private fun templateRootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun templateWorkbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Custom Groups" sheetId="1" r:id="rId1"/>
    <sheet name="Machines" sheetId="2" r:id="rId2"/>
    <sheet name="Maintenance Tasks" sheetId="3" r:id="rId3"/>
    <sheet name="To-Do Reminders" sheetId="4" r:id="rId4"/>
    <sheet name="Warehouse Parts" sheetId="5" r:id="rId5"/>
  </sheets>
</workbook>"""

    private fun templateWorkbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet5.xml"/>
  <Relationship Id="rId6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun templateStylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1B4965"/></patternFill></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""
}
