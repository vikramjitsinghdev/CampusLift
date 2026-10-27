package com.campuslift.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campuslift.app.routing.OpenRouteService
import com.campuslift.app.routing.RouteError
import com.campuslift.app.routing.RouteResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.seedIfEmpty(context)
    }
    @After fun close() { db.close() }

    @Test fun seedSearchDuplicateCancellationAndReseed() = runBlocking {
        val dao = db.dao()
        assertEquals(6, dao.locationCount())
        assertEquals("Daniel Moore", dao.searchRides(2, 5).single().driverName)
        assertTrue(dao.searchRides(2, 1).isEmpty())
        assertEquals(BookResult.NOT_FOUND, dao.bookRide(999))
        assertEquals(BookResult.SUCCESS, dao.bookRide(1))
        assertEquals(BookResult.ALREADY_BOOKED, dao.bookRide(1))
        assertEquals(3, dao.searchRides(2, 5).single().seatsLeft)
        db.seedIfEmpty(context)
        val booking = dao.myBookings().single()
        assertEquals(0, dao.cancelBooking(booking.bookingId, 2))
        assertEquals(1, dao.cancelBooking(booking.bookingId))
        assertTrue(dao.myBookings().isEmpty())
        assertEquals(4, dao.seatsLeft(1))
        assertEquals(BookResult.SUCCESS, dao.bookRide(1))
    }

    @Test fun concurrentBookingsCannotOverfill() = runBlocking {
        val results = (1..8).map { student -> async { db.dao().bookRide(1, student) } }.awaitAll()
        assertEquals(4, results.count { it == BookResult.SUCCESS })
        assertEquals(4, results.count { it == BookResult.RIDE_FULL })
        assertEquals(0, db.dao().seatsLeft(1))
    }

    @Test fun driverLimitAppliesAcrossRidesAtSameTime() = runBlocking {
        db.dao().insertRides(listOf(Ride(99, 1, 2, 6, "16:00", 4)))
        (1..4).forEach { assertEquals(BookResult.SUCCESS, db.dao().bookRide(1, it)) }
        assertEquals(BookResult.DRIVER_FULL, db.dao().bookRide(99, 5))
    }

    @Test fun bookingSurvivesDatabaseReopen() = runBlocking {
        val name = "campuslift-test-${UUID.randomUUID()}.db"
        var persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            persistent.seedIfEmpty(context)
            persistent.dao().bookRide(1)
            persistent.close()
            persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            persistent.seedIfEmpty(context)
            assertEquals(1, persistent.dao().myBookings().size)
        } finally { persistent.close(); context.deleteDatabase(name) }
    }

    @Test fun missingRoutingConfigurationIsHandledWithoutNetwork() = runBlocking {
        val a = Location(1, "A", 45.95, -66.64)
        val b = Location(2, "B", 45.93, -66.66)
        assertEquals(RouteResult.Failure(RouteError.MISSING_KEY), OpenRouteService("").route(a, b))
        assertEquals(RouteResult.Failure(RouteError.MISSING_COORDINATES), OpenRouteService("").route(a.copy(latitude = null), b))
    }

    @Test fun rejectsBrokenSeedRelationship() {
        val seed = JsonSeeder.read(context)
        try {
            JsonSeeder.validate(seed.copy(rides = listOf(seed.rides.first().copy(driverId = 999))))
            fail("Invalid driver reference should fail validation")
        } catch (_: IllegalArgumentException) { }
    }
}
