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
    version = 4,
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN retired INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookings ADD COLUMN pickupId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookings ADD COLUMN destinationId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bookings ADD COLUMN pickup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN destination TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN driverName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN carModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN plate TEXT")
                db.execSQL("ALTER TABLE bookings ADD COLUMN departureTime TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE bookings SET
                        pickupId = COALESCE((SELECT r.pickupId FROM rides r WHERE r.rideId = bookings.rideId), 0),
                        destinationId = COALESCE((SELECT r.destinationId FROM rides r WHERE r.rideId = bookings.rideId), 0),
                        pickup = COALESCE((SELECT p.name FROM rides r JOIN locations p ON p.locationId = r.pickupId WHERE r.rideId = bookings.rideId), ''),
                        destination = COALESCE((SELECT dest.name FROM rides r JOIN locations dest ON dest.locationId = r.destinationId WHERE r.rideId = bookings.rideId), ''),
                        driverName = COALESCE((SELECT d.name FROM rides r JOIN drivers d ON d.driverId = r.driverId WHERE r.rideId = bookings.rideId), ''),
                        carModel = COALESCE((SELECT d.carModel FROM rides r JOIN drivers d ON d.driverId = r.driverId WHERE r.rideId = bookings.rideId), ''),
                        phone = COALESCE((SELECT d.phone FROM rides r JOIN drivers d ON d.driverId = r.driverId WHERE r.rideId = bookings.rideId), ''),
                        plate = (SELECT d.plate FROM rides r JOIN drivers d ON d.driverId = r.driverId WHERE r.rideId = bookings.rideId),
                        departureTime = COALESCE((SELECT r.departureTime FROM rides r WHERE r.rideId = bookings.rideId), '')
                """.trimIndent())
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "campuslift.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
