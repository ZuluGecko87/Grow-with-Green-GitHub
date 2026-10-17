package com.z.grow.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.z.grow.data.AppSettings
import com.z.grow.data.Backup
import com.z.grow.data.SettingsStore
import java.util.concurrent.TimeUnit

/** Weekly overwrite of the backup file the user last chose. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val settings = store.load()
        val uri = settings.backupUri
        if (!settings.autoBackup || uri == null) return Result.success()
        return try {
            Backup.write(applicationContext, uri)
            store.setLastBackup(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) return Result.retry()
            Notifications.post(
                applicationContext,
                Notifications.BACKUP_FAILED_ID,
                Notifications.BACKUP_CHANNEL,
                "Automatic backup failed",
                listOf("Open Settings in Grow with Green and choose a backup location again."),
            )
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "auto-backup"

        fun schedule(context: Context, settings: AppSettings) {
            val work = WorkManager.getInstance(context)
            if (!settings.autoBackup || settings.backupUri == null) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
