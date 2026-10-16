package com.z.grow.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.z.grow.data.AppSettings
import com.z.grow.data.Backup
import com.z.grow.data.GrowData
import com.z.grow.data.GrowDatabase
import com.z.grow.data.GrowEnvironment
import com.z.grow.data.JournalEntry
import com.z.grow.data.Nutrient
import com.z.grow.data.PhotoStore
import com.z.grow.data.Plant
import com.z.grow.data.SettingsStore
import com.z.grow.data.TaskKind
import com.z.grow.data.TaskLog
import com.z.grow.data.data
import com.z.grow.work.AutoBackupWorker
import com.z.grow.work.ReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class GrowViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = GrowDatabase.get(app).dao()
    private val settingsStore = SettingsStore(app)

    val data: StateFlow<GrowData> = dao.data()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrowData.EMPTY)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    suspend fun environment(id: Long) = dao.environment(id)

    suspend fun nutrientsFor(environmentId: Long) = dao.nutrientsFor(environmentId)

    suspend fun plant(id: Long) = dao.plant(id)

    suspend fun journalEntry(id: Long) = dao.journalEntry(id)

    suspend fun photosFor(entryId: Long) = dao.photosFor(entryId)

    fun saveEnvironment(environment: GrowEnvironment, nutrients: List<Nutrient>) = viewModelScope.launch {
        dao.saveEnvironment(environment, nutrients.filter { it.name.isNotBlank() })
    }

    fun deleteEnvironment(environment: GrowEnvironment) = viewModelScope.launch {
        dao.deleteEnvironment(environment)
    }

    fun savePlant(plant: Plant) = viewModelScope.launch {
        if (plant.id == 0L) dao.insertPlant(plant) else dao.updatePlant(plant)
    }

    fun deletePlant(plant: Plant) = viewModelScope.launch { dao.deletePlant(plant) }

    fun logTask(plantId: Long, date: LocalDate, kind: TaskKind, ph: Double?, ppm: Int?, amount: String) =
        viewModelScope.launch {
            dao.upsertTaskLog(
                TaskLog(plantId = plantId, epochDay = date.toEpochDay(), kind = kind, ph = ph, ppm = ppm, amount = amount),
            )
        }

    fun removeTaskLog(log: TaskLog) = viewModelScope.launch {
        dao.deleteTaskLog(log.plantId, log.epochDay, log.kind.name)
    }

    /** [removedPhotos] are files dropped from the entry; they're deleted once the save lands. */
    fun saveJournalEntry(entry: JournalEntry, photos: List<String>, removedPhotos: List<String>) = viewModelScope.launch {
        dao.saveJournalEntry(entry, photos)
        PhotoStore.delete(app, removedPhotos)
    }

    fun deleteJournalEntry(entry: JournalEntry) = viewModelScope.launch {
        val photos = dao.photosFor(entry.id).map { it.fileName }
        dao.deleteJournalEntry(entry)
        PhotoStore.delete(app, photos)
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = change(old)
        settingsStore.save(new)
        _settings.value = new
        if (old.remindersEnabled != new.remindersEnabled || old.reminderMinute != new.reminderMinute) {
            ReminderWorker.schedule(app, new, reschedule = true)
        }
        AutoBackupWorker.schedule(app, new)
    }

    suspend fun backupTo(uri: Uri): Result<Unit> = runCatching {
        Backup.write(app, uri)
        updateSettings { it.copy(backupUri = uri, lastBackupMillis = System.currentTimeMillis()) }
    }

    suspend fun restoreFrom(uri: Uri): Result<Unit> = runCatching { Backup.restore(app, uri) }
}
