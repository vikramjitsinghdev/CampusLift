package com.campuslift.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Opens a real version 1 database and upgrades it to the current schema. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "campuslift-migration-test.db"

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

        val db = helper.runMigrationsAndValidate(name, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)

        db.query("SELECT rideId, studentId, status, serviceDate FROM bookings").use { row ->
            assertTrue(row.moveToFirst())
            assertEquals(1, row.getInt(0))
            assertEquals(1, row.getInt(1))
            assertEquals("confirmed", row.getString(2))
            assertEquals(LocalDate.now().toString(), row.getString(3))
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
}
