package com.example.engine

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.AutoBackupFrequency
import java.util.concurrent.TimeUnit

object NotificationChannels {
    const val CHANNEL_REMINDERS = "machinalog_reminders"
    const val CHANNEL_BACKUP = "machinalog_backup"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.notification_channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_reminders_desc)
            }
            val backupChannel = NotificationChannel(
                CHANNEL_BACKUP,
                context.getString(R.string.notification_channel_backup_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_backup_desc)
            }
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(backupChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NotificationChannels.ensureCreated(applicationContext)
        val prefs = AppPreferences(applicationContext)
        val settings = prefs.getSnapshot()
        if (!settings.notificationsEnabled || !NotificationChannels.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        val dao = AppDatabase.getInstance(applicationContext).machinaDao()
        val now = System.currentTimeMillis()
        val tasks = dao.getAllTasksSync()
        val todos = dao.getAllTodosSync()

        val dueTasks = tasks.filter { task ->
            task.reminderEnabled &&
                (task.deadlineMillis - now) <= TimeUnit.HOURS.toMillis(task.reminderLeadHours.toLong().coerceAtLeast(1))
        }
        val overdueTasksCount = dueTasks.count { it.deadlineMillis < now }

        val dueTodos = todos.filter { todo ->
            !todo.isCompleted && todo.reminderEnabled &&
                todo.dueAtMillis <= now + TimeUnit.MINUTES.toMillis(30)
        }

        if (dueTasks.isEmpty() && dueTodos.isEmpty()) {
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = mutableListOf<String>()
        if (overdueTasksCount > 0) parts += "$overdueTasksCount overdue tasks"
        val upcomingTasks = dueTasks.size - overdueTasksCount
        if (upcomingTasks > 0) parts += "$upcomingTasks tasks due soon"
        if (dueTodos.isNotEmpty()) parts += "${dueTodos.size} engineer reminders"

        val title = if (overdueTasksCount > 0) {
            "Factory Maintenance Alert ($overdueTasksCount Overdue)"
        } else {
            "Upcoming Maintenance & Reminders"
        }

        val preview = buildString {
            append(parts.joinToString(" · "))
            val firstTitle = dueTasks.firstOrNull()?.title ?: dueTodos.firstOrNull()?.title
            if (firstTitle != null) {
                append("\nNext: ").append(firstTitle)
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_machina)
            .setContentTitle(title)
            .setContentText(parts.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(
                if (overdueTasksCount > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(2001, notification)
        return Result.success()
    }
}

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NotificationChannels.ensureCreated(applicationContext)
        val success = runCatching { BackupSyncManager.runAutoBackup(applicationContext) }.getOrDefault(false)
        return if (success) Result.success() else Result.retry()
    }
}

object MaintenanceReminderScheduler {
    private const val REMINDER_WORK_NAME = "machinalog_periodic_reminders"
    private const val BACKUP_WORK_NAME = "machinalog_periodic_autobackup"

    fun scheduleReminders(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(REMINDER_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(6, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(
            REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun triggerImmediateReminderCheck(context: Context) {
        val req = OneTimeWorkRequestBuilder<ReminderCheckWorker>().build()
        WorkManager.getInstance(context).enqueue(req)
    }

    fun scheduleAutoBackup(context: Context, frequency: AutoBackupFrequency) {
        val wm = WorkManager.getInstance(context)
        when (frequency) {
            AutoBackupFrequency.OFF -> wm.cancelUniqueWork(BACKUP_WORK_NAME)
            AutoBackupFrequency.DAILY -> {
                val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(BACKUP_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
            }
            AutoBackupFrequency.WEEKLY -> {
                val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
                wm.enqueueUniquePeriodicWork(BACKUP_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
            }
        }
    }
}
