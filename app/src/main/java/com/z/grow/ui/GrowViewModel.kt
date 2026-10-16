package com.z.grow.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.z.grow.data.Achievement
import com.z.grow.data.Achievements
import com.z.grow.data.AppSettings
import com.z.grow.data.Backup
import com.z.grow.data.DeathReason
import com.z.grow.data.GrowStage
import com.z.grow.data.EntryKind
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
import com.z.grow.work.Notifications
import com.z.grow.work.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class GrowViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = GrowDatabase.get(app).dao()
    private val settingsStore = SettingsStore(app)

    val data: StateFlow<GrowData> = dao.data()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrowData.EMPTY)

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** An achievement that was just unlocked, and the plant that earned it (if any). */
    data class Unlock(val achievement: Achievement, val plant: Plant?)

    private val _unlocked = MutableSharedFlow<Unlock>(extraBufferCapacity = 16)

    /** Fires once for each achievement as it's unlocked. */
    val unlocked: SharedFlow<Unlock> = _unlocked.asSharedFlow()

    init {
        // Re-check achievements whenever anything changes; awarding is idempotent.
        viewModelScope.launch {
            data.collect { snapshot ->
                val today = LocalDate.now().toEpochDay()
                Achievements.newlyEarned(snapshot, today).forEach { (achievement, earned) ->
                    val entry = JournalEntry(
                        epochDay = today,
                        text = "Achievement unlocked: ${achievement.emoji} ${achievement.title} — ${achievement.description}",
                        plantId = earned.plantId,
                        kind = EntryKind.ACHIEVEMENT,
                    )
                    if (dao.awardAchievement(earned, entry)) {
                        val plant = snapshot.plant(earned.plantId)
                        _unlocked.emit(Unlock(achievement, plant))
                        Notifications.post(
                            app,
                            Notifications.achievementId(earned.key),
                            Notifications.ACHIEVEMENTS_CHANNEL,
                            "🏆 Achievement unlocked: ${achievement.emoji} ${achievement.title}",
                            listOfNotNull(achievement.description, plant?.let { "Earned by ${it.displayName}" }),
                        )
                    }
                }
            }
        }
    }

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

    /**
     * Saves the plant and records history in the journal: when it's added, when its profile
     * photo changes (with a copy of the photo) and when its stage changes. [original] is the
     * plant as it was loaded, or null for a new plant.
     */
    fun savePlant(plant: Plant, original: Plant?) = viewModelScope.launch {
        val id = if (original == null) dao.insertPlant(plant.copy(id = 0)) else plant.id.also { dao.updatePlant(plant) }
        val photoChanged = plant.photoFileName != original?.photoFileName
        val photoCopy = if (photoChanged) plant.photoFileName?.let { copyPhoto(it) } else null

        if (original == null) {
            val details = listOf(plant.strain, plant.manufacturer).filter { it.isNotBlank() }.joinToString(" · ")
            dao.saveJournalEntry(
                JournalEntry(
                    epochDay = plant.startEpochDay,
                    text = "Started ${plant.displayName}" + if (details.isNotEmpty()) " ($details)" else "",
                    plantId = id,
                    kind = EntryKind.PLANT_ADDED,
                ),
                listOfNotNull(photoCopy),
            )
            return@launch
        }
        val today = LocalDate.now().toEpochDay()
        if (photoCopy != null) {
            dao.saveJournalEntry(
                JournalEntry(epochDay = today, text = "New profile photo", plantId = id, kind = EntryKind.PROFILE_PHOTO),
                listOf(photoCopy),
            )
        }
        if (plant.stage != original.stage) {
            dao.saveJournalEntry(
                JournalEntry(
                    epochDay = today,
                    text = stageChangeText(original, plant),
                    plantId = id,
                    kind = EntryKind.STAGE_CHANGE,
                ),
                emptyList(),
            )
        }
        if (photoChanged) original.photoFileName?.let { PhotoStore.delete(app, listOf(it)) }
    }

    fun deletePlant(plant: Plant) = viewModelScope.launch {
        dao.deletePlant(plant)
        plant.photoFileName?.let { PhotoStore.delete(app, listOf(it)) }
    }

    /** Uses a copy of a journal photo as the plant's profile photo. */
    fun setProfilePhoto(plantId: Long, fileName: String) = viewModelScope.launch {
        val plant = dao.plant(plantId) ?: return@launch
        val copy = copyPhoto(fileName) ?: return@launch
        dao.updatePlant(plant.copy(photoFileName = copy))
        plant.photoFileName?.let { PhotoStore.delete(app, listOf(it)) }
    }

    private fun stageChangeText(old: Plant, new: Plant): String = when (new.stage) {
        GrowStage.DRYING -> "Started drying (target ${new.dryDays} days)"
        GrowStage.CURING -> "Started curing (target ${new.cureWeeks} weeks)"
        GrowStage.HARVESTED -> "Harvested and moved to the archive"
        GrowStage.DEAD -> "Plant died: ${(new.deathReason ?: DeathReason.OTHER).label}" +
            new.deathNotes.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
        else -> "Stage changed: ${old.stage.label} → ${new.stage.label}"
    }

    private suspend fun copyPhoto(fileName: String) = withContext(Dispatchers.IO) { PhotoStore.copy(app, fileName) }

    fun logTask(plantId: Long, date: LocalDate, kind: TaskKind, ph: Double?, ppm: Int?, amount: String) =
        viewModelScope.launch {
            dao.upsertTaskLog(
                TaskLog(plantId = plantId, epochDay = date.toEpochDay(), kind = kind, ph = ph, ppm = ppm, amount = amount),
            )
        }

    /** One-tap log for today. Returns false (and changes nothing) if it was already logged today. */
    suspend fun quickLog(plant: Plant, kind: TaskKind): Boolean {
        val today = LocalDate.now().toEpochDay()
        if (data.value.logs.any { it.plantId == plant.id && it.epochDay == today && it.kind == kind }) return false
        dao.upsertTaskLog(TaskLog(plantId = plant.id, epochDay = today, kind = kind))
        return true
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
