package com.z.grow.data

import java.time.LocalDate

/** What a plant needs on [day], or null. Feeding replaces watering on days both fall due. */
fun Plant.taskOn(day: Long): TaskKind? {
    if (stage == GrowStage.HARVESTED || day < startEpochDay) return null
    val offset = day - startEpochDay
    if (feedEveryDays > 0 && offset % feedEveryDays == 0L) return TaskKind.FEED
    if (waterEveryDays > 0 && offset % waterEveryDays == 0L) return TaskKind.WATER
    return null
}

/** Next day on or after [from] the plant gets [kind]. Feed days count as watering. */
fun Plant.nextDay(kind: TaskKind, from: Long): Long? {
    val start = maxOf(from, startEpochDay)
    for (day in start..start + 366) {
        val task = taskOn(day) ?: continue
        if (kind == TaskKind.WATER || task == kind) return day
    }
    return null
}

data class PlantTask(
    val plant: Plant,
    val kind: TaskKind,
    val done: Boolean,
    val environment: GrowEnvironment?,
    val nutrients: List<Nutrient>,
)

data class LightStatus(val environment: GrowEnvironment, val hoursOn: Int, val isFlipDay: Boolean) {
    val lightsOffMinute: Int get() = (environment.lightsOnMinute + hoursOn * 60) % (24 * 60)
}

data class DayAgenda(
    val date: LocalDate,
    val tasks: List<PlantTask>,
    val lights: List<LightStatus>,
    val notes: List<GrowNote>,
) {
    val hasWater get() = tasks.any { it.kind == TaskKind.WATER }
    val hasFeed get() = tasks.any { it.kind == TaskKind.FEED }
    val hasFlip get() = lights.any { it.isFlipDay }
    val hasNotes get() = notes.isNotEmpty()
}

/** Snapshot of everything in the database, with schedule lookups. */
class GrowData(
    val environments: List<GrowEnvironment>,
    val plants: List<Plant>,
    nutrients: List<Nutrient>,
    logs: List<TaskLog>,
    notes: List<GrowNote>,
) {
    private val environmentsById = environments.associateBy { it.id }
    private val nutrientsByEnvironment = nutrients.groupBy { it.environmentId }
    private val doneTasks = logs.mapTo(HashSet()) { Triple(it.plantId, it.epochDay, it.kind) }
    private val notesByDay = notes.groupBy { it.epochDay }

    fun environment(id: Long?): GrowEnvironment? = id?.let { environmentsById[it] }

    fun nutrientsFor(environmentId: Long?): List<Nutrient> =
        environmentId?.let { nutrientsByEnvironment[it] }.orEmpty()

    fun plantsIn(environmentId: Long): List<Plant> = plants.filter { it.environmentId == environmentId }

    fun agenda(date: LocalDate): DayAgenda {
        val day = date.toEpochDay()
        val tasks = plants.mapNotNull { plant ->
            val kind = plant.taskOn(day) ?: return@mapNotNull null
            PlantTask(
                plant = plant,
                kind = kind,
                done = Triple(plant.id, day, kind) in doneTasks,
                environment = environment(plant.environmentId),
                nutrients = if (kind == TaskKind.FEED) nutrientsFor(plant.environmentId) else emptyList(),
            )
        }
        val lights = environments.filter { it.hasLightSchedule }.map {
            LightStatus(it, it.lightHoursOn(day), it.flipEpochDay == day)
        }
        return DayAgenda(date, tasks, lights, notesByDay[day].orEmpty())
    }

    companion object {
        val EMPTY = GrowData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
