package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

enum class Cadence(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class MachineStatus(val label: String) {
    OPERATIONAL("Operational"),
    MAINTENANCE_DUE("Maint. Due"),
    MALFUNCTION("Malfunction")
}

enum class GroupScope(val label: String) {
    ALL("Machines & Tasks"),
    MACHINE("Machines Only"),
    TASK("Tasks Only")
}

enum class RepeatRule(val label: String) {
    ONCE("Once"),
    DAILY("Every Day"),
    WEEKLY("Every Week"),
    MONTHLY("Every Month"),
    YEARLY("Every Year")
}

enum class ReminderPriority(val label: String) {
    NORMAL("Normal"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class ServiceEntryType(val label: String) {
    PREVENTIVE_SERVICE("Preventive Service"),
    MALFUNCTION_REPAIR("Malfunction / Breakdown")
}

enum class Severity(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical")
}

enum class AutoBackupFrequency(val label: String) {
    OFF("Manual Only"),
    DAILY("Daily Auto-Backup"),
    WEEKLY("Weekly Auto-Backup")
}

@Entity(
    tableName = "custom_groups",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class CustomGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val scope: GroupScope = GroupScope.ALL,
    val colorKey: String = "COBALT",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "machines",
    foreignKeys = [
        ForeignKey(
            entity = CustomGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["groupId"])
    ]
)
data class MachineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val groupId: Long? = null,
    val assetCode: String,
    val name: String,
    val locationZone: String,
    val modelInfo: String,
    val status: MachineStatus = MachineStatus.OPERATIONAL,
    val notes: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "maintenance_tasks",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CustomGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["machineId"]),
        Index(value = ["groupId"])
    ]
)
data class MaintenanceTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val machineId: Long,
    val groupId: Long? = null,
    val title: String,
    val description: String = "",
    val cadence: Cadence,
    val deadlineMillis: Long,
    val estimatedMinutes: Int = 30,
    val personInCharge: String,
    val reminderEnabled: Boolean = true,
    val reminderLeadHours: Int = 24,
    val lastCompletedMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "todo_reminders",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CustomGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["machineId"]),
        Index(value = ["groupId"])
    ]
)
data class TodoReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val machineId: Long? = null,
    val groupId: Long? = null,
    val personInCharge: String = "",
    val repeatRule: RepeatRule = RepeatRule.ONCE,
    val dueAtMillis: Long,
    val reminderEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val priority: ReminderPriority = ReminderPriority.NORMAL,
    val completedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "service_logs",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaintenanceTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["machineId"]),
        Index(value = ["taskId"])
    ]
)
data class ServiceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val machineId: Long,
    val taskId: Long? = null,
    val entryType: ServiceEntryType,
    val cadenceSnapshot: Cadence? = null,
    val summaryTitle: String,
    val malfunctionDetails: String = "",
    val severity: Severity = Severity.LOW,
    val personInCharge: String,
    val serviceDurationMinutes: Int,
    val completedAtMillis: Long,
    val deadlineWasMet: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "spare_parts_used",
    foreignKeys = [
        ForeignKey(
            entity = ServiceLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["serviceLogId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["serviceLogId"])
    ]
)
data class SparePartUsedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val serviceLogId: Long,
    val partName: String,
    val partCode: String = "",
    val partDescription: String = "",
    val fromWarehouse: Boolean = true,
    val quantity: Int = 1,
    val unit: String = "pcs"
)

data class ServiceLogWithParts(
    @Embedded val log: ServiceLogEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "serviceLogId"
    )
    val parts: List<SparePartUsedEntity> = emptyList()
)

@Entity(
    tableName = "warehouse_parts",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["partCode"])
    ]
)
data class WarehousePartEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val partCode: String,
    val partName: String = "",
    val partDescription: String = "",
    val defaultUnit: String = "pcs",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

