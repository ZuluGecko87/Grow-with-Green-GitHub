package com.z.grow.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GrowEnvironment::class,
        Nutrient::class,
        Plant::class,
        TaskLog::class,
        JournalEntry::class,
        JournalPhoto::class,
        EarnedAchievement::class,
    ],
    version = GrowDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class GrowDatabase : RoomDatabase() {
    abstract fun dao(): GrowDao

    /** Flushes the write-ahead log into the main file so it can be copied. */
    fun checkpoint() {
        openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
    }

    companion object {
        const val VERSION = 5
        const val NAME = "grow.db"

        @Volatile private var instance: GrowDatabase? = null

        fun get(context: Context): GrowDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GrowDatabase::class.java, NAME)
                .build()
                .also { instance = it }
        }

        /** Closes the open database, e.g. before replacing its file during a restore. */
        fun close() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
