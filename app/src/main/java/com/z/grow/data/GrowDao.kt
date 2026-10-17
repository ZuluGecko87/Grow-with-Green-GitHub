package com.z.grow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Dao
abstract class GrowDao {
    @Query("SELECT * FROM environments ORDER BY name COLLATE NOCASE")
    abstract fun environments(): Flow<List<GrowEnvironment>>

    @Query("SELECT * FROM environments WHERE id = :id")
    abstract suspend fun environment(id: Long): GrowEnvironment?

    @Insert
    protected abstract suspend fun insertEnvironment(environment: GrowEnvironment): Long

    @Update
    protected abstract suspend fun updateEnvironment(environment: GrowEnvironment)

    @Delete
    abstract suspend fun deleteEnvironment(environment: GrowEnvironment)

    @Query("SELECT * FROM nutrients ORDER BY id")
    abstract fun nutrients(): Flow<List<Nutrient>>

    @Query("SELECT * FROM nutrients WHERE environmentId = :environmentId ORDER BY id")
    abstract suspend fun nutrientsFor(environmentId: Long): List<Nutrient>

    @Query("DELETE FROM nutrients WHERE environmentId = :environmentId")
    protected abstract suspend fun clearNutrients(environmentId: Long)

    @Insert
    protected abstract suspend fun insertNutrients(nutrients: List<Nutrient>)

    /** Saves the environment and replaces its nutrient lineup. */
    @Transaction
    open suspend fun saveEnvironment(environment: GrowEnvironment, nutrients: List<Nutrient>): Long {
        val id = if (environment.id == 0L) {
            insertEnvironment(environment)
        } else {
            updateEnvironment(environment)
            environment.id
        }
        clearNutrients(id)
        insertNutrients(nutrients.map { it.copy(id = 0, environmentId = id) })
        return id
    }

    @Query("SELECT * FROM plants ORDER BY startEpochDay DESC, id DESC")
    abstract fun plants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :id")
    abstract suspend fun plant(id: Long): Plant?

    @Insert
    abstract suspend fun insertPlant(plant: Plant): Long

    @Update
    abstract suspend fun updatePlant(plant: Plant)

    @Delete
    protected abstract suspend fun deletePlantRow(plant: Plant)

    @Query("UPDATE notes SET plantId = NULL WHERE plantId = :plantId")
    protected abstract suspend fun detachJournal(plantId: Long)

    /** Deletes the plant and its logs but keeps its journal entries. */
    @Transaction
    open suspend fun deletePlant(plant: Plant) {
        detachJournal(plant.id)
        deletePlantRow(plant)
    }

    @Query("SELECT * FROM task_logs")
    abstract fun taskLogs(): Flow<List<TaskLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTaskLog(log: TaskLog)

    @Query("DELETE FROM task_logs WHERE plantId = :plantId AND epochDay = :epochDay AND kind = :kind")
    abstract suspend fun deleteTaskLog(plantId: Long, epochDay: Long, kind: String)

    @Query("SELECT * FROM notes ORDER BY epochDay DESC, id DESC")
    abstract fun journal(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_photos ORDER BY id")
    abstract fun journalPhotos(): Flow<List<JournalPhoto>>

    @Query("SELECT * FROM notes WHERE id = :id")
    abstract suspend fun journalEntry(id: Long): JournalEntry?

    @Query("SELECT * FROM journal_photos WHERE entryId = :entryId ORDER BY id")
    abstract suspend fun photosFor(entryId: Long): List<JournalPhoto>

    @Insert
    protected abstract suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Update
    protected abstract suspend fun updateJournalEntry(entry: JournalEntry)

    @Query("DELETE FROM journal_photos WHERE entryId = :entryId")
    protected abstract suspend fun clearPhotos(entryId: Long)

    @Insert
    protected abstract suspend fun insertPhotos(photos: List<JournalPhoto>)

    /** Saves the entry and replaces its photo list. */
    @Transaction
    open suspend fun saveJournalEntry(entry: JournalEntry, photoFiles: List<String>): Long {
        val id = if (entry.id == 0L) {
            insertJournalEntry(entry)
        } else {
            updateJournalEntry(entry)
            entry.id
        }
        clearPhotos(id)
        insertPhotos(photoFiles.map { JournalPhoto(entryId = id, fileName = it) })
        return id
    }

    @Delete
    abstract suspend fun deleteJournalEntry(entry: JournalEntry)

    @Query("SELECT * FROM achievements ORDER BY earnedEpochDay, key")
    abstract fun achievements(): Flow<List<EarnedAchievement>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAchievement(achievement: EarnedAchievement): Long

    /** Stores the achievement and its journal entry; false if it was already earned. */
    @Transaction
    open suspend fun awardAchievement(achievement: EarnedAchievement, entry: JournalEntry): Boolean {
        if (insertAchievement(achievement) == -1L) return false
        insertJournalEntry(entry)
        return true
    }
}

/** Live snapshot of the whole database. */
fun GrowDao.data(): Flow<GrowData> = combine(
    combine(environments(), plants(), nutrients(), ::Triple),
    combine(taskLogs(), journal(), journalPhotos(), ::Triple),
    achievements(),
) { (environments, plants, nutrients), (logs, journal, photos), achievements ->
    GrowData(environments, plants, nutrients, logs, journal, photos, achievements)
}
