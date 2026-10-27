package com.campuslift.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(entities = [Location::class, Driver::class, Ride::class, Booking::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): CarpoolDao

    suspend fun seedIfEmpty(context: Context) = withContext(Dispatchers.IO) {
        withTransaction {
            if (dao().locationCount() == 0) {
                val seed = JsonSeeder.read(context)
                dao().insertLocations(seed.locations)
                dao().insertDrivers(seed.drivers)
                dao().insertRides(seed.rides)
            }
        }
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "campuslift.db")
                .build().also { instance = it }
        }
    }
}
