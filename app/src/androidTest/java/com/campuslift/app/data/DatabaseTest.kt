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
import java.time.LocalDateTime
import java.util.UUID

/**
 * Seed schedule used below: ride 2 is SUB (4) to Regent Mall (6) at 18:00 with driver 1,
 * ride 12 is the same trip at 23:00 with driver 4, ride 1 is SUB to Downtown at 16:00.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private val student = DemoStudent.ID

    // A fixed "now" makes the tests independent of the real clock.
    private val today = "2026-11-16"
    private val tomorrow = "2026-11-17"
    private val afternoon = ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 15, 0))   // before every ride
    private val evening = ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 18, 20))    // ride 2 left 20 min ago
    private val night = ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 18, 31))      // ride 2 left 31 min ago

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.syncSeed(context)
    }
    @After fun close() { db.close() }

    @Test fun seedSearchDuplicateCancellationAndRebook() = runBlocking {
        val dao = db.dao()
        assertEquals(6, dao.locationCount())
        assertEquals(30, dao.getAllRides().size)
        assertEquals(10, dao.getAllRides().map { it.driverId }.distinct().size)
        val found = dao.availableRides(4, 6, today, afternoon)
        assertEquals(listOf(2, 12), found.map { it.rideId })
        assertEquals(listOf("18:00", "23:00"), found.map { it.departureTime })
        assertEquals("Daniel Moore", found.first().driverName)
        assertEquals(today, found.first().serviceDate)
        assertTrue(dao.availableRides(4, 1, today, afternoon).isEmpty())

        assertEquals(BookResult.NOT_FOUND, dao.bookRide(999, student, today, afternoon))
        assertEquals(BookResult.SUCCESS, dao.bookRide(2, student, today, afternoon))
        assertEquals(BookResult.ALREADY_BOOKED, dao.bookRide(2, student, today, afternoon))
        assertEquals(3, dao.availableRides(4, 6, today, afternoon).first().seatsLeft)

        db.syncSeed(context)   // an unchanged seed must not touch bookings
        val booking = dao.activeBookings(student, afternoon).single()
        assertEquals(4, booking.pickupId)
        assertEquals(6, booking.destinationId)
        assertEquals(today, booking.serviceDate)
        assertEquals(BookingStatus.CONFIRMED, booking.status)
        assertEquals("DEMO-101", booking.plate)
        assertEquals(booking.bookingId, dao.confirmedBookingId(2, student, today))

        assertEquals(0, dao.cancelBooking(booking.bookingId, 2))   // another student cannot cancel it
        assertEquals(1, dao.cancelBooking(booking.bookingId, student))
        assertTrue(dao.activeBookings(student, afternoon).isEmpty())
        assertEquals(4, dao.seatsLeft(2, today))
        assertEquals(BookResult.SUCCESS, dao.bookRide(2, student, today, afternoon))
    }

    @Test fun calendarDateDecidesWhichTimesAreAvailable() = runBlocking {
        val dao = db.dao()
        // Today at 18:20 the 18:00 ride is gone; tomorrow and later show every time again.
        assertEquals(listOf(12), dao.availableRides(4, 6, today, evening).map { it.rideId })
        assertEquals(listOf(2, 12), dao.availableRides(4, 6, tomorrow, evening).map { it.rideId })
        assertEquals(listOf(2, 12), dao.availableRides(4, 6, "2026-11-25", evening).map { it.rideId })
        assertTrue(dao.availableRides(4, 6, "2026-11-15", evening).isEmpty())   // yesterday

        assertEquals(BookResult.DEPARTED, dao.bookRide(2, student, today, evening))
        assertEquals(BookResult.DEPARTED, dao.bookRide(2, student, "2026-11-15", evening))
        assertEquals(BookResult.SUCCESS, dao.bookRide(2, student, tomorrow, evening))
        assertEquals(BookResult.SUCCESS, dao.bookRide(2, student, "2026-11-25", evening))
        // Each date has its own seats.
        assertEquals(4, dao.seatsLeft(2, today))
        assertEquals(3, dao.seatsLeft(2, tomorrow))
        assertEquals(3, dao.availableRides(4, 6, "2026-11-25", evening).first().seatsLeft)
    }

    @Test fun historyIsSplitIntoActiveCompletedAndCancelled() = runBlocking {
        val dao = db.dao()
        dao.bookRide(2, student, today, afternoon)      // 18:00 today, will be completed
        dao.bookRide(12, student, today, afternoon)     // 23:00 today, will be cancelled
        dao.bookRide(1, student, tomorrow, afternoon)   // 16:00 tomorrow, stays active
        val cancelled = dao.confirmedBookingId(12, student, today)!!
        dao.cancelBooking(cancelled, student)

        // 20 minutes after leaving, the 18:00 ride still counts as active.
        assertEquals(listOf(today, tomorrow), dao.bookingsIn(RideState.ACTIVE, student, evening).map { it.serviceDate })
        assertTrue(dao.bookingsIn(RideState.COMPLETED, student, evening).isEmpty())

        // 31 minutes after leaving, it has moved to Completed.
        assertEquals(listOf("16:00"), dao.bookingsIn(RideState.ACTIVE, student, night).map { it.departureTime })
        assertEquals(listOf("18:00"), dao.bookingsIn(RideState.COMPLETED, student, night).map { it.departureTime })
        assertEquals(listOf("23:00"), dao.bookingsIn(RideState.CANCELLED, student, night).map { it.departureTime })

        assertEquals(3, dao.allBookings(student).size)             // nothing is ever deleted
        assertEquals(BookingStatus.CANCELLED, dao.getBooking(cancelled, student)?.status)
        assertNull(dao.getBooking(cancelled, 2))                   // another student cannot open it
    }

    @Test fun demoRequiresBothCheckInsAndEndsInHistory() = runBlocking {
        val dao = db.dao()
        assertEquals(BookResult.SUCCESS, dao.bookRide(2, student, today, afternoon))
        val id = dao.confirmedBookingId(2, student, today)!!
        assertEquals(0, dao.advanceDemo(id, student, 1, 1000))
        assertEquals(1, dao.advanceDemo(id, student, 0, 1000))
        assertEquals(0, dao.advanceDemo(id, student, 0, 1000))
        assertEquals(1, dao.advanceDemo(id, student, 1, 2000))
        assertEquals(2000L, dao.getBooking(id, student)?.demoStartedAt)
        assertEquals(0, dao.cancelBooking(id, student))
        assertEquals(1, dao.advanceDemo(id, student, 2, 32_000))
        assertEquals(id, dao.bookingsIn(RideState.COMPLETED, student, afternoon).single().bookingId)
    }

    @Test fun concurrentBookingsCannotOverfill() = runBlocking {
        val results = (1..8).map { s -> async { db.dao().bookRide(2, s, today, afternoon) } }.awaitAll()
        assertEquals(4, results.count { it == BookResult.SUCCESS })
        assertEquals(4, results.count { it == BookResult.RIDE_FULL })
        assertEquals(0, db.dao().seatsLeft(2, today))
    }

    @Test fun driverLimitAppliesAcrossRidesAtSameTime() = runBlocking {
        db.dao().upsertRides(listOf(Ride(99, 1, 4, 5, "18:00", 4)))    // same driver, same time as ride 2
        (1..4).forEach { assertEquals(BookResult.SUCCESS, db.dao().bookRide(2, it, today, afternoon)) }
        assertEquals(BookResult.DRIVER_FULL, db.dao().bookRide(99, 5, today, afternoon))
    }

    @Test fun editedSeedIsAppliedAndOnlyAffectedBookingsAreRemoved() = runBlocking {
        val dao = db.dao()
        dao.bookRide(1, student, today, afternoon)   // unchanged ride
        dao.bookRide(2, student, today, afternoon)   // ride that will change
        dao.bookRide(5, student, today, afternoon)   // ride that will be removed

        val seed = JsonSeeder.read(context)
        val edited = seed.copy(
            locations = seed.locations.map { if (it.locationId == 6) it.copy(name = "Regent Mall (main doors)") else it },
            rides = seed.rides.filter { it.rideId != 5 }
                .map { if (it.rideId == 2) it.copy(departureTime = "18:15") else it },
            hash = "edited"
        )
        dao.applySeed(edited)

        assertEquals("edited", dao.seedHash())
        assertNull(dao.getRide(5))
        assertEquals("18:15", dao.getRide(2)?.departureTime)
        assertEquals("Regent Mall (main doors)", dao.getLocation(6)?.name)
        val kept = dao.activeBookings(student, afternoon).single()   // only the ride 1 booking is left
        assertEquals(5, kept.destinationId)

        db.syncSeed(context)   // the real assets differ from "edited", so they are loaded again
        assertEquals(seed.hash, dao.seedHash())
        assertNotNull(dao.getRide(5))
        assertEquals("18:00", dao.getRide(2)?.departureTime)
    }

    @Test fun everySeedLocationHasCoordinatesAndEveryPairHasARide() = runBlocking {
        val dao = db.dao()
        val pickups = dao.getPickups()
        val destinations = dao.getDestinations()
        assertEquals(setOf(1, 3, 4), pickups.map { it.locationId }.toSet())
        assertEquals(setOf(2, 5, 6), destinations.map { it.locationId }.toSet())
        (pickups + destinations).forEach { assertTrue(it.latitude != null && it.longitude != null) }
        pickups.forEach { p -> destinations.forEach { d ->
            assertTrue(dao.availableRides(p.locationId, d.locationId, today, afternoon).isNotEmpty())
        } }
    }

    @Test fun bookingSurvivesDatabaseReopen() = runBlocking {
        val name = "campuslift-test-${UUID.randomUUID()}.db"
        var persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            persistent.syncSeed(context)
            persistent.dao().bookRide(2, student, today, afternoon)
            persistent.close()
            persistent = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            persistent.syncSeed(context)
            assertEquals(1, persistent.dao().activeBookings(student, afternoon).size)
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
