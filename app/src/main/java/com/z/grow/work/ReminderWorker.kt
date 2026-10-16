package com.z.grow.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.z.grow.data.AppSettings
import com.z.grow.data.GrowDatabase
import com.z.grow.data.SettingsStore
import com.z.grow.data.TaskKind
import com.z.grow.data.data
import com.z.grow.ui.cycleLabel
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a day, notifies about plants due for water or nutrients and planned light changes. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        if (!store.load().remindersEnabled) return Result.success()
        val today = LocalDate.now()
        if (store.lastReminderDay == today.toEpochDay()) return Result.success()

        val agenda = GrowDatabase.get(applicationContext).dao().data().first().agenda(today)
        val due = agenda.tasks.filter { !it.done && it.plant.remindersEnabled }
        val lines = due.map { task ->
            val mix = task.nutrients.joinToString(", ") { n -> listOf(n.name, n.dose).filter { it.isNotBlank() }.joinToString(" ") }
            val late = if (task.overdueDays > 0) " (overdue ${task.overdueDays} day${if (task.overdueDays == 1) "" else "s"})" else ""
            "${task.kind.label} ${task.plant.displayName}$late" + if (mix.isNotEmpty()) " — $mix" else ""
        } + agenda.lights.filter { it.isFlipDay }.map {
            "Switch ${it.environment.name} lights to ${cycleLabel(it.hoursOn)}"
        } + agenda.milestones.filter { it.plant.remindersEnabled }.map { it.text }

        if (lines.isNotEmpty()) {
            val water = due.count { it.kind == TaskKind.WATER }
            val feed = due.count { it.kind == TaskKind.FEED }
            val title = listOfNotNull(
                water.takeIf { it > 0 }?.let { "$it to water" },
                feed.takeIf { it > 0 }?.let { "$it to feed" },
            ).joinToString(", ").ifEmpty { if (agenda.hasMilestone) "Harvest update" else "Light change today" }
            Notifications.post(applicationContext, Notifications.REMINDER_ID, Notifications.REMINDERS_CHANNEL, "Today: $title", lines)
        }
        store.lastReminderDay = today.toEpochDay()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily-reminder"

        /** [reschedule] restarts the daily timer, e.g. after the reminder time changes. */
        fun schedule(context: Context, settings: AppSettings, reschedule: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!settings.remindersEnabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(settings.reminderMinute / 60, settings.reminderMinute % 60)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(
                WORK_NAME,
                if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
