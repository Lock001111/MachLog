package com.example.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.MachineEntity
import com.example.data.MaintenanceTaskEntity
import com.example.data.ServiceEntryType
import com.example.data.ServiceLogWithParts
import com.example.data.TaskOutcome
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportFilterParams(
    val includeTasks: Boolean = true,
    val includeLogs: Boolean = true,
    val machineId: Long? = null,
    val customGroupId: Long? = null,
    val personInCharge: String? = null,
    val cadence: Cadence? = null,
    val entryType: ServiceEntryType? = null,
    val outcome: TaskOutcome? = null,
    val startMillis: Long? = null,
    val endMillis: Long? = null
)

data class ExportDataset(
    val machinesById: Map<Long, MachineEntity>,
    val groupsById: Map<Long, CustomGroupEntity>,
    val tasks: List<MaintenanceTaskEntity>,
    val logs: List<ServiceLogWithParts>
)

object ReportExportEngine {

    fun filterDataset(
        params: ExportFilterParams,
        machines: List<MachineEntity>,
        groups: List<CustomGroupEntity>,
        allTasks: List<MaintenanceTaskEntity>,
        allLogs: List<ServiceLogWithParts>
    ): ExportDataset {
        val machineMap = machines.associateBy { it.id }
        val groupMap = groups.associateBy { it.id }

        val filteredTasks = if (!params.includeTasks) emptyList() else {
            allTasks.filter { task ->
                val machine = machineMap[task.machineId]
                val effectiveGroupId = task.groupId ?: machine?.groupId
                val matchesMachine = params.machineId == null || task.machineId == params.machineId
                val matchesGroup = params.customGroupId == null || effectiveGroupId == params.customGroupId
                val matchesPic = params.personInCharge.isNullOrBlank() ||
                    task.personInCharge.equals(params.personInCharge, ignoreCase = true)
                val matchesCadence = params.cadence == null || task.cadence == params.cadence
                val matchesStart = params.startMillis == null || task.deadlineMillis >= params.startMillis
                val matchesEnd = params.endMillis == null || task.deadlineMillis <= params.endMillis
                matchesMachine && matchesGroup && matchesPic && matchesCadence && matchesStart && matchesEnd
            }
        }

        val filteredLogs = if (!params.includeLogs) emptyList() else {
            allLogs.filter { item ->
                val log = item.log
                val machine = machineMap[log.machineId]
                val matchesMachine = params.machineId == null || log.machineId == params.machineId
                val matchesGroup = params.customGroupId == null || machine?.groupId == params.customGroupId
                val matchesPic = params.personInCharge.isNullOrBlank() ||
                    log.personInCharge.equals(params.personInCharge, ignoreCase = true)
                val matchesCadence = params.cadence == null || log.cadenceSnapshot == params.cadence
                val matchesType = params.entryType == null || log.entryType == params.entryType
                val matchesOutcome = params.outcome == null || log.outcome == params.outcome
                val matchesStart = params.startMillis == null || log.completedAtMillis >= params.startMillis
                val matchesEnd = params.endMillis == null || log.completedAtMillis <= params.endMillis
                matchesMachine && matchesGroup && matchesPic && matchesCadence && matchesType && matchesOutcome && matchesStart && matchesEnd
            }
        }

        return ExportDataset(
            machinesById = machineMap,
            groupsById = groupMap,
            tasks = filteredTasks,
            logs = filteredLogs
        )
    }

    fun exportToCacheFile(
        context: Context,
        fileName: String,
        isPdf: Boolean,
        params: ExportFilterParams,
        dataset: ExportDataset
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            if (isPdf) {
                writePdf(out, params, dataset)
            } else {
                writeXlsx(out, params, dataset)
            }
        }
        return file
    }

    fun writeXlsx(out: OutputStream, params: ExportFilterParams, dataset: ExportDataset) {
        val now = System.currentTimeMillis()
        val totalMinutes = dataset.logs.sumOf { it.log.serviceDurationMinutes }
        val doneCount = dataset.logs.count { it.log.outcome == TaskOutcome.DONE }
        val rejectedCount = dataset.logs.count { it.log.outcome == TaskOutcome.REJECTED }
        val malfunctionsCount = dataset.logs.count { it.log.entryType == ServiceEntryType.MALFUNCTION_REPAIR }
        val totalPartsQty = dataset.logs.sumOf { item -> item.parts.sumOf { it.quantity } }

        val summaryRows = mutableListOf<List<CellValue>>(
            listOf(CellValue.Text("MachinaLog Maintenance & Service Report"), CellValue.Text("")),
            listOf(CellValue.Text("Generated At"), CellValue.Text(Formatters.formatIsoDateTime(now))),
            listOf(CellValue.Text("Machine Filter"), CellValue.Text(params.machineId?.let { dataset.machinesById[it]?.let { m -> "${m.assetCode} ${m.name}" } } ?: "All Machines")),
            listOf(CellValue.Text("Group Filter"), CellValue.Text(params.customGroupId?.let { dataset.groupsById[it]?.name } ?: "All Groups")),
            listOf(CellValue.Text("Person In Charge"), CellValue.Text(params.personInCharge?.takeIf { it.isNotBlank() } ?: "All Engineers")),
            listOf(CellValue.Text("Cadence Filter"), CellValue.Text(params.cadence?.label ?: "All Cadences")),
            listOf(CellValue.Text("Outcome Filter"), CellValue.Text(params.outcome?.label ?: "All Outcomes (Done & Rejected)")),
            listOf(CellValue.Text("Entry Type"), CellValue.Text(params.entryType?.label ?: "All Types")),
            listOf(CellValue.Text("Period Start"), CellValue.Text(params.startMillis?.let { Formatters.formatDateOnly(it) } ?: "Beginning")),
            listOf(CellValue.Text("Period End"), CellValue.Text(params.endMillis?.let { Formatters.formatDateOnly(it) } ?: "Present")),
            listOf(CellValue.Text("KPI Metric"), CellValue.Text("Value")),
            listOf(CellValue.Text("Active Scheduled Tasks Matched"), CellValue.Num(dataset.tasks.size.toDouble())),
            listOf(CellValue.Text("Machine Reports Matched"), CellValue.Num(dataset.logs.size.toDouble())),
            listOf(CellValue.Text("Tasks Completed (Done)"), CellValue.Num(doneCount.toDouble())),
            listOf(CellValue.Text("Tasks Rejected"), CellValue.Num(rejectedCount.toDouble())),
            listOf(CellValue.Text("Malfunctions Flagged on Tasks"), CellValue.Num(malfunctionsCount.toDouble())),
            listOf(CellValue.Text("Total Service Time (Minutes)"), CellValue.Num(totalMinutes.toDouble())),
            listOf(CellValue.Text("Total Spare Parts Quantity"), CellValue.Num(totalPartsQty.toDouble()))
        )

        val taskRows = mutableListOf<List<CellValue>>(
            listOf(
                CellValue.Text("Asset Code"),
                CellValue.Text("Machine"),
                CellValue.Text("Custom Group"),
                CellValue.Text("Task Title"),
                CellValue.Text("Priority"),
                CellValue.Text("Cadence"),
                CellValue.Text("Created At"),
                CellValue.Text("Deadline"),
                CellValue.Text("Status"),
                CellValue.Text("Person In Charge"),
                CellValue.Text("Assistant Engineers"),
                CellValue.Text("Est. Minutes"),
                CellValue.Text("Reminder")
            )
        )
        dataset.tasks.forEach { t ->
            val m = dataset.machinesById[t.machineId]
            val g = (t.groupId ?: m?.groupId)?.let { dataset.groupsById[it]?.name } ?: "Ungrouped"
            val status = if (t.deadlineMillis < now) "OVERDUE" else "SCHEDULED"
            taskRows += listOf(
                CellValue.Text(m?.assetCode ?: "-"),
                CellValue.Text(m?.name ?: "Unknown"),
                CellValue.Text(g),
                CellValue.Text(t.title),
                CellValue.Text(t.priority.label),
                CellValue.Text(t.cadence.label),
                CellValue.Text(Formatters.formatIsoDateTime(t.createdAtMillis)),
                CellValue.Text(Formatters.formatIsoDateTime(t.deadlineMillis)),
                CellValue.Text(status),
                CellValue.Text(t.personInCharge),
                CellValue.Text(t.assistantEngineers.ifBlank { "-" }),
                CellValue.Num(t.estimatedMinutes.toDouble()),
                CellValue.Text(if (t.reminderEnabled) "On (${Formatters.formatLeadTime(t.reminderLeadHours)} before)" else "Off")
            )
        }

        val logRows = mutableListOf<List<CellValue>>(
            listOf(
                CellValue.Text("Created At"),
                CellValue.Text("Recorded Date"),
                CellValue.Text("Outcome"),
                CellValue.Text("Priority"),
                CellValue.Text("Type"),
                CellValue.Text("Severity"),
                CellValue.Text("Asset Code"),
                CellValue.Text("Machine"),
                CellValue.Text("Custom Group"),
                CellValue.Text("Task Summary"),
                CellValue.Text("Details / Rejection Reason"),
                CellValue.Text("Person In Charge"),
                CellValue.Text("Assistant Engineers"),
                CellValue.Text("Service Time (min)"),
                CellValue.Text("On Time"),
                CellValue.Text("Spare Parts Used")
            )
        )
        dataset.logs.forEach { item ->
            val l = item.log
            val m = dataset.machinesById[l.machineId]
            val g = m?.groupId?.let { dataset.groupsById[it]?.name } ?: "Ungrouped"
            val partsSummary = if (item.parts.isEmpty()) "None" else {
                item.parts.joinToString("; ") { p ->
                    val idStr = if (p.partCode.isNotBlank()) "[ID: ${p.partCode}] " else ""
                    val descStr = if (p.partDescription.isNotBlank()) " (${p.partDescription})" else ""
                    val srcStr = if (p.fromWarehouse) " [Warehouse]" else ""
                    "$idStr${p.partName}$descStr$srcStr x${p.quantity} ${p.unit}"
                }
            }
            val detailsCombined = if (l.outcome == TaskOutcome.REJECTED) {
                listOfNotNull(
                    l.rejectionReason.takeIf { it.isNotBlank() }?.let { "Rejected: $it" },
                    l.malfunctionDetails.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            } else {
                l.malfunctionDetails
            }
            logRows += listOf(
                CellValue.Text(Formatters.formatIsoDateTime(l.createdAtMillis)),
                CellValue.Text(Formatters.formatIsoDateTime(l.completedAtMillis)),
                CellValue.Text(l.outcome.label),
                CellValue.Text(l.prioritySnapshot?.label ?: "-"),
                CellValue.Text(l.entryType.label),
                CellValue.Text(l.severity.label),
                CellValue.Text(m?.assetCode ?: "-"),
                CellValue.Text(m?.name ?: "Unknown"),
                CellValue.Text(g),
                CellValue.Text(l.summaryTitle),
                CellValue.Text(detailsCombined),
                CellValue.Text(l.personInCharge),
                CellValue.Text(l.assistantEngineers.ifBlank { "-" }),
                CellValue.Num(l.serviceDurationMinutes.toDouble()),
                CellValue.Text(if (l.outcome == TaskOutcome.REJECTED) "N/A" else if (l.deadlineWasMet) "Yes" else "Late"),
                CellValue.Text(partsSummary)
            )
        }

        val partsRows = mutableListOf<List<CellValue>>(
            listOf(
                CellValue.Text("Date"),
                CellValue.Text("Asset Code"),
                CellValue.Text("Machine"),
                CellValue.Text("Spare Part ID / No."),
                CellValue.Text("Spare Part Name"),
                CellValue.Text("Spare Part Description"),
                CellValue.Text("From Warehouse"),
                CellValue.Text("Quantity"),
                CellValue.Text("Unit"),
                CellValue.Text("Technician (PIC)")
            )
        )
        dataset.logs.forEach { item ->
            val l = item.log
            val m = dataset.machinesById[l.machineId]
            item.parts.forEach { p ->
                partsRows += listOf(
                    CellValue.Text(Formatters.formatIsoDateTime(l.completedAtMillis)),
                    CellValue.Text(m?.assetCode ?: "-"),
                    CellValue.Text(m?.name ?: "Unknown"),
                    CellValue.Text(p.partCode.ifBlank { "-" }),
                    CellValue.Text(p.partName),
                    CellValue.Text(p.partDescription.ifBlank { "-" }),
                    CellValue.Text(if (p.fromWarehouse) "Yes" else "No"),
                    CellValue.Num(p.quantity.toDouble()),
                    CellValue.Text(p.unit),
                    CellValue.Text(l.personInCharge)
                )
            }
        }

        ZipOutputStream(out).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypesXml())
            writeZipEntry(zip, "_rels/.rels", rootRelsXml())
            writeZipEntry(zip, "xl/workbook.xml", workbookXml())
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml())
            writeZipEntry(zip, "xl/styles.xml", stylesXml())
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildWorksheetXml(summaryRows, headerRowIndex = 11))
            writeZipEntry(zip, "xl/worksheets/sheet2.xml", buildWorksheetXml(taskRows, headerRowIndex = 1))
            writeZipEntry(zip, "xl/worksheets/sheet3.xml", buildWorksheetXml(logRows, headerRowIndex = 1))
            writeZipEntry(zip, "xl/worksheets/sheet4.xml", buildWorksheetXml(partsRows, headerRowIndex = 1))
        }
    }

    fun writePdf(out: OutputStream, params: ExportFilterParams, dataset: ExportDataset) {
        val doc = PdfDocument()
        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B4965")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1B4965")
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F2937")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }
        val cardFillPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas: Canvas = page.canvas
        var y = margin + 12f

        fun startNewPage() {
            canvas.drawText("MachinaLog · Page $pageNum", margin, pageHeight - 20f, mutedPaint)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin + 12f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                startNewPage()
            }
        }

        fun drawWrappedText(text: String, x: Float, maxWidth: Float, paint: Paint, lineHeight: Float = 13f) {
            val words = text.split(" ")
            val line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "${line} $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    line.clear().append(candidate)
                } else {
                    ensureSpace(lineHeight)
                    canvas.drawText(line.toString(), x, y, paint)
                    y += lineHeight
                    line.clear().append(word)
                }
            }
            if (line.isNotEmpty()) {
                ensureSpace(lineHeight)
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
            }
        }

        // Header
        canvas.drawText("MachinaLog — Factory Maintenance Report", margin, y, titlePaint)
        y += 18f
        canvas.drawText("Generated: ${Formatters.formatDateTime(System.currentTimeMillis())}", margin, y, mutedPaint)
        y += 14f

        val machineLabel = params.machineId?.let { dataset.machinesById[it]?.let { m -> "${m.assetCode} ${m.name}" } } ?: "All Machines"
        val groupLabel = params.customGroupId?.let { dataset.groupsById[it]?.name } ?: "All Groups"
        val picLabel = params.personInCharge?.takeIf { it.isNotBlank() } ?: "All Engineers"
        val cadenceLabel = params.cadence?.label ?: "All Cadences"
        val periodLabel = "${params.startMillis?.let { Formatters.formatDateOnly(it) } ?: "Any"} to ${params.endMillis?.let { Formatters.formatDateOnly(it) } ?: "Present"}"

        val outcomeLabel = params.outcome?.label ?: "All Outcomes"

        canvas.drawRect(margin, y, margin + contentWidth, y + 58f, cardFillPaint)
        y += 14f
        canvas.drawText("Filters: Machine=$machineLabel  |  Group=$groupLabel  |  PIC=$picLabel", margin + 8f, y, bodyPaint)
        y += 14f
        canvas.drawText("Cadence=$cadenceLabel  |  Outcome=$outcomeLabel  |  Period=$periodLabel", margin + 8f, y, bodyPaint)
        y += 14f
        val totalMin = dataset.logs.sumOf { it.log.serviceDurationMinutes }
        val doneCount = dataset.logs.count { it.log.outcome == TaskOutcome.DONE }
        val rejectedCount = dataset.logs.count { it.log.outcome == TaskOutcome.REJECTED }
        canvas.drawText(
            "KPIs: ${dataset.tasks.size} Active Tasks  |  ${dataset.logs.size} Reports ($doneCount Done, $rejectedCount Rejected)  |  Service Time: ${Formatters.formatDuration(totalMin)}",
            margin + 8f,
            y,
            boldBodyPaint
        )
        y += 28f

        if (params.includeTasks) {
            ensureSpace(32f)
            canvas.drawText("1. Active Scheduled Tasks (${dataset.tasks.size})", margin, y, sectionPaint)
            y += 8f
            canvas.drawLine(margin, y, margin + contentWidth, y, dividerPaint)
            y += 14f
            if (dataset.tasks.isEmpty()) {
                canvas.drawText("No active tasks matched the selected filter parameters.", margin, y, mutedPaint)
                y += 18f
            } else {
                dataset.tasks.forEach { t ->
                    ensureSpace(40f)
                    val m = dataset.machinesById[t.machineId]
                    val state = if (t.deadlineMillis < System.currentTimeMillis()) "OVERDUE" else "DUE"
                    val asstStr = if (t.assistantEngineers.isNotBlank()) " (+ ${t.assistantEngineers})" else ""
                    canvas.drawText(
                        "[${t.priority.label.uppercase()} · ${t.cadence.label.uppercase()}] ${t.title} — ${m?.assetCode ?: ""} ${m?.name ?: ""}",
                        margin,
                        y,
                        boldBodyPaint
                    )
                    y += 12f
                    canvas.drawText(
                        "Status: $state (${Formatters.formatDateTime(t.deadlineMillis)})  |  Created: ${Formatters.formatDateTime(t.createdAtMillis)}  |  PIC: ${t.personInCharge}$asstStr",
                        margin + 8f,
                        y,
                        mutedPaint
                    )
                    y += 16f
                }
            }
            y += 8f
        }

        if (params.includeLogs) {
            ensureSpace(32f)
            canvas.drawText("2. Machine Task History & Reports (${dataset.logs.size})", margin, y, sectionPaint)
            y += 8f
            canvas.drawLine(margin, y, margin + contentWidth, y, dividerPaint)
            y += 14f
            if (dataset.logs.isEmpty()) {
                canvas.drawText("No task reports matched the selected filter parameters.", margin, y, mutedPaint)
                y += 18f
            } else {
                dataset.logs.forEach { item ->
                    val l = item.log
                    val m = dataset.machinesById[l.machineId]
                    val asstStr = if (l.assistantEngineers.isNotBlank()) " (+ ${l.assistantEngineers})" else ""
                    ensureSpace(56f)
                    canvas.drawText(
                        "[${l.outcome.label.uppercase()} · ${l.entryType.label}] ${l.summaryTitle} (${m?.assetCode ?: ""} ${m?.name ?: ""})",
                        margin,
                        y,
                        boldBodyPaint
                    )
                    y += 12f
                    canvas.drawText(
                        "Created: ${Formatters.formatDateTime(l.createdAtMillis)}  |  Recorded: ${Formatters.formatDateTime(l.completedAtMillis)}  |  PIC: ${l.personInCharge}$asstStr",
                        margin + 8f,
                        y,
                        mutedPaint
                    )
                    y += 12f
                    if (l.outcome == TaskOutcome.REJECTED && l.rejectionReason.isNotBlank()) {
                        drawWrappedText("Rejection Reason: ${l.rejectionReason}", margin + 8f, contentWidth - 12f, bodyPaint)
                    }
                    if (l.malfunctionDetails.isNotBlank()) {
                        drawWrappedText("Notes: ${l.malfunctionDetails}", margin + 8f, contentWidth - 12f, bodyPaint)
                    }
                    if (item.parts.isNotEmpty()) {
                        val partsStr = item.parts.joinToString("; ") { p ->
                            val idStr = if (p.partCode.isNotBlank()) "[${p.partCode}] " else ""
                            val descStr = if (p.partDescription.isNotBlank()) " - ${p.partDescription}" else ""
                            val whStr = if (p.fromWarehouse) " (Warehouse)" else ""
                            "$idStr${p.partName}$descStr$whStr x${p.quantity} ${p.unit}"
                        }
                        drawWrappedText("Spare Parts: $partsStr", margin + 8f, contentWidth - 12f, mutedPaint)
                    }
                    y += 8f
                }
            }
        }

        canvas.drawText("MachinaLog · Page $pageNum", margin, pageHeight - 20f, mutedPaint)
        doc.finishPage(page)
        doc.writeTo(out)
        doc.close()
    }

    private sealed class CellValue {
        data class Text(val value: String) : CellValue()
        data class Num(val value: Double) : CellValue()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun colRef(colIndexZeroBased: Int): String {
        var idx = colIndexZeroBased
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A'.code + (idx % 26)).toChar())
            idx = (idx / 26) - 1
        }
        return sb.toString()
    }

    private fun buildWorksheetXml(rows: List<List<CellValue>>, headerRowIndex: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cell ->
                val ref = "${colRef(cIdx)}$rowNum"
                val style = if (rowNum == headerRowIndex) """ s="1"""" else ""
                when (cell) {
                    is CellValue.Text -> {
                        sb.append("""<c r="$ref" t="inlineStr"$style><is><t>${escapeXml(cell.value)}</t></is></c>""")
                    }
                    is CellValue.Num -> {
                        sb.append("""<c r="$ref"$style><v>${cell.value}</v></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Summary" sheetId="1" r:id="rId1"/>
    <sheet name="Maintenance Tasks" sheetId="2" r:id="rId2"/>
    <sheet name="Service History" sheetId="3" r:id="rId3"/>
    <sheet name="Spare Parts" sheetId="4" r:id="rId4"/>
  </sheets>
</workbook>"""

    private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
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
