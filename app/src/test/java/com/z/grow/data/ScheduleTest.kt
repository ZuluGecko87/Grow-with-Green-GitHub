package com.z.grow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {
    private val today = 20_000L

    private fun plant(waterEvery: Int = 2, feedEvery: Int = 0, start: Long = today - 15) =
        Plant(id = 1, name = "P", stage = GrowStage.VEGETATIVE, startEpochDay = start, waterEveryDays = waterEvery, feedEveryDays = feedEvery)

    private fun water(day: Long, id: Long = day) = TaskLog(id = id, plantId = 1, epochDay = day, kind = TaskKind.WATER)
    private fun feed(day: Long, id: Long = day) = TaskLog(id = id, plantId = 1, epochDay = day, kind = TaskKind.FEED)

    private fun dueDays(p: Plant, logs: List<TaskLog>, days: Int = 8) =
        Projection(p, logs, today).let { proj -> (today until today + days).filter { proj.dueOn(it) != null } }

    @Test fun wateringYesterdayMovesNextWateringToTomorrow() {
        // The reported case: started weeks ago, every 2 days, watered yesterday.
        assertEquals(listOf(today + 1, today + 3, today + 5, today + 7), dueDays(plant(), listOf(water(today - 1))))
    }

    @Test fun wateringEarlyResetsTheCounter() {
        // Was due tomorrow (last watered yesterday), but watered today instead.
        val proj = Projection(plant(), listOf(water(today - 1), water(today)), today)
        assertNull(proj.dueOn(today))
        assertNull(proj.dueOn(today + 1))
        assertEquals(TaskKind.WATER, proj.dueOn(today + 2)?.kind)
    }

    @Test fun missedWateringIsOverdueToday() {
        val proj = Projection(plant(), listOf(water(today - 5)), today)
        val due = proj.dueOn(today)!!
        assertEquals(TaskKind.WATER, due.kind)
        assertEquals(3, due.overdueDays) // was due 3 days ago
        assertEquals(TaskKind.WATER, proj.dueOn(today + 2)?.kind)
        assertNull(proj.dueOn(today + 1))
    }

    @Test fun lateWateringRestartsFromTheLoggedDay() {
        // Overdue, then logged today: next is two days from today, not from the original schedule.
        assertEquals(listOf(today + 2, today + 4, today + 6), dueDays(plant(), listOf(water(today - 5), water(today))))
    }

    @Test fun neverWateredIsDueFromTheStartDate() {
        val due = Projection(plant(start = today - 4), emptyList(), today).dueOn(today)!!
        assertEquals(4, due.overdueDays)
    }

    @Test fun feedingCountsAsWatering() {
        // Water every 2, feed every 6; fed yesterday → water tomorrow, feed in 5 days (which also waters).
        val proj = Projection(plant(waterEvery = 2, feedEvery = 6), listOf(feed(today - 1)), today)
        assertEquals(TaskKind.WATER, proj.dueOn(today + 1)?.kind)
        assertEquals(TaskKind.WATER, proj.dueOn(today + 3)?.kind)
        assertEquals(TaskKind.FEED, proj.dueOn(today + 5)?.kind)
        assertEquals(TaskKind.WATER, proj.dueOn(today + 7)?.kind)
    }

    @Test fun nothingProjectedForThePastOrForNonGrowingStages() {
        assertNull(Projection(plant(), emptyList(), today).dueOn(today - 1))
        val drying = plant().copy(stage = GrowStage.DRYING)
        assertNull(Projection(drying, emptyList(), today).next(TaskKind.WATER))
    }

    @Test fun agendaShowsLogsOnPastDaysAndDueTasksFromToday() {
        val p = plant()
        val data = GrowData(emptyList(), listOf(p), emptyList(), listOf(water(today - 1)), emptyList(), emptyList(), emptyList(), today)
        val yesterday = data.agenda(java.time.LocalDate.ofEpochDay(today - 1)).tasks.single()
        assertEquals(true, yesterday.done)
        assertEquals(emptyList<PlantTask>(), data.agenda(java.time.LocalDate.ofEpochDay(today)).tasks)
        assertEquals(false, data.agenda(java.time.LocalDate.ofEpochDay(today + 1)).tasks.single().done)
    }
}
