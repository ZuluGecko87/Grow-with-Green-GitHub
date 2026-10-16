package com.z.grow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.z.grow.data.GrowData
import com.z.grow.data.GrowDatabase
import com.z.grow.data.GrowEnvironment
import com.z.grow.data.GrowNote
import com.z.grow.data.Nutrient
import com.z.grow.data.Plant
import com.z.grow.data.TaskKind
import com.z.grow.data.TaskLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class GrowViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = GrowDatabase.get(app).dao()

    val data: StateFlow<GrowData> = combine(
        dao.environments(), dao.plants(), dao.nutrients(), dao.taskLogs(), dao.notes(), ::GrowData,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrowData.EMPTY)

    suspend fun environment(id: Long) = dao.environment(id)

    suspend fun nutrientsFor(environmentId: Long) = dao.nutrientsFor(environmentId)

    suspend fun plant(id: Long) = dao.plant(id)

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

    fun setTaskDone(plantId: Long, date: LocalDate, kind: TaskKind, done: Boolean) = viewModelScope.launch {
        val day = date.toEpochDay()
        if (done) dao.insertTaskLog(TaskLog(plantId = plantId, epochDay = day, kind = kind))
        else dao.deleteTaskLog(plantId, day, kind.name)
    }

    fun addNote(date: LocalDate, text: String) = viewModelScope.launch {
        dao.insertNote(GrowNote(epochDay = date.toEpochDay(), text = text))
    }

    fun deleteNote(note: GrowNote) = viewModelScope.launch { dao.deleteNote(note) }
}
