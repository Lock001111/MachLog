package com.example.engine

import android.content.Context
import com.example.BuildConfig
import com.example.data.AppPreferences
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.EngineerEntity
import com.example.data.GroupScope
import com.example.data.MachinaDao
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceRepository
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.ServiceEntryType
import com.example.data.Severity
import com.example.data.SparePartDraft
import com.example.data.TaskOutcome
import com.example.data.TodoReminderEntity
import com.example.data.WarehousePartEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object LocalWebServerManager {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _status = MutableStateFlow(WebPortalStatus())
    val status: StateFlow<WebPortalStatus> = _status.asStateFlow()

    @Volatile
    private var pinEnabledFlag: Boolean = false

    @Volatile
    private var pinCodeValue: String = "4821"

    fun updatePinConfig(enabled: Boolean, pin: String) {
        pinEnabledFlag = enabled
        pinCodeValue = pin.ifBlank { "4821" }
        val cur = _status.value
        _status.value = cur.copy(pinEnabled = pinEnabledFlag, pinCode = pinCodeValue)
    }

    @Synchronized
    fun start(
        context: Context,
        dao: MachinaDao,
        repo: MaintenanceRepository,
        prefs: AppPreferences,
        pinEnabled: Boolean,
        pinCode: String
    ) {
        updatePinConfig(pinEnabled, pinCode)
        if (serverSocket != null && acceptJob?.isActive == true) {
            refreshNetworkAddresses()
            return
        }

        val boundSocket = openAvailableSocket()
        if (boundSocket == null) {
            _status.value = WebPortalStatus(
                isRunning = false,
                pinEnabled = pinEnabledFlag,
                pinCode = pinCodeValue,
                errorMessage = "Unable to bind local HTTP port (8080-8090)"
            )
            return
        }

        serverSocket = boundSocket
        val port = boundSocket.localPort
        val ips = discoverLocalIpv4Addresses()
        val urls = if (ips.isEmpty()) listOf("http://127.0.0.1:$port") else ips.map { "http://$it:$port" }

        _status.value = WebPortalStatus(
            isRunning = true,
            port = port,
            urls = urls,
            primaryUrl = urls.firstOrNull(),
            pinEnabled = pinEnabledFlag,
            pinCode = pinCodeValue,
            errorMessage = null
        )

        acceptJob = scope.launch {
            while (isActive && serverSocket?.isClosed == false) {
                val client = runCatching { serverSocket?.accept() }.getOrNull() ?: break
                launch {
                    handleClient(client, context, dao, repo, prefs)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _status.value = WebPortalStatus(
            isRunning = false,
            port = 8080,
            urls = emptyList(),
            primaryUrl = null,
            pinEnabled = pinEnabledFlag,
            pinCode = pinCodeValue,
            errorMessage = null
        )
    }

    fun refreshNetworkAddresses() {
        val sock = serverSocket ?: return
        if (sock.isClosed) return
        val port = sock.localPort
        val ips = discoverLocalIpv4Addresses()
        val urls = if (ips.isEmpty()) listOf("http://127.0.0.1:$port") else ips.map { "http://$it:$port" }
        _status.value = _status.value.copy(
            isRunning = true,
            port = port,
            urls = urls,
            primaryUrl = urls.firstOrNull(),
            pinEnabled = pinEnabledFlag,
            pinCode = pinCodeValue
        )
    }

    private fun openAvailableSocket(): ServerSocket? {
        for (port in 8080..8090) {
            val sock = runCatching { ServerSocket(port) }.getOrNull()
            if (sock != null) return sock
        }
        return runCatching { ServerSocket(0) }.getOrNull()
    }

    fun discoverLocalIpv4Addresses(): List<String> {
        val addresses = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { addresses += it }
                    }
                }
            }
        }
        return addresses.sortedByDescending { it.startsWith("192.168.") || it.startsWith("10.") }
    }

    internal data class HttpRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    internal data class HttpResponse(
        val statusCode: Int,
        val statusText: String,
        val contentType: String,
        val body: ByteArray,
        val extraHeaders: Map<String, String> = emptyMap()
    )

    private suspend fun handleClient(
        socket: Socket,
        context: Context,
        dao: MachinaDao,
        repo: MaintenanceRepository,
        prefs: AppPreferences
    ) {
        socket.use { client ->
            client.soTimeout = 15_000
            val req = readHttpRequest(BufferedInputStream(client.getInputStream())) ?: return
            val resp = routeRequest(req, context, dao, repo, prefs)
            writeHttpResponse(client, resp)
        }
    }

    internal fun readHttpRequest(input: BufferedInputStream): HttpRequest? {
        val headerBytes = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val b = input.read()
            if (b == -1) break
            headerBytes.write(b)
            if ((matched == 0 || matched == 2) && b == '\r'.code) {
                matched++
            } else if ((matched == 1 || matched == 3) && b == '\n'.code) {
                matched++
                if (matched == 4) break
            } else {
                matched = if (b == '\r'.code) 1 else 0
            }
            if (headerBytes.size() > 65_536) return null
        }
        val headerText = headerBytes.toString(Charsets.ISO_8859_1.name())
        val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val requestParts = lines.first().split(' ')
        if (requestParts.size < 2) return null
        val method = requestParts[0].uppercase()
        val rawTarget = requestParts[1]
        val qIdx = rawTarget.indexOf('?')
        val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
        val query = if (qIdx >= 0) parseQueryParams(rawTarget.substring(qIdx + 1)) else emptyMap()

        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 20_000_000) ?: 0
        val body = if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            DataInputStream(input).readFully(buf)
            buf
        } else {
            ByteArray(0)
        }

        return HttpRequest(method = method, path = path, query = query, headers = headers, body = body)
    }

    private fun writeHttpResponse(socket: Socket, resp: HttpResponse) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ${resp.statusCode} ${resp.statusText}\r\n")
        sb.append("Content-Type: ${resp.contentType}\r\n")
        sb.append("Content-Length: ${resp.body.size}\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n")
        sb.append("Access-Control-Allow-Headers: Content-Type, X-Machina-Pin\r\n")
        resp.extraHeaders.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("Connection: close\r\n\r\n")

        val out = socket.getOutputStream()
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(resp.body)
        out.flush()
    }

    private suspend fun routeRequest(
        req: HttpRequest,
        context: Context,
        dao: MachinaDao,
        repo: MaintenanceRepository,
        prefs: AppPreferences
    ): HttpResponse {
        if (req.method == "OPTIONS") {
            return HttpResponse(204, "No Content", "text/plain", ByteArray(0))
        }

        if (req.method == "GET" && (req.path == "/" || req.path == "/index.html")) {
            val htmlBytes = runCatching {
                context.assets.open("web/index.html").use { it.readBytes() }
            }.getOrElse {
                "<h1>MachinaLog Web</h1><p>Missing web/index.html asset.</p>".toByteArray(Charsets.UTF_8)
            }
            return HttpResponse(200, "OK", "text/html; charset=utf-8", htmlBytes)
        }

        val suppliedPin = req.headers["x-machina-pin"] ?: req.query["pin"]
        val authorized = !pinEnabledFlag || suppliedPin == pinCodeValue

        if (req.method == "GET" && req.path == "/api/ping") {
            return jsonResponse(
                200,
                "OK",
                WebPingResponse(
                    ok = true,
                    appName = "MachinaLog",
                    pinRequired = pinEnabledFlag,
                    authorized = authorized
                )
            )
        }

        if (!authorized) {
            return jsonResponse(401, "Unauthorized", WebActionResult(false, "Invalid or missing PIN"))
        }

        return runCatching {
            when {
                req.method == "GET" && req.path == "/api/state" -> {
                    val snapshot = prefs.getSnapshot()
                    val groups = dao.getAllGroupsSync()
                    val machines = dao.getAllMachinesSync()
                    val tasks = dao.getAllTasksSync()
                    val todos = dao.getAllTodosSync()
                    val warehouse = dao.getAllWarehousePartsSync()
                    val engineers = dao.getAllEngineersSync()
                    val logs = dao.getAllServiceLogsWithPartsSync()

                    jsonResponse(
                        200,
                        "OK",
                        WebStateResponse(
                            nowMillis = System.currentTimeMillis(),
                            defaultEngineerName = snapshot.defaultEngineerName,
                            appVersionName = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            githubRepo = AppUpdateChecker.GITHUB_REPO,
                            groups = groups.map {
                                WebGroupDto(it.id, it.uuid, it.name, it.scope.name, it.colorKey)
                            },
                            machines = machines.map {
                                WebMachineDto(it.id, it.uuid, it.groupId, it.assetCode, it.name, it.locationZone, it.modelInfo, it.status.name, it.notes)
                            },
                            tasks = tasks.map {
                                WebTaskDto(it.id, it.uuid, it.machineId, it.groupId, it.title, it.description, it.cadence.name, it.deadlineMillis, it.estimatedMinutes, it.personInCharge, it.assistantEngineers, it.priority.name, it.reminderEnabled, it.reminderLeadHours, it.lastCompletedMillis)
                            },
                            todos = todos.map {
                                WebTodoDto(it.id, it.uuid, it.title, it.notes, it.machineId, it.groupId, it.personInCharge, it.repeatRule.name, it.dueAtMillis, it.reminderEnabled, it.isCompleted, it.priority.name, it.completedAtMillis)
                            },
                            warehouseParts = warehouse.map {
                                WebWarehousePartDto(it.id, it.uuid, it.partCode, it.partName, it.partDescription, it.defaultUnit)
                            },
                            engineers = engineers.map {
                                WebEngineerDto(it.id, it.uuid, it.name, it.employeeCode, it.role, it.shift)
                            },
                            serviceLogs = logs.map { item ->
                                val l = item.log
                                WebServiceLogDto(
                                    id = l.id,
                                    uuid = l.uuid,
                                    machineId = l.machineId,
                                    taskId = l.taskId,
                                    entryType = l.entryType.name,
                                    cadenceSnapshot = l.cadenceSnapshot?.name,
                                    summaryTitle = l.summaryTitle,
                                    malfunctionDetails = l.malfunctionDetails,
                                    severity = l.severity.name,
                                    outcome = l.outcome.name,
                                    rejectionReason = l.rejectionReason,
                                    personInCharge = l.personInCharge,
                                    assistantEngineers = l.assistantEngineers,
                                    serviceDurationMinutes = l.serviceDurationMinutes,
                                    completedAtMillis = l.completedAtMillis,
                                    deadlineWasMet = l.deadlineWasMet,
                                    parts = item.parts.map { p ->
                                        WebSparePartUsedDto(p.partCode, p.partName, p.partDescription, p.fromWarehouse, p.quantity, p.unit)
                                    }
                                )
                            }
                        )
                    )
                }

                // --- Groups ---
                req.method == "POST" && req.path == "/api/groups" -> {
                    val body = decodeJson<WebSaveGroupReq>(req.body) ?: return badRequest()
                    val existing = if (body.id != 0L) dao.getAllGroupsSync().find { it.id == body.id } else null
                    val scope = runCatching { GroupScope.valueOf(body.scope) }.getOrDefault(GroupScope.ALL)
                    repo.saveGroup(
                        CustomGroupEntity(
                            id = existing?.id ?: 0L,
                            uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                            name = body.name.trim(),
                            scope = scope,
                            colorKey = body.colorKey.ifBlank { "COBALT" }
                        )
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Group saved"))
                }
                req.method == "DELETE" && req.path.startsWith("/api/groups/") -> {
                    val id = req.path.removePrefix("/api/groups/").toLongOrNull() ?: return badRequest()
                    repo.deleteGroup(id)
                    jsonResponse(200, "OK", WebActionResult(true, "Group deleted"))
                }

                // --- Machines ---
                req.method == "POST" && req.path == "/api/machines" -> {
                    val body = decodeJson<WebSaveMachineReq>(req.body) ?: return badRequest()
                    val existing = if (body.id != 0L) dao.getMachineById(body.id) else null
                    val status = runCatching { MachineStatus.valueOf(body.status) }.getOrDefault(MachineStatus.OPERATIONAL)
                    repo.saveMachine(
                        MachineEntity(
                            id = existing?.id ?: 0L,
                            uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                            groupId = body.groupId,
                            assetCode = body.assetCode.trim().uppercase(),
                            name = body.name.trim(),
                            locationZone = body.locationZone.trim(),
                            modelInfo = body.modelInfo.trim(),
                            status = status,
                            notes = body.notes.trim()
                        )
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Machine saved"))
                }
                req.method == "DELETE" && req.path.startsWith("/api/machines/") -> {
                    val id = req.path.removePrefix("/api/machines/").toLongOrNull() ?: return badRequest()
                    repo.deleteMachine(id)
                    jsonResponse(200, "OK", WebActionResult(true, "Machine deleted"))
                }

                // --- Engineers ---
                req.method == "POST" && req.path == "/api/engineers" -> {
                    val body = decodeJson<WebSaveEngineerReq>(req.body) ?: return badRequest()
                    val existing = if (body.id != 0L) dao.getEngineerById(body.id) else null
                    repo.saveEngineer(
                        EngineerEntity(
                            id = existing?.id ?: 0L,
                            uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                            name = body.name.trim(),
                            employeeCode = body.employeeCode.trim().uppercase(),
                            role = body.role.trim().ifEmpty { "Maintenance Engineer" },
                            shift = body.shift.trim().ifEmpty { "Day Shift" }
                        )
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Engineer saved"))
                }
                req.method == "DELETE" && req.path.startsWith("/api/engineers/") -> {
                    val id = req.path.removePrefix("/api/engineers/").toLongOrNull() ?: return badRequest()
                    repo.deleteEngineer(id)
                    jsonResponse(200, "OK", WebActionResult(true, "Engineer removed"))
                }

                // --- Maintenance Tasks ---
                req.method == "POST" && req.path.startsWith("/api/tasks/") && req.path.endsWith("/complete") -> {
                    val idStr = req.path.removePrefix("/api/tasks/").removeSuffix("/complete")
                    val id = idStr.toLongOrNull() ?: return badRequest()
                    val body = decodeJson<WebCompleteTaskReq>(req.body) ?: return badRequest()
                    val sev = runCatching { Severity.valueOf(body.severity) }.getOrDefault(Severity.MEDIUM)
                    repo.completeTask(
                        taskId = id,
                        personInCharge = body.personInCharge,
                        assistantEngineers = body.assistantEngineers,
                        actualDurationMinutes = body.actualDurationMinutes,
                        notes = body.notes,
                        spareParts = body.spareParts.map {
                            SparePartDraft(
                                partName = it.partName.ifBlank { it.partCode },
                                partCode = it.partCode,
                                partDescription = it.partDescription,
                                fromWarehouse = it.fromWarehouse,
                                quantity = it.quantity.coerceAtLeast(1),
                                unit = it.unit.ifBlank { "pcs" }
                            )
                        },
                        flagMalfunction = body.flagMalfunction,
                        malfunctionSeverity = sev
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Task completed & report generated"))
                }
                req.method == "POST" && req.path.startsWith("/api/tasks/") && req.path.endsWith("/reject") -> {
                    val idStr = req.path.removePrefix("/api/tasks/").removeSuffix("/reject")
                    val id = idStr.toLongOrNull() ?: return badRequest()
                    val body = decodeJson<WebRejectTaskReq>(req.body) ?: return badRequest()
                    repo.rejectTask(
                        taskId = id,
                        personInCharge = body.personInCharge,
                        assistantEngineers = body.assistantEngineers,
                        rejectionReason = body.rejectionReason,
                        rescheduleNextCycle = body.rescheduleNextCycle
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Task rejected & report recorded"))
                }
                req.method == "POST" && req.path == "/api/tasks" -> {
                    val body = decodeJson<WebSaveTaskReq>(req.body) ?: return badRequest()
                    val existing = if (body.id != 0L) dao.getTaskById(body.id) else null
                    val cadence = runCatching { Cadence.valueOf(body.cadence) }.getOrDefault(Cadence.ONCE)
                    val prio = runCatching { ReminderPriority.valueOf(body.priority) }.getOrDefault(ReminderPriority.NORMAL)
                    repo.saveTask(
                        MaintenanceTaskEntity(
                            id = existing?.id ?: 0L,
                            uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                            machineId = body.machineId,
                            groupId = body.groupId,
                            title = body.title.trim(),
                            description = body.description.trim(),
                            cadence = cadence,
                            deadlineMillis = body.deadlineMillis,
                            estimatedMinutes = body.estimatedMinutes.coerceAtLeast(1),
                            personInCharge = body.personInCharge.trim(),
                            assistantEngineers = body.assistantEngineers.trim(),
                            priority = prio,
                            reminderEnabled = body.reminderEnabled,
                            reminderLeadHours = body.reminderLeadHours.coerceAtLeast(1),
                            lastCompletedMillis = existing?.lastCompletedMillis
                        )
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Task saved"))
                }
                req.method == "DELETE" && req.path.startsWith("/api/tasks/") -> {
                    val id = req.path.removePrefix("/api/tasks/").toLongOrNull() ?: return badRequest()
                    repo.deleteTask(id)
                    jsonResponse(200, "OK", WebActionResult(true, "Task deleted"))
                }

                // --- To-Do Reminders ---
                req.method == "POST" && req.path.startsWith("/api/todos/") && req.path.endsWith("/toggle") -> {
                    val id = req.path.removePrefix("/api/todos/").removeSuffix("/toggle").toLongOrNull() ?: return badRequest()
                    repo.toggleTodoComplete(id)
                    jsonResponse(200, "OK", WebActionResult(true, "Reminder updated"))
                }
                req.method == "POST" && req.path == "/api/todos" -> {
                    val body = decodeJson<WebSaveTodoReq>(req.body) ?: return badRequest()
                    val existing = if (body.id != 0L) dao.getTodoById(body.id) else null
                    val rule = runCatching { RepeatRule.valueOf(body.repeatRule) }.getOrDefault(RepeatRule.ONCE)
                    val prio = runCatching { ReminderPriority.valueOf(body.priority) }.getOrDefault(ReminderPriority.NORMAL)
                    repo.saveTodo(
                        TodoReminderEntity(
                            id = existing?.id ?: 0L,
                            uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                            title = body.title.trim(),
                            notes = body.notes.trim(),
                            machineId = body.machineId,
                            groupId = body.groupId,
                            personInCharge = body.personInCharge.trim(),
                            repeatRule = rule,
                            dueAtMillis = body.dueAtMillis,
                            reminderEnabled = body.reminderEnabled,
                            isCompleted = existing?.isCompleted ?: false,
                            priority = prio,
                            completedAtMillis = existing?.completedAtMillis
                        )
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "To-Do saved"))
                }
                req.method == "DELETE" && req.path.startsWith("/api/todos/") -> {
                    val id = req.path.removePrefix("/api/todos/").toLongOrNull() ?: return badRequest()
                    repo.deleteTodo(id)
                    jsonResponse(200, "OK", WebActionResult(true, "To-Do deleted"))
                }

                // --- Warehouse Parts Catalog ---
                req.method == "POST" && req.path == "/api/warehouse" -> {
                    val body = decodeJson<WebSaveWarehousePartReq>(req.body) ?: return badRequest()
                    val existing = if (body.id != 0L) dao.getAllWarehousePartsSync().find { it.id == body.id } else null
                    repo.saveWarehousePart(
                        WarehousePartEntity(
                            id = existing?.id ?: 0L,
                            uuid = existing?.uuid ?: java.util.UUID.randomUUID().toString(),
                            partCode = body.partCode.trim().uppercase(),
                            partName = body.partName.trim(),
                            partDescription = body.partDescription.trim(),
                            defaultUnit = body.defaultUnit.trim().ifEmpty { "pcs" }
                        )
                    )
                    jsonResponse(200, "OK", WebActionResult(true, "Warehouse spare part saved"))
                }
                req.method == "DELETE" && req.path.startsWith("/api/warehouse/") -> {
                    val id = req.path.removePrefix("/api/warehouse/").toLongOrNull() ?: return badRequest()
                    repo.deleteWarehousePart(id)
                    jsonResponse(200, "OK", WebActionResult(true, "Warehouse spare part deleted"))
                }

                // --- Task History Logs (delete only; creation occurs automatically on Task Done / Reject) ---
                req.method == "DELETE" && req.path.startsWith("/api/logs/") -> {
                    val id = req.path.removePrefix("/api/logs/").toLongOrNull() ?: return badRequest()
                    repo.deleteServiceLog(id)
                    jsonResponse(200, "OK", WebActionResult(true, "History entry deleted"))
                }

                // --- Reports (Excel & PDF Download) ---
                req.method == "GET" && (req.path == "/api/reports/export.xlsx" || req.path == "/api/reports/export.pdf") -> {
                    val isPdf = req.path.endsWith(".pdf")
                    val params = ExportFilterParams(
                        includeTasks = req.query["includeTasks"]?.toBooleanStrictOrNull() ?: true,
                        includeLogs = req.query["includeLogs"]?.toBooleanStrictOrNull() ?: true,
                        machineId = req.query["machineId"]?.toLongOrNull(),
                        customGroupId = req.query["customGroupId"]?.toLongOrNull(),
                        personInCharge = req.query["personInCharge"]?.takeIf { it.isNotBlank() },
                        cadence = req.query["cadence"]?.let { runCatching { Cadence.valueOf(it) }.getOrNull() },
                        entryType = req.query["entryType"]?.let { runCatching { ServiceEntryType.valueOf(it) }.getOrNull() },
                        outcome = req.query["outcome"]?.let { runCatching { TaskOutcome.valueOf(it) }.getOrNull() },
                        startMillis = req.query["startMillis"]?.toLongOrNull(),
                        endMillis = req.query["endMillis"]?.toLongOrNull()
                    )
                    val dataset = ReportExportEngine.filterDataset(
                        params = params,
                        machines = dao.getAllMachinesSync(),
                        groups = dao.getAllGroupsSync(),
                        allTasks = dao.getAllTasksSync(),
                        allLogs = dao.getAllServiceLogsWithPartsSync()
                    )
                    val baos = ByteArrayOutputStream()
                    if (isPdf) {
                        ReportExportEngine.writePdf(baos, params, dataset)
                    } else {
                        ReportExportEngine.writeXlsx(baos, params, dataset)
                    }
                    val stamp = Formatters.formatFileStamp()
                    val ext = if (isPdf) "pdf" else "xlsx"
                    val mime = if (isPdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    HttpResponse(
                        statusCode = 200,
                        statusText = "OK",
                        contentType = mime,
                        body = baos.toByteArray(),
                        extraHeaders = mapOf("Content-Disposition" to "attachment; filename=\"MachinaLog_Report_$stamp.$ext\"")
                    )
                }

                // --- Excel Import & Starter Template ---
                req.method == "GET" && req.path == "/api/excel/template.xlsx" -> {
                    val baos = ByteArrayOutputStream()
                    ExcelImportEngine.writeImportTemplateXlsx(baos)
                    HttpResponse(
                        statusCode = 200,
                        statusText = "OK",
                        contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        body = baos.toByteArray(),
                        extraHeaders = mapOf("Content-Disposition" to "attachment; filename=\"MachinaLog_Import_Template.xlsx\"")
                    )
                }
                req.method == "POST" && req.path == "/api/excel/import" -> {
                    val replace = req.query["replace"]?.toBooleanStrictOrNull() ?: false
                    val defaultEng = prefs.getSnapshot().defaultEngineerName
                    val preview = ExcelImportEngine.parseXlsx(ByteArrayInputStream(req.body), defaultEng)
                    if (preview.totalRecords == 0) {
                        jsonResponse(400, "Bad Request", WebActionResult(false, preview.warnings.firstOrNull() ?: "No valid rows found in Excel file"))
                    } else {
                        val result = ExcelImportEngine.commitImport(dao, prefs, preview, replace)
                        val missingNote = if (result.summary.missingMachineCodesForTasks.isNotEmpty()) {
                            " · Unknown machine codes skipped: ${result.summary.missingMachineCodesForTasks.joinToString(", ")}"
                        } else ""
                        val msg = if (replace) {
                            "Replaced all records (${result.summary.totalNew} imported)$missingNote"
                        } else {
                            "Excel Smart Merge: ${result.formatSummaryMessage()}$missingNote"
                        }
                        jsonResponse(200, "OK", WebActionResult(true, msg))
                    }
                }

                // --- Backup JSON Download & Upload Restore ---
                req.method == "GET" && req.path == "/api/backup/export.json" -> {
                    val json = BackupSyncManager.exportJsonString(dao)
                    val stamp = Formatters.formatFileStamp()
                    HttpResponse(
                        statusCode = 200,
                        statusText = "OK",
                        contentType = "application/json; charset=utf-8",
                        body = json.toByteArray(Charsets.UTF_8),
                        extraHeaders = mapOf("Content-Disposition" to "attachment; filename=\"machinalog_backup_$stamp.json\"")
                    )
                }
                req.method == "POST" && req.path == "/api/backup/import" -> {
                    val mode = if (req.query["mode"]?.uppercase() == "REPLACE") RestoreMode.REPLACE else RestoreMode.MERGE
                    val pkg = BackupSyncManager.parseBackupJson(req.body.toString(Charsets.UTF_8))
                        ?: return jsonResponse(400, "Bad Request", WebActionResult(false, "Invalid MachinaLog backup JSON"))
                    BackupSyncManager.restoreBackup(dao, prefs, pkg, mode)
                    jsonResponse(200, "OK", WebActionResult(true, "Backup restored (${mode.name})"))
                }

                else -> jsonResponse(404, "Not Found", WebActionResult(false, "Unknown endpoint"))
            }
        }.getOrElse { err ->
            jsonResponse(500, "Internal Server Error", WebActionResult(false, err.localizedMessage ?: "Server error"))
        }
    }

    private inline fun <reified T> decodeJson(bytes: ByteArray): T? {
        return runCatching {
            val adapter = moshi.adapter(T::class.java)
            adapter.fromJson(bytes.toString(Charsets.UTF_8))
        }.getOrNull()
    }

    private inline fun <reified T> jsonResponse(code: Int, text: String, payload: T): HttpResponse {
        val adapter = moshi.adapter(T::class.java)
        return HttpResponse(
            statusCode = code,
            statusText = text,
            contentType = "application/json; charset=utf-8",
            body = adapter.toJson(payload).toByteArray(Charsets.UTF_8)
        )
    }

    private fun badRequest(): HttpResponse =
        jsonResponse(400, "Bad Request", WebActionResult(false, "Invalid request parameters"))

    private fun parseQueryParams(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val k = runCatching { URLDecoder.decode(pair.substring(0, eq), "UTF-8") }.getOrDefault(pair.substring(0, eq))
                    val v = runCatching { URLDecoder.decode(pair.substring(eq + 1), "UTF-8") }.getOrDefault(pair.substring(eq + 1))
                    k to v
                } else null
            }
            .toMap()
    }
}
