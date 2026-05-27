package com.trigeo.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OutingEntity::class, ReadingEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class TrigeoDatabase : RoomDatabase() {
    abstract fun outingDao(): OutingDao
    abstract fun readingDao(): ReadingDao

    companion object {
        @Volatile private var instance: TrigeoDatabase? = null

        fun get(context: Context): TrigeoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrigeoDatabase::class.java,
                "trigeo.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
