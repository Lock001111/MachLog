package com.example

import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceRepository
import com.example.data.MaintenanceTaskEntity
import com.example.data.RepeatRule
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogEntity
import com.example.data.ServiceLogWithParts
import com.example.data.SparePartUsedEntity
import com.example.data.WarehousePartEntity
import com.example.engine.BackupSyncManager
import com.example.engine.ExcelImportEngine
import com.example.engine.ExcelImportPreview
import com.example.engine.ExportFilterParams
import com.example.engine.ImportedMachineRow
import com.example.engine.ImportedTaskRow
import com.example.engine.ImportedWarehousePartRow
import com.example.engine.LocalWebServerManager
import com.example.engine.PortableBackupPackage
import com.example.engine.ReportExportEngine
import com.example.data.MachineStatus
import com.example.data.ReminderPriority
import com.example.data.TaskOutcome
import com.example.data.formatEngineerList
import com.example.data.parseEngineerList
import com.example.engine.AppUpdateChecker
import com.example.engine.Formatters
import com.example.ui.screens.filterWarehouseParts
import com.example.ui.screens.paginateItems
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.zip.ZipInputStream

class MachinaLogicTest {

    @Test
    fun `recurring cadence rollover advances into future while preserving time`() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 10, 8, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 12, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val nextDaily = MaintenanceRepository.computeNextCadenceMillis(baseCal.timeInMillis, nowCal.timeInMillis, Cadence.DAILY)
        assertTrue(nextDaily > nowCal.timeInMillis)
        val resCal = Calendar.getInstance().apply { timeInMillis = nextDaily }
        assertEquals(8, resCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, resCal.get(Calendar.MINUTE))

        val nextYearly = MaintenanceRepository.computeNextRepeatMillis(baseCal.timeInMillis, nowCal.timeInMillis, RepeatRule.YEARLY)
        val yearCal = Calendar.getInstance().apply { timeInMillis = nextYearly }
        assertEquals(2027, yearCal.get(Calendar.YEAR))
    }

    @Test
    fun `excel export creates valid multi-sheet xlsx zip package with parameter filters`() {
        val group = CustomGroupEntity(id = 1L, name = "Line A")
        val m1 = MachineEntity(id = 10L, groupId = 1L, assetCode = "MCH-10", name = "Lathe", locationZone = "Bay 1", modelInfo = "L-10")
        val m2 = MachineEntity(id = 20L, groupId = null, assetCode = "MCH-20", name = "Compressor", locationZone = "Utility", modelInfo = "C-20")
        val t1 = MaintenanceTaskEntity(id = 100L, machineId = 10L, title = "Oil Check", cadence = Cadence.DAILY, deadlineMillis = 1700000000000L, personInCharge = "Elena")
        val t2 = MaintenanceTaskEntity(id = 101L, machineId = 20L, title = "Belt Swap", cadence = Cadence.WEEKLY, deadlineMillis = 1700000000000L, personInCharge = "Marcus")

        val log1 = ServiceLogWithParts(
            log = ServiceLogEntity(
                id = 500L,
                machineId = 10L,
                entryType = ServiceEntryType.MALFUNCTION_REPAIR,
                summaryTitle = "Coolant Leak",
                malfunctionDetails = "Replaced cracked hose",
                personInCharge = "Elena",
                serviceDurationMinutes = 45,
                completedAtMillis = 1700000000000L
            ),
            parts = listOf(
                SparePartUsedEntity(
                    id = 1L,
                    serviceLogId = 500L,
                    partName = "Coolant Hose",
                    partCode = "WH-CH-10",
                    partDescription = "Braided EPDM high-pressure 12mm coolant line",
                    fromWarehouse = true,
                    quantity = 2,
                    unit = "pcs"
                )
            )
        )

        val params = ExportFilterParams(personInCharge = "Elena")
        val dataset = ReportExportEngine.filterDataset(params, listOf(m1, m2), listOf(group), listOf(t1, t2), listOf(log1))
        assertEquals(1, dataset.tasks.size)
        assertEquals(1, dataset.logs.size)

        val baos = ByteArrayOutputStream()
        ReportExportEngine.writeXlsx(baos, params, dataset)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet4.xml"))
        val sheet4 = entries["xl/worksheets/sheet4.xml"].orEmpty()
        assertTrue(sheet4.contains("Spare Part ID / No."))
        assertTrue(sheet4.contains("Spare Part Description"))
        assertTrue(sheet4.contains("From Warehouse"))
        assertTrue(sheet4.contains("WH-CH-10"))
        assertTrue(sheet4.contains("Braided EPDM high-pressure 12mm coolant line"))
    }

    @Test
    fun `excel import engine parses generated template workbook accurately`() {
        val baos = ByteArrayOutputStream()
        ExcelImportEngine.writeImportTemplateXlsx(baos)

        val preview = ExcelImportEngine.parseXlsx(
            inputStream = ByteArrayInputStream(baos.toByteArray()),
            defaultEngineer = "Alex Rivera"
        )
        assertEquals(2, preview.groups.size)
        assertEquals(2, preview.machines.size)
        assertEquals(2, preview.tasks.size)
        assertEquals(2, preview.todos.size)
        assertEquals(2, preview.warehouseParts.size)
        assertEquals("MCH-601", preview.machines.first().assetCode)
        assertEquals(Cadence.MONTHLY, preview.tasks[1].cadence)
        assertEquals(RepeatRule.WEEKLY, preview.todos[1].repeatRule)
        assertEquals("TR-NZL-15", preview.warehouseParts.first().partCode)
    }

    @Test
    fun `warehouse catalog filter matches multi-token queries across code and description`() {
        val parts = listOf(
            WarehousePartEntity(id = 1L, partCode = "BRG-6205-2RS", partName = "Deep Groove Bearing", partDescription = "SKF 25x52x15mm double-sealed conveyor bearing"),
            WarehousePartEntity(id = 2L, partCode = "HF-40-10UM", partName = "Hydraulic Return Filter", partDescription = "10-micron spin-on return tank filter"),
            WarehousePartEntity(id = 3L, partCode = "SOL-24DC-52", partName = "Pneumatic Solenoid", partDescription = "24V DC NAMUR directional valve")
        )

        val byCode = filterWarehouseParts(parts, "brg 6205")
        assertEquals(1, byCode.size)
        assertEquals("BRG-6205-2RS", byCode.first().partCode)

        val byDesc = filterWarehouseParts(parts, "10-micron tank")
        assertEquals(1, byDesc.size)
        assertEquals("HF-40-10UM", byDesc.first().partCode)

        assertEquals(3, filterWarehouseParts(parts, "   ").size)
    }

    @Test
    fun `backup json parser decodes empty portable package`() {
        val sampleJson = """
            {
              "schemaVersion": 1,
              "exportedAtMillis": 1700000000000,
              "deviceLabel": "Pixel Tablet",
              "groups": [],
              "machines": [],
              "tasks": [],
              "todos": [],
              "serviceLogs": [],
              "warehouseParts": [
                {
                  "uuid": "wp-1",
                  "partCode": "HF-40-10UM",
                  "partName": "Hydraulic Return Filter",
                  "partDescription": "10-micron return element",
                  "defaultUnit": "pcs",
                  "updatedAtMillis": 1700000000000
                }
              ]
            }
        """.trimIndent()
        val parsed: PortableBackupPackage? = BackupSyncManager.parseBackupJson(sampleJson)
        assertNotNull(parsed)
        assertEquals("Pixel Tablet", parsed?.deviceLabel)
        assertEquals(1, parsed?.warehouseParts?.size)
        assertEquals("HF-40-10UM", parsed?.warehouseParts?.firstOrNull()?.partCode)
    }

    @Test
    fun `local web server http parser handles query strings and exact binary content length`() {
        val binaryPayload = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x7F, -128)
        val header = buildString {
            append("POST /api/excel/import?replace=true&pin=4821 HTTP/1.1\r\n")
            append("Host: 192.168.1.42:8080\r\n")
            append("X-Machina-Pin: 4821\r\n")
            append("Content-Length: ${binaryPayload.size}\r\n")
            append("\r\n")
        }.toByteArray(Charsets.ISO_8859_1)

        val stream = BufferedInputStream(ByteArrayInputStream(header + binaryPayload))
        val parsed = LocalWebServerManager.readHttpRequest(stream)
        assertNotNull(parsed)
        assertEquals("POST", parsed?.method)
        assertEquals("/api/excel/import", parsed?.path)
        assertEquals("true", parsed?.query?.get("replace"))
        assertEquals("4821", parsed?.headers?.get("x-machina-pin"))
        assertArrayEquals(binaryPayload, parsed?.body)

        // Ensure network discovery safely returns non-null list
        val ips = LocalWebServerManager.discoverLocalIpv4Addresses()
        assertNotNull(ips)
    }

    @Test
    fun `excel diff summary detects new and modified machines, tasks, and warehouse parts while skipping duplicates`() {
        val existingMachines = listOf(
            MachineEntity(
                id = 10L,
                assetCode = "MCH-100",
                name = "CNC Lathe A",
                locationZone = "Bay 1",
                modelInfo = "Haas ST-10",
                status = MachineStatus.OPERATIONAL,
                notes = "Primary lathe"
            )
        )
        val existingTasks = listOf(
            MaintenanceTaskEntity(
                id = 20L,
                machineId = 10L,
                title = "Check Spindle Coolant",
                description = "Inspect coolant level",
                cadence = Cadence.DAILY,
                deadlineMillis = 1700000000000L,
                estimatedMinutes = 15,
                personInCharge = "Elena",
                reminderEnabled = true
            ),
            MaintenanceTaskEntity(
                id = 21L,
                machineId = 10L,
                title = "Filter Flush",
                description = "Old description",
                cadence = Cadence.WEEKLY,
                deadlineMillis = 1700000000000L,
                estimatedMinutes = 30,
                personInCharge = "Elena",
                reminderEnabled = true
            )
        )
        val existingParts = listOf(
            WarehousePartEntity(
                id = 30L,
                partCode = "BRG-6205",
                partName = "Bearing 6205",
                partDescription = "Standard sealed bearing",
                defaultUnit = "pcs"
            ),
            WarehousePartEntity(
                id = 31L,
                partCode = "FLT-10",
                partName = "Return Filter",
                partDescription = "25-micron filter",
                defaultUnit = "pcs"
            )
        )

        val preview = ExcelImportPreview(
            groups = emptyList(),
            machines = listOf(
                // Exact duplicate of MCH-100 (plus extra duplicate row inside workbook) -> both skipped
                ImportedMachineRow("mch-100", "CNC Lathe A", "Bay 1", "Haas ST-10", MachineStatus.OPERATIONAL, "", "Primary lathe"),
                ImportedMachineRow("MCH-100", "CNC Lathe A", "Bay 1", "Haas ST-10", MachineStatus.OPERATIONAL, "", "Primary lathe"),
                // Brand new machine -> 1 new
                ImportedMachineRow("MCH-200", "Hydraulic Press", "Bay 2", "Schuler 200T", MachineStatus.OPERATIONAL, "", "")
            ),
            tasks = listOf(
                // Identical task (without explicit deadline column) -> skipped duplicate
                ImportedTaskRow("MCH-100", "Check Spindle Coolant", "Inspect coolant level", Cadence.DAILY, 9999999999999L, 15, "Elena", groupName = "", reminderEnabled = true, hasExplicitDeadline = false),
                // Changed description & PIC -> 1 updated
                ImportedTaskRow("MCH-100", "Filter Flush", "Updated ultrasonic flush", Cadence.WEEKLY, 1700000000000L, 30, "Marcus", groupName = "", reminderEnabled = true, hasExplicitDeadline = true),
                // Brand new task on existing machine -> 1 new
                ImportedTaskRow("MCH-100", "Axis Grease", "Lubricate ballscrews", Cadence.MONTHLY, 1700000000000L, 45, "Elena", groupName = "", reminderEnabled = true, hasExplicitDeadline = true),
                // Brand new task on newly imported machine -> 1 new
                ImportedTaskRow("MCH-200", "Ram Seal Check", "Verify zero drip", Cadence.DAILY, 1700000000000L, 20, "Marcus", groupName = "", reminderEnabled = true, hasExplicitDeadline = true)
            ),
            todos = emptyList(),
            warehouseParts = listOf(
                // Identical duplicate -> skipped
                ImportedWarehousePartRow("BRG-6205", "Bearing 6205", "Standard sealed bearing", "pcs"),
                // Changed description -> 1 updated
                ImportedWarehousePartRow("flt-10", "Return Filter", "10-micron upgraded filter", "pcs"),
                // Brand new part -> 1 new
                ImportedWarehousePartRow("BELT-A42", "Drive Belt A42", "Kevlar V-belt", "pcs")
            ),
            warnings = emptyList()
        )

        val diff = ExcelImportEngine.computeDiffSummary(
            preview = preview,
            existingGroups = emptyList(),
            existingMachines = existingMachines,
            existingTasks = existingTasks,
            existingTodos = emptyList(),
            existingWarehouseParts = existingParts
        )

        assertEquals(1, diff.machines.newCount)
        assertEquals(0, diff.machines.updatedCount)
        assertEquals(2, diff.machines.unchangedSkippedCount)

        assertEquals(2, diff.tasks.newCount)
        assertEquals(1, diff.tasks.updatedCount)
        assertEquals(1, diff.tasks.unchangedSkippedCount)

        assertEquals(1, diff.warehouseParts.newCount)
        assertEquals(1, diff.warehouseParts.updatedCount)
        assertEquals(1, diff.warehouseParts.unchangedSkippedCount)

        assertEquals(4, diff.totalNew)
        assertEquals(2, diff.totalUpdated)
        assertEquals(4, diff.totalSkippedDuplicates)
    }

    @Test
    fun `engineer list helpers and lead time formatter behave accurately`() {
        val parsed = parseEngineerList(" Marcus Chen , Elena Vance, marcus chen , , Priya Nair ")
        assertEquals(listOf("Marcus Chen", "Elena Vance", "marcus chen", "Priya Nair"), parsed)

        val formatted = formatEngineerList(
            names = listOf("Elena Vance", "Marcus Chen", "Priya Nair", "Marcus Chen"),
            excludePic = "elena vance"
        )
        assertEquals("Marcus Chen, Priya Nair", formatted)

        assertEquals("1 hour", Formatters.formatLeadTime(1))
        assertEquals("12 hours", Formatters.formatLeadTime(12))
        assertEquals("1 day", Formatters.formatLeadTime(24))
        assertEquals("3 days", Formatters.formatLeadTime(72))
        assertEquals("36 hours", Formatters.formatLeadTime(36))
    }

    @Test
    fun `report filter and xlsx export include outcome, rejection reason, and assistant engineers`() {
        val m1 = MachineEntity(id = 10L, assetCode = "MCH-10", name = "Lathe", locationZone = "Bay 1", modelInfo = "L-10")
        val doneLog = ServiceLogWithParts(
            log = ServiceLogEntity(
                id = 1L,
                machineId = 10L,
                entryType = ServiceEntryType.PREVENTIVE_SERVICE,
                outcome = TaskOutcome.DONE,
                cadenceSnapshot = Cadence.ONCE,
                summaryTitle = "Spindle Alignment",
                malfunctionDetails = "Aligned within 0.01mm",
                personInCharge = "Elena Vance",
                assistantEngineers = "Marcus Chen, Priya Nair",
                serviceDurationMinutes = 50,
                completedAtMillis = 1700000000000L
            )
        )
        val rejectedLog = ServiceLogWithParts(
            log = ServiceLogEntity(
                id = 2L,
                machineId = 10L,
                entryType = ServiceEntryType.PREVENTIVE_SERVICE,
                outcome = TaskOutcome.REJECTED,
                cadenceSnapshot = Cadence.WEEKLY,
                summaryTitle = "Hydraulic Flush",
                rejectionReason = "Machine running priority batch",
                personInCharge = "Marcus Chen",
                assistantEngineers = "Elena Vance",
                serviceDurationMinutes = 0,
                completedAtMillis = 1700005000000L
            )
        )

        val onlyRejected = ReportExportEngine.filterDataset(
            params = ExportFilterParams(outcome = TaskOutcome.REJECTED),
            machines = listOf(m1),
            groups = emptyList(),
            allTasks = emptyList(),
            allLogs = listOf(doneLog, rejectedLog)
        )
        assertEquals(1, onlyRejected.logs.size)
        assertEquals(TaskOutcome.REJECTED, onlyRejected.logs.first().log.outcome)

        val baos = ByteArrayOutputStream()
        ReportExportEngine.writeXlsx(baos, ExportFilterParams(outcome = TaskOutcome.REJECTED), onlyRejected)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(baos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }
        val sheet3 = entries["xl/worksheets/sheet3.xml"].orEmpty()
        assertTrue(sheet3.contains("Rejected"))
        assertTrue(sheet3.contains("Machine running priority batch"))
        assertTrue(sheet3.contains("Elena Vance"))
    }

    @Test
    fun `backup v2 json parser decodes engineers, outcomes, and task reminder lead hours`() {
        val sampleJson = """
            {
              "schemaVersion": 2,
              "exportedAtMillis": 1700000000000,
              "deviceLabel": "Pixel Tablet",
              "groups": [],
              "machines": [],
              "engineers": [
                {
                  "uuid": "eng-1",
                  "name": "Elena Vance",
                  "employeeCode": "ENG-01",
                  "role": "Lead Engineer",
                  "shift": "Day Shift",
                  "updatedAtMillis": 1700000000000
                }
              ],
              "tasks": [
                {
                  "uuid": "tsk-1",
                  "machineUuid": "m-1",
                  "title": "Check Valve",
                  "description": "",
                  "cadence": "ONCE",
                  "deadlineMillis": 1700010000000,
                  "estimatedMinutes": 20,
                  "personInCharge": "Elena Vance",
                  "assistantEngineers": "Marcus Chen",
                  "reminderEnabled": true,
                  "reminderLeadHours": 48,
                  "updatedAtMillis": 1700000000000
                }
              ],
              "todos": [],
              "serviceLogs": [
                {
                  "uuid": "log-1",
                  "machineUuid": "m-1",
                  "entryType": "PREVENTIVE_SERVICE",
                  "outcome": "REJECTED",
                  "rejectionReason": "Safety lockout active",
                  "summaryTitle": "Check Valve",
                  "malfunctionDetails": "",
                  "severity": "LOW",
                  "personInCharge": "Elena Vance",
                  "assistantEngineers": "Marcus Chen",
                  "serviceDurationMinutes": 0,
                  "completedAtMillis": 1700005000000,
                  "deadlineWasMet": false,
                  "updatedAtMillis": 1700005000000,
                  "spareParts": []
                }
              ],
              "warehouseParts": []
            }
        """.trimIndent()

        val parsed = BackupSyncManager.parseBackupJson(sampleJson)
        assertNotNull(parsed)
        assertEquals(2, parsed?.schemaVersion)
        assertEquals(1, parsed?.engineers?.size)
        assertEquals("Elena Vance", parsed?.engineers?.firstOrNull()?.name)
        assertEquals(48, parsed?.tasks?.firstOrNull()?.reminderLeadHours)
        assertEquals("ONCE", parsed?.tasks?.firstOrNull()?.cadence)
        assertEquals("REJECTED", parsed?.serviceLogs?.firstOrNull()?.outcome)
        assertEquals("Safety lockout active", parsed?.serviceLogs?.firstOrNull()?.rejectionReason)
        assertEquals("Marcus Chen", parsed?.serviceLogs?.firstOrNull()?.assistantEngineers)
    }

    @Test
    fun `app update version comparator detects newer tags accurately`() {
        assertTrue(AppUpdateChecker.isRemoteVersionNewer("v1.0.1", "1.0", 1))
        assertTrue(AppUpdateChecker.isRemoteVersionNewer("v1.2.0", "1.0", 1))
        assertTrue(AppUpdateChecker.isRemoteVersionNewer("2.0", "1.9.9", 10))
        assertFalse(AppUpdateChecker.isRemoteVersionNewer("v1.0", "1.0", 1))
        assertFalse(AppUpdateChecker.isRemoteVersionNewer("v1.0.0", "1.0", 1))
        assertFalse(AppUpdateChecker.isRemoteVersionNewer("v0.9.5", "1.0", 1))
    }

    @Test
    fun `warehouse search picker enforces 3 character minimum threshold`() {
        val parts = listOf(
            WarehousePartEntity(id = 1L, partCode = "BRG-6205-2RS", partName = "Deep Groove Bearing", partDescription = "SKF 25x52x15mm conveyor bearing"),
            WarehousePartEntity(id = 2L, partCode = "HF-40-10UM", partName = "Hydraulic Return Filter", partDescription = "10-micron return tank filter")
        )
        assertTrue(filterWarehouseParts(parts, "", minQueryLength = 3).isEmpty())
        assertTrue(filterWarehouseParts(parts, "b", minQueryLength = 3).isEmpty())
        assertTrue(filterWarehouseParts(parts, "br", minQueryLength = 3).isEmpty())

        val matched = filterWarehouseParts(parts, "brg", minQueryLength = 3)
        assertEquals(1, matched.size)
        assertEquals("BRG-6205-2RS", matched.first().partCode)
    }

    @Test
    fun `paginateItems supports 25, 50, and 100 per page with boundary clamping`() {
        val items = (1..120).toList()

        val page1Size25 = paginateItems(items, pageIndex = 0, pageSize = 25)
        assertEquals(5, page1Size25.totalPages)
        assertEquals(1, page1Size25.startNumber)
        assertEquals(25, page1Size25.endNumber)
        assertEquals(25, page1Size25.items.size)

        val lastPageSize25 = paginateItems(items, pageIndex = 99, pageSize = 25)
        assertEquals(4, lastPageSize25.safePageIndex)
        assertEquals(101, lastPageSize25.startNumber)
        assertEquals(120, lastPageSize25.endNumber)
        assertEquals(20, lastPageSize25.items.size)

        val pageSize50 = paginateItems(items, pageIndex = 1, pageSize = 50)
        assertEquals(3, pageSize50.totalPages)
        assertEquals(51, pageSize50.startNumber)
        assertEquals(100, pageSize50.endNumber)

        val pageSize100 = paginateItems(items, pageIndex = 1, pageSize = 100)
        assertEquals(2, pageSize100.totalPages)
        assertEquals(101, pageSize100.startNumber)
        assertEquals(120, pageSize100.endNumber)

        val emptyPaged = paginateItems(emptyList<Int>(), pageIndex = 0, pageSize = 25)
        assertEquals(0, emptyPaged.totalItems)
        assertEquals(1, emptyPaged.totalPages)
    }

    @Test
    fun `task priority is preserved in excel import template and backup json`() {
        val baos = ByteArrayOutputStream()
        ExcelImportEngine.writeImportTemplateXlsx(baos)
        val preview = ExcelImportEngine.parseXlsx(
            inputStream = ByteArrayInputStream(baos.toByteArray()),
            defaultEngineer = "Alex Rivera"
        )
        assertEquals(2, preview.tasks.size)
        assertEquals(ReminderPriority.HIGH, preview.tasks[0].priority)

        val jsonWithPrio = """
            {
              "schemaVersion": 2,
              "exportedAtMillis": 1700000000000,
              "deviceLabel": "Pixel Tablet",
              "groups": [],
              "machines": [],
              "engineers": [],
              "tasks": [
                {
                  "uuid": "tsk-prio",
                  "machineUuid": "m-1",
                  "title": "Emergency Motor Inspection",
                  "description": "",
                  "cadence": "DAILY",
                  "priority": "URGENT",
                  "deadlineMillis": 1700010000000,
                  "estimatedMinutes": 30,
                  "personInCharge": "Elena Vance",
                  "reminderEnabled": true,
                  "reminderLeadHours": 12,
                  "updatedAtMillis": 1700000000000
                }
              ],
              "todos": [],
              "serviceLogs": [],
              "warehouseParts": []
            }
        """.trimIndent()
        val decoded = BackupSyncManager.parseBackupJson(jsonWithPrio)
        assertEquals("URGENT", decoded?.tasks?.firstOrNull()?.priority)
    }
}
