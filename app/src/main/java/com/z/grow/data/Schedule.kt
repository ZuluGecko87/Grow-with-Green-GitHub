package com.z.grow.data

import java.time.LocalDate

/** What a plant needs on [day], or null. Feeding replaces watering on days both fall due. */
fun Plant.taskOn(day: Long): TaskKind? {
    if (!stage.isGrowing || day < startEpochDay) return null
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

/** A watering or feeding on one day: scheduled, logged, or both. */
data class PlantTask(
    val plant: Plant,
    val kind: TaskKind,
    val scheduled: Boolean,
    val log: TaskLog?,
    val environment: GrowEnvironment?,
    val nutrients: List<Nutrient>,
) {
    val done: Boolean get() = log != null
}

/** Drying or curing finishing on a given day. */
data class Milestone(val plant: Plant) {
    val text: String
        get() = when (plant.stage) {
            GrowStage.DRYING -> "${plant.displayName} is done drying (${plant.dryDays} days)"
            else -> "${plant.displayName} has finished curing (${plant.cureWeeks} weeks)"
        }
}

data class LightStatus(val environment: GrowEnvironment, val hoursOn: Int, val isFlipDay: Boolean) {
    val lightsOffMinute: Int get() = (environment.lightsOnMinute + hoursOn * 60) % (24 * 60)
}

data class DayAgenda(
    val date: LocalDate,
    val tasks: List<PlantTask>,
    val lights: List<LightStatus>,
    val journal: List<JournalEntry>,
    val milestones: List<Milestone>,
) {
    val hasMilestone get() = milestones.isNotEmpty()
    val hasWater get() = tasks.any { it.kind == TaskKind.WATER }
    val hasFeed get() = tasks.any { it.kind == TaskKind.FEED }
    val hasFlip get() = lights.any { it.isFlipDay }
    val hasJournal get() = journal.isNotEmpty()
}

/** One row in a journal timeline: a written/recorded entry or a watering/feeding log. */
sealed interface TimelineItem {
    val epochDay: Long
    val plantId: Long?

    data class Entry(val entry: JournalEntry) : TimelineItem {
        override val epochDay get() = entry.epochDay
        override val plantId get() = entry.plantId
    }

    data class Log(val log: TaskLog) : TimelineItem {
        override val epochDay get() = log.epochDay
        override val plantId: Long get() = log.plantId
    }
}

/** Snapshot of everything in the database, with schedule lookups. */
class GrowData(
    val environments: List<GrowEnvironment>,
    val plants: List<Plant>,
    nutrients: List<Nutrient>,
    val logs: List<TaskLog>,
    val journal: List<JournalEntry>,
    photos: List<JournalPhoto>,
) {
    private val environmentsById = environments.associateBy { it.id }
    private val plantsById = plants.associateBy { it.id }
    private val nutrientsByEnvironment = nutrients.groupBy { it.environmentId }
    private val logsByKey = logs.associateBy { Triple(it.plantId, it.epochDay, it.kind) }
    private val logsByDay = logs.groupBy { it.epochDay }
    private val journalByDay = journal.groupBy { it.epochDay }
    private val photosByEntry = photos.groupBy { it.entryId }

    fun environment(id: Long?): GrowEnvironment? = id?.let { environmentsById[it] }

    fun plant(id: Long?): Plant? = id?.let { plantsById[it] }

    fun nutrientsFor(environmentId: Long?): List<Nutrient> =
        environmentId?.let { nutrientsByEnvironment[it] }.orEmpty()

    val activePlants: List<Plant> = plants.filter { !it.stage.isArchived }
    val archivedPlants: List<Plant> = plants.filter { it.stage.isArchived }

    fun plantsIn(environmentId: Long): List<Plant> = activePlants.filter { it.environmentId == environmentId }

    fun photosFor(entryId: Long): List<JournalPhoto> = photosByEntry[entryId].orEmpty()

    /** Newest first. */
    fun logsFor(plantId: Long): List<TaskLog> =
        logs.filter { it.plantId == plantId }.sortedByDescending { it.epochDay }

    /** Journal entries and logs, newest day first; optionally for one plant. */
    fun timeline(plantId: Long? = null, includeLogs: Boolean = true): List<TimelineItem> {
        val entries = journal.map { TimelineItem.Entry(it) }
        val logItems = if (includeLogs) logs.map { TimelineItem.Log(it) } else emptyList()
        return (entries + logItems)
            .filter { plantId == null || it.plantId == plantId }
            .sortedWith(compareByDescending<TimelineItem> { it.epochDay }.thenBy { if (it is TimelineItem.Log) 1 else 0 })
    }

    fun agenda(date: LocalDate): DayAgenda {
        val day = date.toEpochDay()
        val scheduled = plants.mapNotNull { plant ->
            val kind = plant.taskOn(day) ?: return@mapNotNull null
            task(plant, kind, scheduled = true, log = logsByKey[Triple(plant.id, day, kind)])
        }
        val scheduledKeys = scheduled.mapTo(HashSet()) { it.plant.id to it.kind }
        val extra = logsByDay[day].orEmpty()
            .filter { (it.plantId to it.kind) !in scheduledKeys }
            .mapNotNull { log -> plantsById[log.plantId]?.let { task(it, log.kind, scheduled = false, log = log) } }
        val lights = environments.filter { it.hasLightSchedule }.map {
            LightStatus(it, it.lightHoursOn(day), it.flipEpochDay == day)
        }
        val milestones = plants.filter { it.stageDoneDay == day }.map(::Milestone)
        return DayAgenda(date, scheduled + extra, lights, journalByDay[day].orEmpty(), milestones)
    }

    private fun task(plant: Plant, kind: TaskKind, scheduled: Boolean, log: TaskLog?) = PlantTask(
        plant = plant,
        kind = kind,
        scheduled = scheduled,
        log = log,
        environment = environment(plant.environmentId),
        nutrients = if (kind == TaskKind.FEED) nutrientsFor(plant.environmentId) else emptyList(),
    )

    companion object {
        val EMPTY = GrowData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
