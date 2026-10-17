package com.z.grow.work

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
import com.z.grow.MainActivity
import com.z.grow.R

object Notifications {
    const val REMINDERS_CHANNEL = "reminders"
    const val BACKUP_CHANNEL = "backup"
    const val ACHIEVEMENTS_CHANNEL = "achievements"
    const val REMINDER_ID = 1
    const val BACKUP_FAILED_ID = 2

    /** Each achievement gets its own notification so several can show at once. */
    fun achievementId(key: String) = 1000 + (key.hashCode() and 0xFFFFF)

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS_CHANNEL, "Watering & feeding reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(BACKUP_CHANNEL, "Backup", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(ACHIEVEMENTS_CHANNEL, "Achievements", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, channel: String, title: String, lines: List<String>) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().also { inbox -> lines.forEach(inbox::addLine) }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_sprout)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }
}
