package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.MaintenanceRepository
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
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
}
