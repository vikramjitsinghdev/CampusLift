package com.campuslift.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Opens real earlier database versions and upgrades them to the current schema. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "campuslift-migration-test.db"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun version1BookingKeepsItsDataAndGetsADate() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO locations (locationId, name) VALUES (1, 'A'), (2, 'B')")
            execSQL("INSERT INTO drivers (driverId, name, carModel, phone) VALUES (1, 'Driver', 'Van', '555-0100')")
            execSQL("INSERT INTO rides (rideId, driverId, pickupId, destinationId, departureTime, totalSeats) VALUES (1, 1, 1, 2, '17:00', 4)")
            execSQL("INSERT INTO bookings (rideId, studentId, status) VALUES (1, 1, 'confirmed')")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            name, 4, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4
        )

        db.query(
            "SELECT rideId, studentId, status, serviceDate, pickupId, destinationId, pickup, destination, driverName, carModel, phone, departureTime FROM bookings"
        ).use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(1, row.getInt(0))
            assertEquals(1, row.getInt(1))
            assertEquals("confirmed", row.getString(2))
            assertEquals(LocalDate.now().toString(), row.getString(3))
            assertEquals(1, row.getInt(4))
            assertEquals(2, row.getInt(5))
            assertEquals("A", row.getString(6))
            assertEquals("B", row.getString(7))
            assertEquals("Driver", row.getString(8))
            assertEquals("Van", row.getString(9))
            assertEquals("555-0100", row.getString(10))
            assertEquals("17:00", row.getString(11))
        }
        db.query("SELECT COUNT(*) FROM seed_info").use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(0, row.getInt(0))
        }
        db.query("SELECT demoStage, demoStartedAt FROM bookings").use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(0, row.getInt(0))
            assertTrue(row.isNull(1))
        }
    }

    @Test fun version3UpgradeBackfillsBookingSnapshotsAndSurvivesSyncSeed() = runBlocking {
        val dbName = "campuslift-v3-upgrade-${UUID.randomUUID()}.db"
        try {
            helper.createDatabase(dbName, 3).apply {
                execSQL("INSERT INTO locations (locationId, name, latitude, longitude) VALUES (4, 'Old SUB', 45.9, -66.6), (6, 'Old Mall', 45.93, -66.66)")
                execSQL("INSERT INTO drivers (driverId, name, carModel, phone, plate) VALUES (1, 'Old Driver', 'Old Van', '555-0199', 'OLD-1')")
                execSQL("INSERT INTO rides (rideId, driverId, pickupId, destinationId, departureTime, totalSeats) VALUES (2, 1, 4, 6, '18:00', 4)")
                execSQL("INSERT INTO bookings (rideId, studentId, serviceDate, status, demoStage) VALUES (2, 1, '2026-11-20', 'confirmed', 0)")
                execSQL("INSERT INTO seed_info (id, hash) VALUES (1, 'old-seed-hash')")
                close()
            }

            helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4).close()

            val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()
            try {
                // Real seed sync overwrites driver/location/ride rows with current JSON assets,
                // while the upgraded booking keeps its copied historical snapshot.
                upgraded.syncSeed(context)
                val item = upgraded.dao().getBooking(1, 1)!!
                assertEquals("Old SUB", item.pickup)
                assertEquals("Old Mall", item.destination)
                assertEquals("Old Driver", item.driverName)
                assertEquals("Old Van", item.carModel)
                assertEquals("555-0199", item.phone)
                assertEquals("OLD-1", item.plate)
                assertEquals("18:00", item.departureTime)
                assertFalse(item.scheduleUnavailable)
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }
}
