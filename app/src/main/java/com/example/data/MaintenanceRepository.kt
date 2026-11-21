package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

data class SparePartDraft(
    val partName: String,
    val partCode: String = "",
    val partDescription: String = "",
    val fromWarehouse: Boolean = true,
    val quantity: Int = 1,
    val unit: String = "pcs"
)

class MaintenanceRepository(
    private val dao: MachinaDao,
    private val prefs: AppPreferences
) {
    val groupsFlow: Flow<List<CustomGroupEntity>> = dao.observeGroups()
    val machinesFlow: Flow<List<MachineEntity>> = dao.observeMachines()
    val tasksFlow: Flow<List<MaintenanceTaskEntity>> = dao.observeTasks()
    val todosFlow: Flow<List<TodoReminderEntity>> = dao.observeTodos()
    val serviceLogsFlow: Flow<List<ServiceLogWithParts>> = dao.observeServiceLogsWithParts()
    val warehousePartsFlow: Flow<List<WarehousePartEntity>> = dao.observeWarehouseParts()
    val preferencesFlow: Flow<UserPreferences> = prefs.preferencesFlow

    suspend fun ensureSeeded() {
        SampleDataSeeder.seedIfNeeded(dao, prefs)
    }

    suspend fun resetSampleData() {
        SampleDataSeeder.resetWithSampleDataset(dao, prefs)
    }

    suspend fun clearAllRecords() {
        dao.clearAllData()
        prefs.setSampleDataSeeded(true)
    }

    // --- Custom Groups ---
    suspend fun saveGroup(group: CustomGroupEntity): Long {
        val now = System.currentTimeMillis()
        val updated = group.copy(updatedAtMillis = now)
        return if (group.id == 0L) {
            dao.insertGroup(updated)
        } else {
            dao.updateGroup(updated)
            group.id
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        dao.deleteGroupById(groupId)
    }

    // --- Machines ---
    suspend fun saveMachine(machine: MachineEntity): Long {
        val now = System.currentTimeMillis()
        val updated = machine.copy(updatedAtMillis = now)
        return if (machine.id == 0L) {
            dao.insertMachine(updated)
        } else {
            dao.updateMachine(updated)
            machine.id
        }
    }

    suspend fun updateMachineStatus(machineId: Long, status: MachineStatus) {
        val existing = dao.getMachineById(machineId) ?: return
        dao.updateMachine(existing.copy(status = status, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun deleteMachine(machineId: Long) {
        dao.deleteMachineById(machineId)
    }

    // --- Maintenance Tasks ---
    suspend fun saveTask(task: MaintenanceTaskEntity): Long {
        val now = System.currentTimeMillis()
        val updated = task.copy(updatedAtMillis = now)
        return if (task.id == 0L) {
            dao.insertTask(updated)
        } else {
            dao.updateTask(updated)
            task.id
        }
    }

    suspend fun deleteTask(taskId: Long) {
        dao.deleteTaskById(taskId)
    }

    suspend fun completeTask(
        taskId: Long,
        personInCharge: String,
        actualDurationMinutes: Int,
        notes: String,
        spareParts: List<SparePartDraft>,
        flagMalfunction: Boolean,
        malfunctionSeverity: Severity
    ) {
        val task = dao.getTaskById(taskId) ?: return
        val now = System.currentTimeMillis()
        val deadlineWasMet = now <= task.deadlineMillis
        val entryType = if (flagMalfunction) {
            ServiceEntryType.MALFUNCTION_REPAIR
        } else {
            ServiceEntryType.PREVENTIVE_SERVICE
        }

        val log = ServiceLogEntity(
            uuid = UUID.randomUUID().toString(),
            machineId = task.machineId,
            taskId = task.id,
            entryType = entryType,
            cadenceSnapshot = task.cadence,
            summaryTitle = task.title,
            malfunctionDetails = notes.ifBlank { "Scheduled ${task.cadence.label.lowercase()} maintenance completed." },
            severity = if (flagMalfunction) malfunctionSeverity else Severity.LOW,
            personInCharge = personInCharge.ifBlank { task.personInCharge },
            serviceDurationMinutes = actualDurationMinutes.coerceAtLeast(1),
            completedAtMillis = now,
            deadlineWasMet = deadlineWasMet,
            updatedAtMillis = now
        )
        val partEntities = spareParts
            .filter { it.partName.isNotBlank() || it.partCode.isNotBlank() }
            .map {
                SparePartUsedEntity(
                    uuid = UUID.randomUUID().toString(),
                    serviceLogId = 0L,
                    partName = it.partName.trim().ifEmpty { it.partCode.trim() },
                    partCode = it.partCode.trim(),
                    partDescription = it.partDescription.trim(),
                    fromWarehouse = it.fromWarehouse,
                    quantity = it.quantity.coerceAtLeast(1),
                    unit = it.unit.trim().ifEmpty { "pcs" }
                )
            }
        dao.insertLogWithParts(log, partEntities)

        val nextDeadline = computeNextCadenceMillis(
            baseAnchorMillis = task.deadlineMillis,
            nowMillis = now,
            cadence = task.cadence
        )
        dao.updateTask(
            task.copy(
                personInCharge = personInCharge.ifBlank { task.personInCharge },
                deadlineMillis = nextDeadline,
                lastCompletedMillis = now,
                updatedAtMillis = now
            )
        )

        if (flagMalfunction && (malfunctionSeverity == Severity.HIGH || malfunctionSeverity == Severity.CRITICAL)) {
            updateMachineStatus(task.machineId, MachineStatus.MALFUNCTION)
        } else {
            val machine = dao.getMachineById(task.machineId)
            if (machine != null && machine.status == MachineStatus.MAINTENANCE_DUE) {
                dao.updateMachine(machine.copy(status = MachineStatus.OPERATIONAL, updatedAtMillis = now))
            }
        }
    }

    // --- Direct Service / Malfunction Logging ---
    suspend fun logServiceOrMalfunction(
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
        val now = System.currentTimeMillis()
        val log = ServiceLogEntity(
            uuid = UUID.randomUUID().toString(),
            machineId = machineId,
            taskId = null,
            entryType = entryType,
            cadenceSnapshot = null,
            summaryTitle = summaryTitle.trim(),
            malfunctionDetails = details.trim(),
            severity = severity,
            personInCharge = personInCharge.trim(),
            serviceDurationMinutes = durationMinutes.coerceAtLeast(1),
            completedAtMillis = now,
            deadlineWasMet = true,
            updatedAtMillis = now
        )
        val partEntities = spareParts
            .filter { it.partName.isNotBlank() || it.partCode.isNotBlank() }
            .map {
                SparePartUsedEntity(
                    uuid = UUID.randomUUID().toString(),
                    serviceLogId = 0L,
                    partName = it.partName.trim().ifEmpty { it.partCode.trim() },
                    partCode = it.partCode.trim(),
                    partDescription = it.partDescription.trim(),
                    fromWarehouse = it.fromWarehouse,
                    quantity = it.quantity.coerceAtLeast(1),
                    unit = it.unit.trim().ifEmpty { "pcs" }
                )
            }
        dao.insertLogWithParts(log, partEntities)

        if (newMachineStatus != null) {
            updateMachineStatus(machineId, newMachineStatus)
        }
    }

    // --- To-Do Reminders ---
    suspend fun saveTodo(todo: TodoReminderEntity): Long {
        val now = System.currentTimeMillis()
        val updated = todo.copy(updatedAtMillis = now)
        return if (todo.id == 0L) {
            dao.insertTodo(updated)
        } else {
            dao.updateTodo(updated)
            todo.id
        }
    }

    suspend fun toggleTodoComplete(todoId: Long) {
        val todo = dao.getTodoById(todoId) ?: return
        val now = System.currentTimeMillis()
        if (todo.repeatRule == RepeatRule.ONCE) {
            val newDone = !todo.isCompleted
            dao.updateTodo(
                todo.copy(
                    isCompleted = newDone,
                    completedAtMillis = if (newDone) now else null,
                    updatedAtMillis = now
                )
            )
        } else {
            // Recurring reminder: record completion timestamp and advance dueAtMillis to next trigger
            val nextDue = computeNextRepeatMillis(
                baseAnchorMillis = todo.dueAtMillis,
                nowMillis = now,
                rule = todo.repeatRule
            )
            dao.updateTodo(
                todo.copy(
                    dueAtMillis = nextDue,
                    isCompleted = false,
                    completedAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }
    }

    suspend fun deleteTodo(todoId: Long) {
        dao.deleteTodoById(todoId)
    }

    suspend fun deleteServiceLog(logId: Long) {
        dao.deleteServiceLogById(logId)
    }

    // --- Warehouse Spare Parts Catalog ---
    suspend fun saveWarehousePart(part: WarehousePartEntity): Long {
        val now = System.currentTimeMillis()
        val updated = part.copy(
            partCode = part.partCode.trim().uppercase(),
            partName = part.partName.trim(),
            partDescription = part.partDescription.trim(),
            defaultUnit = part.defaultUnit.trim().ifEmpty { "pcs" },
            updatedAtMillis = now
        )
        return if (part.id == 0L) {
            dao.insertWarehousePart(updated)
        } else {
            dao.updateWarehousePart(updated)
            part.id
        }
    }

    suspend fun deleteWarehousePart(partId: Long) {
        dao.deleteWarehousePartById(partId)
    }

    companion object {
        fun computeNextCadenceMillis(baseAnchorMillis: Long, nowMillis: Long, cadence: Cadence): Long {
            val rule = when (cadence) {
                Cadence.DAILY -> RepeatRule.DAILY
                Cadence.WEEKLY -> RepeatRule.WEEKLY
                Cadence.MONTHLY -> RepeatRule.MONTHLY
                Cadence.YEARLY -> RepeatRule.YEARLY
            }
            return computeNextRepeatMillis(baseAnchorMillis, nowMillis, rule)
        }

        fun computeNextRepeatMillis(baseAnchorMillis: Long, nowMillis: Long, rule: RepeatRule): Long {
            if (rule == RepeatRule.ONCE) return baseAnchorMillis
            val anchorCal = Calendar.getInstance().apply { timeInMillis = baseAnchorMillis }
            val cal = Calendar.getInstance().apply {
                timeInMillis = maxOf(baseAnchorMillis, nowMillis)
                set(Calendar.HOUR_OF_DAY, anchorCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, anchorCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (cal.timeInMillis <= nowMillis) {
                when (rule) {
                    RepeatRule.ONCE -> break
                    RepeatRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    RepeatRule.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    RepeatRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
                    RepeatRule.YEARLY -> cal.add(Calendar.YEAR, 1)
                }
            }
            return cal.timeInMillis
        }
    }
}
