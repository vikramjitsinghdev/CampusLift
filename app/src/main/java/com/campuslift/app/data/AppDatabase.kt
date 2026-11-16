package com.campuslift.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [Location::class, Driver::class, Ride::class, Booking::class, SeedInfo::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): RideDao

    /**
     * Loads the JSON seed files the first time, and again whenever they change.
     * Call this before reading rides. It is cheap when nothing changed.
     */
    suspend fun syncSeed(context: Context) = withContext(Dispatchers.IO) {
        val seed = JsonSeeder.read(context)
        if (dao().seedHash() != seed.hash) dao().applySeed(seed)
    }

    companion object {
        /** Version 2 gives every booking a service date and adds the seed_info table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bookings ADD COLUMN serviceDate TEXT NOT NULL DEFAULT ''")
                // Old bookings had no date. Treat them as bookings for the day of the upgrade.
                db.execSQL("UPDATE bookings SET serviceDate = date('now', 'localtime')")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS seed_info (id INTEGER NOT NULL, hash TEXT NOT NULL, PRIMARY KEY(id))"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drivers ADD COLUMN plate TEXT")
                db.execSQL("ALTER TABLE bookings ADD COLUMN demoStage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookings ADD COLUMN demoStartedAt INTEGER")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "campuslift.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
