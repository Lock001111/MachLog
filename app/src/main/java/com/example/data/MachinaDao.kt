package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MachinaDao {

    // --- Custom Groups ---
    @Query("SELECT * FROM custom_groups ORDER BY name ASC")
    fun observeGroups(): Flow<List<CustomGroupEntity>>

    @Query("SELECT * FROM custom_groups ORDER BY name ASC")
    suspend fun getAllGroupsSync(): List<CustomGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: CustomGroupEntity): Long

    @Update
    suspend fun updateGroup(group: CustomGroupEntity)

    @Query("DELETE FROM custom_groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    // --- Machines ---
    @Query("SELECT * FROM machines ORDER BY assetCode ASC")
    fun observeMachines(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines ORDER BY assetCode ASC")
    suspend fun getAllMachinesSync(): List<MachineEntity>

    @Query("SELECT * FROM machines WHERE id = :id")
    suspend fun getMachineById(id: Long): MachineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMachine(machine: MachineEntity): Long

    @Update
    suspend fun updateMachine(machine: MachineEntity)

    @Query("DELETE FROM machines WHERE id = :machineId")
    suspend fun deleteMachineById(machineId: Long)

    // --- Maintenance Tasks ---
    @Query("SELECT * FROM maintenance_tasks ORDER BY deadlineMillis ASC")
    fun observeTasks(): Flow<List<MaintenanceTaskEntity>>

    @Query("SELECT * FROM maintenance_tasks ORDER BY deadlineMillis ASC")
    suspend fun getAllTasksSync(): List<MaintenanceTaskEntity>

    @Query("SELECT * FROM maintenance_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): MaintenanceTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: MaintenanceTaskEntity): Long

    @Update
    suspend fun updateTask(task: MaintenanceTaskEntity)

    @Query("DELETE FROM maintenance_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    // --- To-Do Reminders ---
    @Query("SELECT * FROM todo_reminders ORDER BY isCompleted ASC, dueAtMillis ASC")
    fun observeTodos(): Flow<List<TodoReminderEntity>>

    @Query("SELECT * FROM todo_reminders ORDER BY dueAtMillis ASC")
    suspend fun getAllTodosSync(): List<TodoReminderEntity>

    @Query("SELECT * FROM todo_reminders WHERE id = :id")
    suspend fun getTodoById(id: Long): TodoReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoReminderEntity): Long

    @Update
    suspend fun updateTodo(todo: TodoReminderEntity)

    @Query("DELETE FROM todo_reminders WHERE id = :todoId")
    suspend fun deleteTodoById(todoId: Long)

    // --- Service Logs & Spare Parts ---
    @Transaction
    @Query("SELECT * FROM service_logs ORDER BY completedAtMillis DESC")
    fun observeServiceLogsWithParts(): Flow<List<ServiceLogWithParts>>

    @Transaction
    @Query("SELECT * FROM service_logs ORDER BY completedAtMillis DESC")
    suspend fun getAllServiceLogsWithPartsSync(): List<ServiceLogWithParts>

    @Query("SELECT * FROM spare_parts_used")
    suspend fun getAllSparePartsSync(): List<SparePartUsedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceLog(log: ServiceLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpareParts(parts: List<SparePartUsedEntity>)

    @Query("DELETE FROM spare_parts_used WHERE serviceLogId = :logId")
    suspend fun deletePartsForLog(logId: Long)

    @Query("DELETE FROM service_logs WHERE id = :logId")
    suspend fun deleteServiceLogById(logId: Long)

    @Transaction
    suspend fun insertLogWithParts(log: ServiceLogEntity, parts: List<SparePartUsedEntity>): Long {
        val logId = insertServiceLog(log)
        if (parts.isNotEmpty()) {
            insertSpareParts(parts.map { it.copy(serviceLogId = logId) })
        }
        return logId
    }

    // --- Warehouse Spare Parts Catalog (Codes & Descriptions) ---
    @Query("SELECT * FROM warehouse_parts ORDER BY partCode ASC")
    fun observeWarehouseParts(): Flow<List<WarehousePartEntity>>

    @Query("SELECT * FROM warehouse_parts ORDER BY partCode ASC")
    suspend fun getAllWarehousePartsSync(): List<WarehousePartEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehousePart(part: WarehousePartEntity): Long

    @Update
    suspend fun updateWarehousePart(part: WarehousePartEntity)

    @Query("DELETE FROM warehouse_parts WHERE id = :partId")
    suspend fun deleteWarehousePartById(partId: Long)

    @Query("DELETE FROM warehouse_parts")
    suspend fun clearAllWarehouseParts()

    @Query("DELETE FROM spare_parts_used")
    suspend fun clearAllSpareParts()

    @Query("DELETE FROM service_logs")
    suspend fun clearAllServiceLogs()

    @Query("DELETE FROM todo_reminders")
    suspend fun clearAllTodos()

    @Query("DELETE FROM maintenance_tasks")
    suspend fun clearAllTasks()

    @Query("DELETE FROM machines")
    suspend fun clearAllMachines()

    @Query("DELETE FROM custom_groups")
    suspend fun clearAllGroups()

    @Transaction
    suspend fun clearAllData() {
        clearAllSpareParts()
        clearAllServiceLogs()
        clearAllTodos()
        clearAllTasks()
        clearAllMachines()
        clearAllGroups()
        clearAllWarehouseParts()
    }
}
