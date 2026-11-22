package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.Cadence
import com.example.data.MachineEntity
import com.example.data.MachineStatus
import com.example.data.MaintenanceRepository
import com.example.data.MaintenanceTaskEntity
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.Severity
import com.example.data.TaskOutcome
import com.example.data.TodoReminderEntity
import com.example.engine.AppUpdateChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MachinaLog", appName)
    }

    @Test
    fun `parseReleaseJson extracts apk asset and detects newer release`() {
        val releaseJson = """
            {
              "tag_name": "v1.3.0",
              "name": "MachinaLog v1.3.0",
              "body": "Added task priority, todo pagination, and automatic update detection.",
              "html_url": "https://github.com/Lock001111/MachLog/releases/tag/v1.3.0",
              "assets": [
                {
                  "name": "MachinaLog-v1.3.0.apk",
                  "browser_download_url": "https://github.com/Lock001111/MachLog/releases/download/v1.3.0/MachinaLog-v1.3.0.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = AppUpdateChecker.parseReleaseJson(
            json = releaseJson,
            currentVersionName = "1.0",
            currentVersionCode = 1
        )
        assertNotNull(parsed)
        assertTrue(parsed!!.isUpdateAvailable)
        assertEquals("v1.3.0", parsed.latestTag)
        assertEquals(
            "https://github.com/Lock001111/MachLog/releases/download/v1.3.0/MachinaLog-v1.3.0.apk",
            parsed.apkDownloadUrl
        )
    }

    @Test
    fun `completing a repeating todo records completed row and recreates next active occurrence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.machinaDao()
            val repo = MaintenanceRepository(dao, AppPreferences(context))
            val todoId = repo.saveTodo(
                TodoReminderEntity(
                    title = "Weekly Safety Guard Inspection",
                    notes = "Check interlock switch",
                    repeatRule = RepeatRule.WEEKLY,
                    dueAtMillis = System.currentTimeMillis() - 3_600_000L,
                    priority = ReminderPriority.HIGH,
                    personInCharge = "Elena Vance"
                )
            )

            repo.toggleTodoComplete(todoId)

            val allTodos = dao.getAllTodosSync().filter { it.title == "Weekly Safety Guard Inspection" }
            assertEquals(2, allTodos.size)
            val completedRow = allTodos.firstOrNull { it.isCompleted }
            val nextActiveRow = allTodos.firstOrNull { !it.isCompleted }
            assertNotNull(completedRow)
            assertNotNull(completedRow?.completedAtMillis)
            assertNotNull(nextActiveRow)
            assertTrue(nextActiveRow!!.dueAtMillis > System.currentTimeMillis())
        } finally {
            db.close()
        }
    }

    @Test
    fun `saveTask updates machine status, preserves createdAtMillis on edit, and copies priority and createdAtMillis to history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.machinaDao()
            val repo = MaintenanceRepository(dao, AppPreferences(context))
            val machineId = repo.saveMachine(
                MachineEntity(
                    assetCode = "MCH-901",
                    name = "CNC Grinder",
                    locationZone = "Bay 3",
                    modelInfo = "Studer S33",
                    status = MachineStatus.OPERATIONAL
                )
            )

            val initialCreated = 1_700_000_123_000L
            val taskId = repo.saveTask(
                task = MaintenanceTaskEntity(
                    machineId = machineId,
                    title = "Coolant Pump Seal",
                    cadence = Cadence.WEEKLY,
                    deadlineMillis = System.currentTimeMillis() + 86_400_000L,
                    personInCharge = "Elena Vance",
                    priority = ReminderPriority.URGENT,
                    reminderEnabled = true,
                    reminderLeadHours = 12,
                    createdAtMillis = initialCreated
                ),
                machineStatus = MachineStatus.MAINTENANCE_DUE
            )

            val updatedMachine = dao.getMachineById(machineId)
            assertEquals(MachineStatus.MAINTENANCE_DUE, updatedMachine?.status)

            // Edit task and confirm createdAtMillis is preserved from DB even if caller passes a different value
            val loadedTask = dao.getTaskById(taskId)!!
            repo.saveTask(
                task = loadedTask.copy(title = "Coolant Pump Seal v2", createdAtMillis = 999L),
                machineStatus = MachineStatus.MALFUNCTION
            )
            val editedTask = dao.getTaskById(taskId)!!
            assertEquals(initialCreated, editedTask.createdAtMillis)
            assertEquals(MachineStatus.MALFUNCTION, dao.getMachineById(machineId)?.status)

            // Complete repeating task -> ServiceLog receives createdAtMillis & prioritySnapshot; next cycle recreated with fresh createdAtMillis
            repo.completeTask(
                taskId = taskId,
                personInCharge = "Elena Vance",
                assistantEngineers = listOf("Marcus Chen"),
                actualDurationMinutes = 40,
                notes = "Replaced mechanical seal",
                spareParts = emptyList(),
                flagMalfunction = false,
                malfunctionSeverity = Severity.LOW
            )

            val logs = dao.getAllServiceLogsWithPartsSync()
            assertEquals(1, logs.size)
            val log = logs.first().log
            assertEquals(TaskOutcome.DONE, log.outcome)
            assertEquals(initialCreated, log.createdAtMillis)
            assertEquals(ReminderPriority.URGENT, log.prioritySnapshot)
            assertEquals("Marcus Chen", log.assistantEngineers)

            val remainingTasks = dao.getAllTasksSync().filter { it.title == "Coolant Pump Seal v2" }
            assertEquals(1, remainingTasks.size)
            val nextCycle = remainingTasks.first()
            assertTrue(nextCycle.id != taskId)
            assertTrue(nextCycle.createdAtMillis > initialCreated)
            assertTrue(nextCycle.reminderEnabled)
            assertEquals(ReminderPriority.URGENT, nextCycle.priority)
        } finally {
            db.close()
        }
    }
}
