package com.z.grow.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

data class AppSettings(
    val remindersEnabled: Boolean = false,
    /** Minute of the day reminders are sent. */
    val reminderMinute: Int = 9 * 60,
    val backupUri: Uri? = null,
    val autoBackup: Boolean = false,
    /** Epoch millis of the last successful backup, or 0. */
    val lastBackupMillis: Long = 0,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load() = AppSettings(
        remindersEnabled = prefs.getBoolean(REMINDERS, false),
        reminderMinute = prefs.getInt(REMINDER_MINUTE, 9 * 60),
        backupUri = prefs.getString(BACKUP_URI, null)?.let(Uri::parse),
        autoBackup = prefs.getBoolean(AUTO_BACKUP, false),
        lastBackupMillis = prefs.getLong(LAST_BACKUP, 0),
    )

    fun save(settings: AppSettings) = prefs.edit {
        putBoolean(REMINDERS, settings.remindersEnabled)
        putInt(REMINDER_MINUTE, settings.reminderMinute)
        putString(BACKUP_URI, settings.backupUri?.toString())
        putBoolean(AUTO_BACKUP, settings.autoBackup)
        putLong(LAST_BACKUP, settings.lastBackupMillis)
    }

    fun setLastBackup(millis: Long) = prefs.edit { putLong(LAST_BACKUP, millis) }

    /** Day the reminder last went out, so a re-run of the worker doesn't notify twice. */
    var lastReminderDay: Long
        get() = prefs.getLong(LAST_REMINDER_DAY, Long.MIN_VALUE)
        set(value) = prefs.edit { putLong(LAST_REMINDER_DAY, value) }

    private companion object {
        const val REMINDERS = "reminders_enabled"
        const val REMINDER_MINUTE = "reminder_minute"
        const val BACKUP_URI = "backup_uri"
        const val AUTO_BACKUP = "auto_backup"
        const val LAST_BACKUP = "last_backup"
        const val LAST_REMINDER_DAY = "last_reminder_day"
    }
}
