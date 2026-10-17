package com.z.grow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private val today = 20_000L

    private fun data(
        plants: List<Plant> = emptyList(),
        logs: List<TaskLog> = emptyList(),
        earned: List<EarnedAchievement> = emptyList(),
    ) = GrowData(emptyList(), plants, emptyList(), logs, emptyList(), emptyList(), earned)

    private fun plant(id: Long, stage: GrowStage = GrowStage.SEEDLING, start: Long = today - 10, reason: DeathReason? = null) =
        Plant(id = id, name = "P$id", stage = stage, startEpochDay = start, stageStartEpochDay = start + 5, deathReason = reason)

    private fun ids(d: GrowData) = Achievements.newlyEarned(d, today).map { it.second.key }.toSet()

    @Test fun firstPlantGoesToTheOldestPlant() {
        val earned = Achievements.newlyEarned(data(listOf(plant(7), plant(3))), today)
        assertEquals(3L, earned.single { it.first.id == "first_plant" }.second.plantId)
    }

    @Test fun tenthPlantNeedsTenPlants() {
        assertFalse("plants_10" in ids(data((1L..9L).map { plant(it) })))
        val earned = Achievements.newlyEarned(data((1L..10L).map { plant(it) }), today)
        assertEquals(10L, earned.single { it.first.id == "plants_10" }.second.plantId)
    }

    @Test fun deathAndPestsAreAwardedToTheDeadPlant() {
        val earned = Achievements.newlyEarned(data(listOf(plant(1), plant(2, GrowStage.DEAD, reason = DeathReason.PESTS))), today)
        val byId = earned.associate { it.first.id to it.second.plantId }
        assertEquals(2L, byId["first_death"])
        assertEquals(2L, byId["pests"])
        assertFalse("overwatering" in byId)
    }

    @Test fun harvestCountsOnlyHarvestedPlants() {
        val keys = ids(data(listOf(plant(1, GrowStage.HARVESTED), plant(2, GrowStage.DEAD))))
        assertTrue("first_harvest" in keys)
        assertFalse("harvest_5" in keys)
    }

    @Test fun storedAchievementsAreNotAwardedAgain() {
        val plants = listOf(plant(1))
        val first = Achievements.newlyEarned(data(plants), today).map { it.second }
        assertTrue(first.isNotEmpty())
        assertTrue(Achievements.newlyEarned(data(plants, earned = first), today).isEmpty())
    }

    @Test fun perPlantAchievementsAreKeyedByPlant() {
        val old = listOf(plant(1, start = today - 120), plant(2, start = today - 150), plant(3))
        val keys = ids(data(old))
        assertTrue("p_100_days:1" in keys)
        assertTrue("p_100_days:2" in keys)
        assertFalse("p_100_days:3" in keys)
    }

    @Test fun phSweetSpotCountsOnlyInRangeReadings() {
        val p = plant(1)
        val inRange = (1..9).map { TaskLog(id = it.toLong(), plantId = 1, epochDay = today - it, kind = TaskKind.WATER, ph = 6.2) }
        val outOfRange = TaskLog(id = 50, plantId = 1, epochDay = today, kind = TaskKind.WATER, ph = 7.5)
        assertFalse("ph_10" in ids(data(listOf(p), inRange + outOfRange)))
        val tenth = TaskLog(id = 51, plantId = 1, epochDay = today, kind = TaskKind.WATER, ph = 6.0)
        assertTrue("ph_10" in ids(data(listOf(p), inRange + tenth)))
    }
}
