package com.example.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromCadence(v: Cadence?): String? = v?.name
    @TypeConverter fun toCadence(v: String?): Cadence? = v?.let { Cadence.valueOf(it) }

    @TypeConverter fun fromMachineStatus(v: MachineStatus): String = v.name
    @TypeConverter fun toMachineStatus(v: String): MachineStatus = MachineStatus.valueOf(v)

    @TypeConverter fun fromGroupScope(v: GroupScope): String = v.name
    @TypeConverter fun toGroupScope(v: String): GroupScope = GroupScope.valueOf(v)

    @TypeConverter fun fromRepeatRule(v: RepeatRule): String = v.name
    @TypeConverter fun toRepeatRule(v: String): RepeatRule = RepeatRule.valueOf(v)

    @TypeConverter fun fromReminderPriority(v: ReminderPriority?): String? = v?.name
    @TypeConverter fun toReminderPriority(v: String?): ReminderPriority? = v?.let { runCatching { ReminderPriority.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromServiceEntryType(v: ServiceEntryType): String = v.name
    @TypeConverter fun toServiceEntryType(v: String): ServiceEntryType = ServiceEntryType.valueOf(v)

    @TypeConverter fun fromSeverity(v: Severity): String = v.name
    @TypeConverter fun toSeverity(v: String): Severity = Severity.valueOf(v)

    @TypeConverter fun fromTaskOutcome(v: TaskOutcome): String = v.name
    @TypeConverter fun toTaskOutcome(v: String): TaskOutcome = runCatching { TaskOutcome.valueOf(v) }.getOrDefault(TaskOutcome.DONE)
}
