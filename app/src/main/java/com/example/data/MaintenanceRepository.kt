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
    val engineersFlow: Flow<List<EngineerEntity>> = dao.observeEngineers()
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

    // --- Engineers Roster ---
    suspend fun saveEngineer(engineer: EngineerEntity): Long {
        val now = System.currentTimeMillis()
        val updated = engineer.copy(
            name = engineer.name.trim(),
            employeeCode = engineer.employeeCode.trim().uppercase(),
            role = engineer.role.trim().ifEmpty { "Maintenance Engineer" },
            shift = engineer.shift.trim().ifEmpty { "Day Shift" },
            updatedAtMillis = now
        )
        return if (engineer.id == 0L) {
            dao.insertEngineer(updated)
        } else {
            dao.updateEngineer(updated)
            engineer.id
        }
    }

    suspend fun deleteEngineer(engineerId: Long) {
        dao.deleteEngineerById(engineerId)
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
    suspend fun saveTask(task: MaintenanceTaskEntity, machineStatus: MachineStatus? = null): Long {
        val now = System.currentTimeMillis()
        val cleanAssistants = formatEngineerList(parseEngineerList(task.assistantEngineers), excludePic = task.personInCharge)
        val existingCreated = if (task.id != 0L) {
            dao.getTaskById(task.id)?.createdAtMillis ?: task.createdAtMillis
        } else {
            if (task.createdAtMillis > 0L) task.createdAtMillis else now
        }
        val updated = task.copy(
            assistantEngineers = cleanAssistants,
            reminderLeadHours = task.reminderLeadHours.coerceAtLeast(1),
            createdAtMillis = existingCreated,
            updatedAtMillis = now
        )
        val savedId = if (task.id == 0L) {
            dao.insertTask(updated)
        } else {
            dao.updateTask(updated)
            task.id
        }
        if (machineStatus != null) {
            updateMachineStatus(task.machineId, machineStatus)
        }
        return savedId
    }

    suspend fun deleteTask(taskId: Long) {
        dao.deleteTaskById(taskId)
    }

    /**
     * Completes a task, auto-generates a DONE machine report entry in service_logs,
     * and if repeatable (cadence != Cadence.ONCE), auto-recreates the next cycle task
     * complete with its reminder setting & lead time.
     */
    suspend fun completeTask(
        taskId: Long,
        personInCharge: String,
        assistantEngineers: List<String> = emptyList(),
        actualDurationMinutes: Int,
        notes: String,
        spareParts: List<SparePartDraft>,
        flagMalfunction: Boolean,
        malfunctionSeverity: Severity
    ) {
        val task = dao.getTaskById(taskId) ?: return
        val now = System.currentTimeMillis()
        val deadlineWasMet = now <= task.deadlineMillis
        val finalPic = personInCharge.trim().ifEmpty { task.personInCharge }
        val finalAssistants = if (assistantEngineers.isNotEmpty()) {
            formatEngineerList(assistantEngineers, excludePic = finalPic)
        } else {
            formatEngineerList(parseEngineerList(task.assistantEngineers), excludePic = finalPic)
        }
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
            outcome = TaskOutcome.DONE,
            cadenceSnapshot = task.cadence,
            prioritySnapshot = task.priority,
            summaryTitle = task.title,
            malfunctionDetails = notes.ifBlank { "${task.cadence.label} maintenance completed." },
            rejectionReason = "",
            severity = if (flagMalfunction) malfunctionSeverity else Severity.LOW,
            personInCharge = finalPic,
            assistantEngineers = finalAssistants,
            serviceDurationMinutes = actualDurationMinutes.coerceAtLeast(1),
            completedAtMillis = now,
            deadlineWasMet = deadlineWasMet,
            createdAtMillis = task.createdAtMillis,
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

        // Delete the finished task instance (ServiceLog has ForeignKey.SET_NULL on taskId)
        dao.deleteTaskById(task.id)

        // If repeatable (with cadence != ONCE), auto-recreate the next task instance with its reminder
        if (task.cadence != Cadence.ONCE) {
            val nextDeadline = computeNextCadenceMillis(
                baseAnchorMillis = task.deadlineMillis,
                nowMillis = now,
                cadence = task.cadence
            )
            dao.insertTask(
                MaintenanceTaskEntity(
                    uuid = UUID.randomUUID().toString(),
                    machineId = task.machineId,
                    groupId = task.groupId,
                    title = task.title,
                    description = task.description,
                    cadence = task.cadence,
                    deadlineMillis = nextDeadline,
                    estimatedMinutes = task.estimatedMinutes,
                    personInCharge = finalPic,
                    assistantEngineers = finalAssistants,
                    priority = task.priority,
                    reminderEnabled = task.reminderEnabled,
                    reminderLeadHours = task.reminderLeadHours,
                    lastCompletedMillis = now,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        if (flagMalfunction && (malfunctionSeverity == Severity.HIGH || malfunctionSeverity == Severity.CRITICAL)) {
            updateMachineStatus(task.machineId, MachineStatus.MALFUNCTION)
        } else {
            val machine = dao.getMachineById(task.machineId)
            if (machine != null && machine.status == MachineStatus.MAINTENANCE_DUE) {
                dao.updateMachine(machine.copy(status = MachineStatus.OPERATIONAL, updatedAtMillis = now))
            }
        }
    }

    /**
     * Rejects a task/service request, auto-generates a REJECTED machine report entry
     * with reason, PIC, and assistant engineers. Optionally reschedules next cycle for repeatable tasks.
     */
    suspend fun rejectTask(
        taskId: Long,
        personInCharge: String,
        assistantEngineers: List<String> = emptyList(),
        rejectionReason: String,
        rescheduleNextCycle: Boolean = false
    ) {
        val task = dao.getTaskById(taskId) ?: return
        val now = System.currentTimeMillis()
        val finalPic = personInCharge.trim().ifEmpty { task.personInCharge }
        val finalAssistants = if (assistantEngineers.isNotEmpty()) {
            formatEngineerList(assistantEngineers, excludePic = finalPic)
        } else {
            formatEngineerList(parseEngineerList(task.assistantEngineers), excludePic = finalPic)
        }
        val cleanReason = rejectionReason.trim().ifEmpty { "Service request rejected — machine unavailable for servicing." }

        val log = ServiceLogEntity(
            uuid = UUID.randomUUID().toString(),
            machineId = task.machineId,
            taskId = task.id,
            entryType = ServiceEntryType.PREVENTIVE_SERVICE,
            outcome = TaskOutcome.REJECTED,
            cadenceSnapshot = task.cadence,
            prioritySnapshot = task.priority,
            summaryTitle = task.title,
            malfunctionDetails = "Service request rejected.",
            rejectionReason = cleanReason,
            severity = Severity.MEDIUM,
            personInCharge = finalPic,
            assistantEngineers = finalAssistants,
            serviceDurationMinutes = 0,
            completedAtMillis = now,
            deadlineWasMet = now <= task.deadlineMillis,
            createdAtMillis = task.createdAtMillis,
            updatedAtMillis = now
        )
        dao.insertLogWithParts(log, emptyList())

        dao.deleteTaskById(task.id)
        if (rescheduleNextCycle && task.cadence != Cadence.ONCE) {
            val nextDeadline = computeNextCadenceMillis(
                baseAnchorMillis = task.deadlineMillis,
                nowMillis = now,
                cadence = task.cadence
            )
            dao.insertTask(
                MaintenanceTaskEntity(
                    uuid = UUID.randomUUID().toString(),
                    machineId = task.machineId,
                    groupId = task.groupId,
                    title = task.title,
                    description = task.description,
                    cadence = task.cadence,
                    deadlineMillis = nextDeadline,
                    estimatedMinutes = task.estimatedMinutes,
                    personInCharge = finalPic,
                    assistantEngineers = finalAssistants,
                    priority = task.priority,
                    reminderEnabled = task.reminderEnabled,
                    reminderLeadHours = task.reminderLeadHours,
                    lastCompletedMillis = task.lastCompletedMillis,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }
    }

    // --- To-Do Reminders ---
    suspend fun saveTodo(todo: TodoReminderEntity): Long {
        val now = System.currentTimeMillis()
        val existingCreated = if (todo.id != 0L) {
            dao.getTodoById(todo.id)?.createdAtMillis ?: todo.createdAtMillis
        } else {
            if (todo.createdAtMillis > 0L) todo.createdAtMillis else now
        }
        val updated = todo.copy(
            createdAtMillis = existingCreated,
            updatedAtMillis = now
        )
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
        when {
            todo.isCompleted -> {
                dao.updateTodo(
                    todo.copy(
                        isCompleted = false,
                        completedAtMillis = null,
                        updatedAtMillis = now
                    )
                )
            }
            todo.repeatRule == RepeatRule.ONCE -> {
                dao.updateTodo(
                    todo.copy(
                        isCompleted = true,
                        completedAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            }
            else -> {
                dao.updateTodo(
                    todo.copy(
                        isCompleted = true,
                        completedAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                val nextDue = computeNextRepeatMillis(
                    baseAnchorMillis = todo.dueAtMillis,
                    nowMillis = now,
                    rule = todo.repeatRule
                )
                dao.insertTodo(
                    todo.copy(
                        id = 0L,
                        uuid = UUID.randomUUID().toString(),
                        dueAtMillis = nextDue,
                        isCompleted = false,
                        completedAtMillis = null,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
            }
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
                Cadence.ONCE -> RepeatRule.ONCE
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
