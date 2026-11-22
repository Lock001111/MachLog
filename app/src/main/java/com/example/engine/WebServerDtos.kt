package com.example.engine

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WebPortalStatus(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val urls: List<String> = emptyList(),
    val primaryUrl: String? = null,
    val pinEnabled: Boolean = false,
    val pinCode: String = "4821",
    val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class WebPingResponse(
    val ok: Boolean = true,
    val appName: String = "MachinaLog",
    val pinRequired: Boolean = false,
    val authorized: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WebEngineerDto(
    val id: Long,
    val uuid: String,
    val name: String,
    val employeeCode: String,
    val role: String,
    val shift: String
)

@JsonClass(generateAdapter = true)
data class WebStateResponse(
    val nowMillis: Long,
    val defaultEngineerName: String,
    val appVersionName: String = "1.0",
    val appVersionCode: Int = 1,
    val githubRepo: String = "Lock001111/MachLog",
    val groups: List<WebGroupDto>,
    val machines: List<WebMachineDto>,
    val tasks: List<WebTaskDto>,
    val todos: List<WebTodoDto>,
    val warehouseParts: List<WebWarehousePartDto>,
    val engineers: List<WebEngineerDto>,
    val serviceLogs: List<WebServiceLogDto>
)

@JsonClass(generateAdapter = true)
data class WebGroupDto(
    val id: Long,
    val uuid: String,
    val name: String,
    val scope: String,
    val colorKey: String
)

@JsonClass(generateAdapter = true)
data class WebMachineDto(
    val id: Long,
    val uuid: String,
    val groupId: Long?,
    val assetCode: String,
    val name: String,
    val locationZone: String,
    val modelInfo: String,
    val status: String,
    val notes: String
)

@JsonClass(generateAdapter = true)
data class WebTaskDto(
    val id: Long,
    val uuid: String,
    val machineId: Long,
    val groupId: Long?,
    val title: String,
    val description: String,
    val cadence: String,
    val deadlineMillis: Long,
    val estimatedMinutes: Int,
    val personInCharge: String,
    val assistantEngineers: String,
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean,
    val reminderLeadHours: Int,
    val lastCompletedMillis: Long?,
    val createdAtMillis: Long = 0L
)

@JsonClass(generateAdapter = true)
data class WebTodoDto(
    val id: Long,
    val uuid: String,
    val title: String,
    val notes: String,
    val machineId: Long?,
    val groupId: Long?,
    val personInCharge: String,
    val repeatRule: String,
    val dueAtMillis: Long,
    val reminderEnabled: Boolean,
    val isCompleted: Boolean,
    val priority: String,
    val completedAtMillis: Long?,
    val createdAtMillis: Long = 0L
)

@JsonClass(generateAdapter = true)
data class WebWarehousePartDto(
    val id: Long,
    val uuid: String,
    val partCode: String,
    val partName: String,
    val partDescription: String,
    val defaultUnit: String
)

@JsonClass(generateAdapter = true)
data class WebSparePartUsedDto(
    val partCode: String,
    val partName: String,
    val partDescription: String,
    val fromWarehouse: Boolean,
    val quantity: Int,
    val unit: String
)

@JsonClass(generateAdapter = true)
data class WebServiceLogDto(
    val id: Long,
    val uuid: String,
    val machineId: Long,
    val taskId: Long?,
    val entryType: String,
    val cadenceSnapshot: String?,
    val prioritySnapshot: String? = null,
    val summaryTitle: String,
    val malfunctionDetails: String,
    val severity: String,
    val outcome: String,
    val rejectionReason: String,
    val personInCharge: String,
    val assistantEngineers: String,
    val serviceDurationMinutes: Int,
    val completedAtMillis: Long,
    val deadlineWasMet: Boolean,
    val createdAtMillis: Long = 0L,
    val parts: List<WebSparePartUsedDto>
)

@JsonClass(generateAdapter = true)
data class WebSaveGroupReq(
    val id: Long = 0,
    val name: String,
    val scope: String = "ALL",
    val colorKey: String = "COBALT"
)

@JsonClass(generateAdapter = true)
data class WebSaveEngineerReq(
    val id: Long = 0,
    val name: String,
    val employeeCode: String = "",
    val role: String = "Maintenance Engineer",
    val shift: String = "Day Shift"
)

@JsonClass(generateAdapter = true)
data class WebSaveMachineReq(
    val id: Long = 0,
    val groupId: Long? = null,
    val assetCode: String,
    val name: String,
    val locationZone: String = "Main Floor",
    val modelInfo: String = "",
    val status: String = "OPERATIONAL",
    val notes: String = ""
)

@JsonClass(generateAdapter = true)
data class WebSaveTaskReq(
    val id: Long = 0,
    val machineId: Long,
    val groupId: Long? = null,
    val title: String,
    val description: String = "",
    val cadence: String = "ONCE",
    val deadlineMillis: Long,
    val estimatedMinutes: Int = 30,
    val personInCharge: String = "",
    val assistantEngineers: String = "",
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean = true,
    val reminderLeadHours: Int = 24,
    val machineStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class WebSparePartDraftReq(
    val partCode: String = "",
    val partName: String = "",
    val partDescription: String = "",
    val fromWarehouse: Boolean = true,
    val quantity: Int = 1,
    val unit: String = "pcs"
)

@JsonClass(generateAdapter = true)
data class WebCompleteTaskReq(
    val personInCharge: String = "",
    val assistantEngineers: List<String> = emptyList(),
    val actualDurationMinutes: Int = 30,
    val notes: String = "",
    val flagMalfunction: Boolean = false,
    val severity: String = "MEDIUM",
    val spareParts: List<WebSparePartDraftReq> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WebRejectTaskReq(
    val personInCharge: String = "",
    val assistantEngineers: List<String> = emptyList(),
    val rejectionReason: String = "",
    val rescheduleNextCycle: Boolean = false
)

@JsonClass(generateAdapter = true)
data class WebSaveTodoReq(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val machineId: Long? = null,
    val groupId: Long? = null,
    val personInCharge: String = "",
    val repeatRule: String = "ONCE",
    val dueAtMillis: Long,
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean = true
)

@JsonClass(generateAdapter = true)
data class WebSaveWarehousePartReq(
    val id: Long = 0,
    val partCode: String,
    val partName: String = "",
    val partDescription: String = "",
    val defaultUnit: String = "pcs"
)

@JsonClass(generateAdapter = true)
data class WebActionResult(
    val ok: Boolean,
    val message: String
)
