package com.z.grow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.z.grow.data.SettingsStore
import com.z.grow.ui.GrowApp
import com.z.grow.ui.theme.GrowTheme
import com.z.grow.work.AutoBackupWorker
import com.z.grow.work.Notifications
import com.z.grow.work.ReminderWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)
        val settings = SettingsStore(this).load()
        ReminderWorker.schedule(this, settings, reschedule = false)
        AutoBackupWorker.schedule(this, settings)

        enableEdgeToEdge()
        setContent {
            GrowTheme {
                GrowApp()
            }
        }
    }
}
