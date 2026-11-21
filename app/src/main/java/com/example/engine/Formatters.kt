package com.example.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object Formatters {
    private val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val dateOnlyFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val isoDateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private val fileStampFmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun formatDateTime(millis: Long): String = dateTimeFmt.format(Date(millis))
    fun formatDateOnly(millis: Long): String = dateOnlyFmt.format(Date(millis))
    fun formatIsoDateTime(millis: Long): String = isoDateTimeFmt.format(Date(millis))
    fun formatFileStamp(millis: Long = System.currentTimeMillis()): String = fileStampFmt.format(Date(millis))

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val hrs = minutes / 60
        val mins = minutes % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 -> "${hrs}h"
            else -> "${mins}m"
        }
    }

    fun formatRelativeDeadline(deadlineMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = deadlineMillis - nowMillis
        val absDiff = abs(diff)
        val mins = TimeUnit.MILLISECONDS.toMinutes(absDiff)
        val hours = TimeUnit.MILLISECONDS.toHours(absDiff)
        val days = TimeUnit.MILLISECONDS.toDays(absDiff)

        val span = when {
            days >= 1 -> "${days}d"
            hours >= 1 -> "${hours}h"
            else -> "${mins.coerceAtLeast(1)}m"
        }
        return if (diff < 0) "Overdue $span" else "Due in $span"
    }
}
