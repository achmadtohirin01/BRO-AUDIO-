package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DspSettings::class], version = 3, exportSchema = false)
abstract class DspDatabase : RoomDatabase() {
    abstract fun dspSettingsDao(): DspSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: DspDatabase? = null

        fun getDatabase(context: Context): DspDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DspDatabase::class.java,
                    "bro_audio_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
