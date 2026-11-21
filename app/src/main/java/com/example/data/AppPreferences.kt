package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "machinalog_prefs")

data class UserPreferences(
    val notificationsEnabled: Boolean = true,
    val reminderLeadHours: Int = 24,
    val autoBackupFrequency: AutoBackupFrequency = AutoBackupFrequency.OFF,
    val autoBackupTreeUri: String? = null,
    val lastAutoBackupMillis: Long? = null,
    val sampleDataSeeded: Boolean = false,
    val defaultEngineerName: String = "Elena Vance",
    val pcWebEnabled: Boolean = false,
    val pcWebPinEnabled: Boolean = false,
    val pcWebPin: String = "4821"
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_LEAD_HOURS = intPreferencesKey("reminder_lead_hours")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val AUTO_BACKUP_TREE_URI = stringPreferencesKey("auto_backup_tree_uri")
        val LAST_AUTO_BACKUP_MILLIS = longPreferencesKey("last_auto_backup_millis")
        val SAMPLE_DATA_SEEDED = booleanPreferencesKey("sample_data_seeded")
        val DEFAULT_ENGINEER_NAME = stringPreferencesKey("default_engineer_name")
        val PC_WEB_ENABLED = booleanPreferencesKey("pc_web_enabled")
        val PC_WEB_PIN_ENABLED = booleanPreferencesKey("pc_web_pin_enabled")
        val PC_WEB_PIN = stringPreferencesKey("pc_web_pin")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val freqStr = prefs[Keys.AUTO_BACKUP_FREQUENCY] ?: AutoBackupFrequency.OFF.name
        val freq = runCatching { AutoBackupFrequency.valueOf(freqStr) }.getOrDefault(AutoBackupFrequency.OFF)
        UserPreferences(
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            reminderLeadHours = prefs[Keys.REMINDER_LEAD_HOURS] ?: 24,
            autoBackupFrequency = freq,
            autoBackupTreeUri = prefs[Keys.AUTO_BACKUP_TREE_URI],
            lastAutoBackupMillis = prefs[Keys.LAST_AUTO_BACKUP_MILLIS],
            sampleDataSeeded = prefs[Keys.SAMPLE_DATA_SEEDED] ?: false,
            defaultEngineerName = prefs[Keys.DEFAULT_ENGINEER_NAME] ?: "Elena Vance",
            pcWebEnabled = prefs[Keys.PC_WEB_ENABLED] ?: false,
            pcWebPinEnabled = prefs[Keys.PC_WEB_PIN_ENABLED] ?: false,
            pcWebPin = prefs[Keys.PC_WEB_PIN] ?: "4821"
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setReminderLeadHours(hours: Int) {
        context.dataStore.edit { it[Keys.REMINDER_LEAD_HOURS] = hours }
    }

    suspend fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_FREQUENCY] = frequency.name }
    }

    suspend fun setAutoBackupTreeUri(uriString: String?) {
        context.dataStore.edit {
            if (uriString.isNullOrBlank()) {
                it.remove(Keys.AUTO_BACKUP_TREE_URI)
            } else {
                it[Keys.AUTO_BACKUP_TREE_URI] = uriString
            }
        }
    }

    suspend fun setLastAutoBackupMillis(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_AUTO_BACKUP_MILLIS] = millis }
    }

    suspend fun setSampleDataSeeded(seeded: Boolean) {
        context.dataStore.edit { it[Keys.SAMPLE_DATA_SEEDED] = seeded }
    }

    suspend fun setDefaultEngineerName(name: String) {
        context.dataStore.edit { it[Keys.DEFAULT_ENGINEER_NAME] = name.trim().ifEmpty { "Service Engineer" } }
    }

    suspend fun setPcWebEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PC_WEB_ENABLED] = enabled }
    }

    suspend fun setPcWebPinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PC_WEB_PIN_ENABLED] = enabled }
    }

    suspend fun setPcWebPin(pin: String) {
        context.dataStore.edit { it[Keys.PC_WEB_PIN] = pin.filter { c -> c.isDigit() }.take(6).ifEmpty { "4821" } }
    }
}
