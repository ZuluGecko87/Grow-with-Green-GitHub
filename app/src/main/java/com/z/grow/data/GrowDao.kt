package com.z.grow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
    abstract suspend fun deletePlant(plant: Plant)

    @Query("SELECT * FROM task_logs")
    abstract fun taskLogs(): Flow<List<TaskLog>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTaskLog(log: TaskLog)

    @Query("DELETE FROM task_logs WHERE plantId = :plantId AND epochDay = :epochDay AND kind = :kind")
    abstract suspend fun deleteTaskLog(plantId: Long, epochDay: Long, kind: String)

    @Query("SELECT * FROM notes ORDER BY id")
    abstract fun notes(): Flow<List<GrowNote>>

    @Insert
    abstract suspend fun insertNote(note: GrowNote)

    @Delete
    abstract suspend fun deleteNote(note: GrowNote)
}
