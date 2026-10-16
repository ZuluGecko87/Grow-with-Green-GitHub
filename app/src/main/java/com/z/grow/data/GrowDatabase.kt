package com.z.grow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GrowEnvironment::class, Nutrient::class, Plant::class, TaskLog::class, GrowNote::class],
    version = 1,
    exportSchema = false,
)
abstract class GrowDatabase : RoomDatabase() {
    abstract fun dao(): GrowDao

    companion object {
        @Volatile private var instance: GrowDatabase? = null

        fun get(context: Context): GrowDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GrowDatabase::class.java, "grow.db")
                .build()
                .also { instance = it }
        }
    }
}
