package com.z.grow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class EnvironmentType(val label: String) {
    INDOOR("Indoor"), OUTDOOR("Outdoor"), GREENHOUSE("Greenhouse")
}

enum class GrowMedium(val label: String) {
    SOIL("Soil"),
    LIVING_SOIL("Living soil"),
    COCO("Coco coir"),
    SOILLESS("Soilless mix"),
    HYDRO("Hydroponic"),
    ROCKWOOL("Rockwool"),
    AQUAPONIC("Aquaponic"),
    OTHER("Other"),
}

enum class PlantType(val label: String) {
    CANNABIS("Cannabis"),
    TOMATO("Tomato"),
    PEPPER("Pepper"),
    HERB("Herb"),
    LEAFY_GREEN("Leafy green"),
    MICROGREENS("Microgreens"),
    FRUIT("Fruit"),
    FLOWER("Flower"),
    HOUSEPLANT("Houseplant"),
    OTHER("Other"),
}

enum class Genetics(val label: String) {
    INDICA("Indica"),
    INDICA_DOMINANT("Indica-dominant hybrid"),
    HYBRID("Balanced hybrid"),
    SATIVA_DOMINANT("Sativa-dominant hybrid"),
    SATIVA("Sativa"),
    RUDERALIS("Ruderalis / auto"),
    HEIRLOOM("Heirloom"),
    F1_HYBRID("F1 hybrid"),
    OPEN_POLLINATED("Open-pollinated"),
    UNKNOWN("Unknown"),
}

enum class PlantSource(val label: String) {
    FEMINIZED("Feminized seed"),
    REGULAR("Regular seed"),
    AUTOFLOWER("Autoflower seed"),
    SEED("Seed"),
    CLONE("Clone / cutting"),
    START("Nursery start"),
}

enum class GrowStage(val label: String) {
    GERMINATION("Germination"),
    SEEDLING("Seedling"),
    VEGETATIVE("Vegetative"),
    FLOWERING("Flowering"),
    HARVESTED("Harvested"),
}

enum class TaskKind(val label: String) {
    WATER("Water"), FEED("Feed")
}

/** A grow space: a tent, room, greenhouse or outdoor plot. */
@Entity(tableName = "environments")
data class GrowEnvironment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val type: EnvironmentType = EnvironmentType.INDOOR,
    val medium: GrowMedium = GrowMedium.SOIL,
    val size: String = "",
    val lightType: String = "",
    /** Minute of the day the lights come on. */
    val lightsOnMinute: Int = 6 * 60,
    val vegLightHours: Int = 18,
    /** Day the light cycle switches to [flowerLightHours]; null if no flip is planned. */
    val flipEpochDay: Long? = null,
    val flowerLightHours: Int = 12,
    val notes: String = "",
) {
    val hasLightSchedule: Boolean get() = type != EnvironmentType.OUTDOOR

    fun lightHoursOn(epochDay: Long): Int =
        if (flipEpochDay != null && epochDay >= flipEpochDay) flowerLightHours else vegLightHours
}

/** One product in an environment's feeding lineup. */
@Entity(
    tableName = "nutrients",
    foreignKeys = [
        ForeignKey(
            entity = GrowEnvironment::class,
            parentColumns = ["id"],
            childColumns = ["environmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("environmentId")],
)
data class Nutrient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val environmentId: Long = 0,
    val name: String = "",
    val brand: String = "",
    val dose: String = "",
)

@Entity(
    tableName = "plants",
    foreignKeys = [
        ForeignKey(
            entity = GrowEnvironment::class,
            parentColumns = ["id"],
            childColumns = ["environmentId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("environmentId")],
)
data class Plant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val type: PlantType = PlantType.CANNABIS,
    val strain: String = "",
    val genetics: Genetics = Genetics.UNKNOWN,
    val source: PlantSource = PlantSource.SEED,
    val manufacturer: String = "",
    val environmentId: Long? = null,
    val stage: GrowStage = GrowStage.SEEDLING,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    /** 0 means no watering schedule. */
    val waterEveryDays: Int = 2,
    /** 0 means no feeding schedule. Feed days replace that day's watering. */
    val feedEveryDays: Int = 0,
    val notes: String = "",
    @ColumnInfo(defaultValue = "1") val remindersEnabled: Boolean = true,
) {
    val displayName: String get() = name.ifBlank { strain.ifBlank { "Unnamed plant" } }
}

/** A watering or feeding that was done, with optional readings. */
@Entity(
    tableName = "task_logs",
    foreignKeys = [
        ForeignKey(
            entity = Plant::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["plantId", "epochDay", "kind"], unique = true)],
)
data class TaskLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val epochDay: Long,
    val kind: TaskKind,
    val ph: Double? = null,
    val ppm: Int? = null,
    @ColumnInfo(defaultValue = "") val amount: String = "",
) {
    /** e.g. "pH 6.3 · 650 ppm · 1 gal"; empty if nothing was recorded. */
    val readings: String
        get() = listOfNotNull(
            ph?.let { "pH %.1f".format(it) },
            ppm?.let { "$it ppm" },
            amount.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
}

/** A dated journal entry, optionally about one plant. Table name kept from v1. */
@Entity(tableName = "notes")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long = LocalDate.now().toEpochDay(),
    val text: String = "",
    val plantId: Long? = null,
)

/** A photo attached to a journal entry; [fileName] lives in the app's photos folder. */
@Entity(
    tableName = "journal_photos",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class JournalPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val fileName: String,
)
