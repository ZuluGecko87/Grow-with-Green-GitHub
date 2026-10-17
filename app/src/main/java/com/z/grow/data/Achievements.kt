package com.z.grow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An unlocked achievement. [key] is the achievement id, plus ":plantId" for per-plant ones. */
@Entity(tableName = "achievements")
data class EarnedAchievement(
    @PrimaryKey val key: String,
    val achievementId: String,
    /** The plant that earned it, if any; its card shows the badge. */
    val plantId: Long?,
    val earnedEpochDay: Long,
)

class Achievement internal constructor(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    /** Per-plant achievements can be earned once by every plant. */
    val perPlant: Boolean,
    private val globalRule: ((AchievementContext) -> Award?)?,
    private val plantRule: ((Plant, AchievementContext) -> Boolean)?,
) {
    internal fun evaluate(context: AchievementContext): List<Award> = when {
        globalRule != null -> listOfNotNull(globalRule.invoke(context))
        plantRule != null -> context.data.plants.filter { plantRule.invoke(it, context) }.map { Award(it.id) }
        else -> emptyList()
    }

    fun keyFor(plantId: Long?) = if (perPlant) "$id:$plantId" else id
}

internal data class Award(val plantId: Long?)

internal class AchievementContext(val data: GrowData, val today: Long) {
    val plantsInOrder = data.plants.sortedBy { it.id }
    val harvested = data.plants.filter { it.stage == GrowStage.HARVESTED }.sortedWith(compareBy({ it.stageStart }, { it.id }))
    val dead = data.plants.filter { it.stage == GrowStage.DEAD }.sortedWith(compareBy({ it.stageStart }, { it.id }))
    val logsInOrder = data.logs.sortedWith(compareBy({ it.epochDay }, { it.id }))

    fun firstDeathBy(reason: DeathReason) = dead.firstOrNull { it.deathReason == reason }
    fun firstIn(vararg stages: GrowStage) = plantsInOrder.firstOrNull { it.stage in stages }
}

private fun Plant?.award() = this?.let { Award(it.id) }
private fun TaskLog?.award() = this?.let { Award(it.plantId) }
private fun earned(condition: Boolean) = if (condition) Award(null) else null

object Achievements {
    private fun global(id: String, emoji: String, title: String, description: String, rule: (AchievementContext) -> Award?) =
        Achievement(id, emoji, title, description, perPlant = false, globalRule = rule, plantRule = null)

    private fun perPlant(id: String, emoji: String, title: String, description: String, rule: (Plant, AchievementContext) -> Boolean) =
        Achievement(id, emoji, title, description, perPlant = true, globalRule = null, plantRule = rule)

    private const val PH_LOW = 5.8
    private const val PH_HIGH = 6.8

    val all: List<Achievement> = listOf(
        // Growing
        global("first_plant", "🌱", "Green Thumb Initiate", "Created your first plant. Everyone starts somewhere.") {
            it.plantsInOrder.getOrNull(0).award()
        },
        global("plants_5", "🪴", "Getting Serious", "Five plants and counting.") { it.plantsInOrder.getOrNull(4).award() },
        global("plants_10", "🌳", "Ten Little Plants", "Grew your 10th plant. This is a lifestyle now.") {
            it.plantsInOrder.getOrNull(9).award()
        },
        global("plants_25", "🏭", "Commercial Operation", "25 plants. Does your landlord know?") {
            it.plantsInOrder.getOrNull(24).award()
        },
        global("first_flower", "🌸", "Bloom Time", "Got a plant to flowering.") {
            it.firstIn(GrowStage.FLOWERING, GrowStage.DRYING, GrowStage.CURING, GrowStage.HARVESTED).award()
        },

        // Harvest
        global("first_dry", "🪝", "Hang Time", "Hung your first harvest up to dry.") {
            it.firstIn(GrowStage.DRYING, GrowStage.CURING).award()
        },
        global("first_cure", "🫙", "Jar Life", "Started your first cure.") { it.firstIn(GrowStage.CURING).award() },
        global("cure_complete", "⏳", "Patience Pays Off", "Made it through a full cure.") { context ->
            context.plantsInOrder.firstOrNull { it.stage == GrowStage.CURING && context.today >= (it.stageDoneDay ?: Long.MAX_VALUE) }.award()
        },
        global("first_harvest", "🌾", "First Harvest", "Harvested your first plant. The wait was worth it.") {
            it.harvested.getOrNull(0).award()
        },
        global("harvest_5", "🧺", "Bountiful", "Five harvests in the books.") { it.harvested.getOrNull(4).award() },
        global("harvest_10", "🏆", "Master Grower", "Harvested your 10th plant.") { it.harvested.getOrNull(9).award() },

        // The other kind of milestones
        global("first_death", "💀", "Well, That Happened", "Killed your first plant. Truly, a natural talent.") {
            it.dead.getOrNull(0).award()
        },
        global("deaths_5", "🪦", "Serial Plant Killer", "Five dead plants. The others have started to worry.") {
            it.dead.getOrNull(4).award()
        },
        global("pests", "🐛", "Bugs Everywhere", "Lost a plant to pests. They thank you for the meal.") {
            it.firstDeathBy(DeathReason.PESTS).award()
        },
        global("overwatering", "🌊", "Drowned in Love", "Overwatered a plant to death. It wasn't that thirsty.") {
            it.firstDeathBy(DeathReason.OVERWATERING).award()
        },
        global("underwatering", "🏜️", "Sahara Mode", "Forgot to water. The plant noticed.") {
            it.firstDeathBy(DeathReason.UNDERWATERING).award()
        },
        global("temperature", "🔥", "Too Hot to Handle", "Lost a plant to the temperature. Thermostats exist, you know.") {
            it.firstDeathBy(DeathReason.TEMPERATURE).award()
        },
        global("nutrient_burn", "🧪", "Chemistry Experiment", "Nutrient-burned a plant to death. Less is more.") {
            it.firstDeathBy(DeathReason.NUTRIENT_BURN).award()
        },
        global("disease", "🍄", "Fungus Among Us", "Mold, rot or disease claimed one.") {
            it.firstDeathBy(DeathReason.DISEASE).award()
        },
        global("culled", "✂️", "Tough Love", "Culled a male or hermie. It had to be done.") {
            it.firstDeathBy(DeathReason.CULLED).award()
        },

        // Care
        global("first_feed", "🍽️", "Fine Dining", "Fed a plant its first nutrients.") {
            it.logsInOrder.firstOrNull { log -> log.kind == TaskKind.FEED }.award()
        },
        global("water_10", "💧", "Hydration Station", "Logged 10 waterings and feedings.") { it.logsInOrder.getOrNull(9).award() },
        global("water_100", "🚿", "Waterboy", "Logged 100 waterings and feedings.") { it.logsInOrder.getOrNull(99).award() },
        global("ph_10", "🎯", "Dialed In", "Logged 10 pH readings in the sweet spot ($PH_LOW–$PH_HIGH).") { context ->
            context.logsInOrder.filter { log -> log.ph != null && log.ph in PH_LOW..PH_HIGH }.getOrNull(9).award()
        },

        // Journal
        global("first_note", "📓", "Dear Diary", "Wrote your first journal note.") { context ->
            val note = context.data.journal.filter { it.kind == EntryKind.NOTE }.minByOrNull { it.id }
            note?.let { Award(it.plantId) }
        },
        global("photos_10", "📸", "Paparazzi", "Added 10 photos to the journal.") { earned(it.data.photoCount >= 10) },
        global("profile_photo", "🤳", "Glamour Shot", "Gave a plant its profile photo.") {
            it.plantsInOrder.firstOrNull { plant -> plant.photoFileName != null }.award()
        },

        // Setup
        global("first_space", "🏠", "Home Sweet Grow", "Set up your first grow space.") { earned(it.data.environments.isNotEmpty()) },
        global("outdoor", "🌞", "Touch Grass", "Set up an outdoor grow. Fresh air!") {
            earned(it.data.environments.any { env -> env.type == EnvironmentType.OUTDOOR })
        },
        global("flip", "⏰", "Flip the Switch", "Planned a light change for flowering.") {
            earned(it.data.environments.any { env -> env.flipEpochDay != null })
        },
        global("nutrients_5", "🧴", "Mad Scientist", "Five nutrients in one feeding lineup.") { context ->
            earned(context.data.environments.any { context.data.nutrientsFor(it.id).size >= 5 })
        },

        // Per plant
        perPlant("p_100_days", "📅", "Centurion", "Kept a plant going for 100 days.") { plant, context ->
            val end = if (plant.stage == GrowStage.DEAD) plant.stageStart else context.today
            end - plant.startEpochDay >= 100
        },
        perPlant("p_25_logs", "💦", "Well Watered", "25 waterings and feedings for one plant.") { plant, context ->
            context.data.logs.count { it.plantId == plant.id } >= 25
        },
        perPlant("p_photos_5", "🌟", "Photogenic", "Five journal photos of one plant.") { plant, context ->
            context.data.photoCountFor(plant.id) >= 5
        },
        perPlant("p_speed", "🏎️", "Speed Run", "Harvested within 70 days of starting.") { plant, _ ->
            plant.stage == GrowStage.HARVESTED && plant.stageStart - plant.startEpochDay <= 70
        },
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Achievement? = byId[id]

    /** Achievements earned by the current data that haven't been stored yet. */
    fun newlyEarned(data: GrowData, today: Long): List<Pair<Achievement, EarnedAchievement>> {
        val stored = data.achievements.mapTo(HashSet()) { it.key }
        val context = AchievementContext(data, today)
        return all.flatMap { achievement ->
            achievement.evaluate(context)
                .filter { achievement.keyFor(it.plantId) !in stored }
                .map { award ->
                    achievement to EarnedAchievement(achievement.keyFor(award.plantId), achievement.id, award.plantId, today)
                }
        }
    }
}
