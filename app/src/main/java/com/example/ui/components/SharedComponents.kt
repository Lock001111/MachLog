package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Cadence
import com.example.data.CustomGroupEntity
import com.example.data.MachineStatus
import com.example.data.ReminderPriority
import com.example.data.RepeatRule
import com.example.data.Severity
import com.example.engine.Formatters
import com.example.ui.theme.LocalMachinaColors
import com.example.ui.theme.groupColorForKey
import java.util.Calendar

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MachineStatusBadge(status: MachineStatus, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (status) {
        MachineStatus.OPERATIONAL -> sem.operationalContainer to sem.operational
        MachineStatus.MAINTENANCE_DUE -> sem.warningContainer to sem.warning
        MachineStatus.MALFUNCTION -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = status.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun CadencePill(cadence: Cadence, modifier: Modifier = Modifier) {
    StatusPill(
        text = cadence.label.uppercase(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    )
}

@Composable
fun RepeatRulePill(rule: RepeatRule, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = if (rule == RepeatRule.ONCE) {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        sem.infoContainer to sem.info
    }
    StatusPill(text = rule.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun PriorityPill(priority: ReminderPriority, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (priority) {
        ReminderPriority.NORMAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ReminderPriority.HIGH -> sem.warningContainer to sem.warning
        ReminderPriority.URGENT -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = priority.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun SeverityPill(severity: Severity, modifier: Modifier = Modifier) {
    val sem = LocalMachinaColors.current
    val (bg, fg) = when (severity) {
        Severity.LOW -> sem.operationalContainer to sem.operational
        Severity.MEDIUM -> sem.infoContainer to sem.info
        Severity.HIGH -> sem.warningContainer to sem.warning
        Severity.CRITICAL -> sem.criticalContainer to sem.critical
    }
    StatusPill(text = severity.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun GroupBadge(group: CustomGroupEntity, modifier: Modifier = Modifier) {
    val accent = groupColorForKey(group.colorKey)
    Surface(
        color = accent.copy(alpha = 0.14f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OverdueAlertBanner(
    overdueCount: Int,
    filterActive: Boolean,
    onToggleFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sem = LocalMachinaColors.current
    Surface(
        color = sem.criticalContainer,
        contentColor = sem.critical,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, sem.critical.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = "Overdue tasks alert",
                tint = sem.critical
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$overdueCount overdue maintenance ${if (overdueCount == 1) "task" else "tasks"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Immediate engineer attention required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            TextButton(
                onClick = onToggleFilter,
                colors = ButtonDefaults.textButtonColors(contentColor = sem.critical),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_overdue_filter_button")
            ) {
                Text(if (filterActive) "Show All" else "View Overdue")
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun DateTimePickerField(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = millis }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            onChange(updated.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("pick_date_button")
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = "Pick date", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Formatters.formatDateOnly(millis), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = millis }
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                            }
                            onChange(updated.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                },
                modifier = Modifier
                    .weight(0.8f)
                    .minimumInteractiveComponentSize()
                    .testTag("pick_time_button")
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = "Pick time", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                Text(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
            }
        }
    }
}
