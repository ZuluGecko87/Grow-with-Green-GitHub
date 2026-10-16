package com.z.grow.data

import java.time.LocalDate

/** One upcoming watering or feeding. [overdueDays] > 0 only on today, for a task that was missed. */
data class Due(val kind: TaskKind, val overdueDays: Int)

/**
 * A plant's upcoming waterings and feedings, projected forward from its most recent logs:
 * each logged watering (early or late) restarts the countdown, and a missed one stays due
 * today as overdue. A feeding counts as a watering. Nothing is projected before [today].
 */
class Projection(plant: Plant, logs: List<TaskLog>, private val today: Long) {
    private val waterEvery = plant.waterEveryDays
    private val feedEvery = plant.feedEveryDays
    private val active = plant.stage.isGrowing && (waterEvery > 0 || feedEvery > 0)

    /** When the next watering/feeding fell due, counting from the last log; can be before today. */
    private val waterDue: Long? = if (waterEvery > 0) logs.maxOfOrNull { it.epochDay }?.plus(waterEvery) ?: plant.startEpochDay else null
    private val feedDue: Long? = if (feedEvery > 0) {
        logs.filter { it.kind == TaskKind.FEED }.maxOfOrNull { it.epochDay }?.plus(feedEvery) ?: plant.startEpochDay
    } else {
        null
    }

    private val dues = HashMap<Long, Due>()
    private var simulatedThrough = today - 1
    private var nextWater = waterDue?.let { maxOf(it, today) }
    private var nextFeed = feedDue?.let { maxOf(it, today) }

    fun dueOn(day: Long): Due? {
        if (!active || day < today) return null
        while (simulatedThrough < day && simulatedThrough < today + HORIZON) step(simulatedThrough + 1)
        return dues[day]
    }

    /** Next day on or after today with [kind] due (a feeding counts as watering), or null. */
    fun next(kind: TaskKind): Pair<Long, Due>? {
        for (day in today..today + HORIZON) {
            val due = dueOn(day) ?: continue
            if (kind == TaskKind.WATER || due.kind == kind) return day to due
        }
        return null
    }

    private fun step(day: Long) {
        simulatedThrough = day
        val feed = nextFeed
        val water = nextWater
        if (feed != null && day >= feed) {
            dues[day] = Due(TaskKind.FEED, overdue(day, feedDue))
            nextFeed = day + feedEvery
            if (waterEvery > 0) nextWater = day + waterEvery
        } else if (water != null && day >= water) {
            dues[day] = Due(TaskKind.WATER, overdue(day, waterDue))
            nextWater = day + waterEvery
        }
    }

    private fun overdue(day: Long, originallyDue: Long?) =
        if (day == today && originallyDue != null) (today - originallyDue).toInt().coerceAtLeast(0) else 0

    private companion object {
        const val HORIZON = 400L
    }
}

/** A watering or feeding on one day: scheduled, logged, or both. */
data class PlantTask(
    val plant: Plant,
    val kind: TaskKind,
    val log: TaskLog?,
    /** Days late, for a task still due today that was missed. */
    val overdueDays: Int,
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
    val achievements: List<EarnedAchievement>,
    val today: Long = LocalDate.now().toEpochDay(),
) {
    private val environmentsById = environments.associateBy { it.id }
    private val plantsById = plants.associateBy { it.id }
    private val nutrientsByEnvironment = nutrients.groupBy { it.environmentId }
    private val logsByPlant = logs.groupBy { it.plantId }
    private val projections = HashMap<Long, Projection>()
    private val logsByDay = logs.groupBy { it.epochDay }
    private val journalByDay = journal.groupBy { it.epochDay }
    private val photosByEntry = photos.groupBy { it.entryId }
    private val achievementsByPlant = achievements.groupBy { it.plantId }

    val photoCount: Int = photos.size

    fun photoCountFor(plantId: Long): Int = journal.filter { it.plantId == plantId }.sumOf { photosFor(it.id).size }

    fun achievementsFor(plantId: Long): List<EarnedAchievement> = achievementsByPlant[plantId].orEmpty()

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

    fun projection(plant: Plant): Projection =
        projections.getOrPut(plant.id) { Projection(plant, logsByPlant[plant.id].orEmpty(), today) }

    /** Logged tasks on [date], plus (from today on) what's projected to be due. */
    fun agenda(date: LocalDate): DayAgenda {
        val day = date.toEpochDay()
        val logged = logsByDay[day].orEmpty()
            .mapNotNull { log -> plantsById[log.plantId]?.let { task(it, log.kind, log, overdueDays = 0) } }
        val loggedKeys = logged.mapTo(HashSet()) { it.plant.id to it.kind }
        val due = activePlants.mapNotNull { plant ->
            val next = projection(plant).dueOn(day) ?: return@mapNotNull null
            if ((plant.id to next.kind) in loggedKeys) null else task(plant, next.kind, log = null, overdueDays = next.overdueDays)
        }
        val lights = environments.filter { it.hasLightSchedule }.map {
            LightStatus(it, it.lightHoursOn(day), it.flipEpochDay == day)
        }
        val milestones = plants.filter { it.stageDoneDay == day }.map(::Milestone)
        return DayAgenda(date, due + logged, lights, journalByDay[day].orEmpty(), milestones)
    }

    private fun task(plant: Plant, kind: TaskKind, log: TaskLog?, overdueDays: Int) = PlantTask(
        plant = plant,
        kind = kind,
        log = log,
        overdueDays = overdueDays,
        environment = environment(plant.environmentId),
        nutrients = if (kind == TaskKind.FEED) nutrientsFor(plant.environmentId) else emptyList(),
    )

    companion object {
        val EMPTY = GrowData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}
