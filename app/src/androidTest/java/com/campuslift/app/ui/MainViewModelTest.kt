package com.campuslift.app.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campuslift.app.R
import com.campuslift.app.data.BookResult
import com.campuslift.app.data.Location
import com.campuslift.app.data.RideListItem
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.routing.RouteError
import com.campuslift.app.routing.RouteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class MainViewModelTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val pickup = Location(4, "SUB", 45.945, -66.641)
    private val destination = Location(6, "Regent Mall", 45.935, -66.664)
    private val fixedTime = ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 15, 0))

    private fun card(date: String, rideId: Int = 2) = RideListItem(
        rideId = rideId,
        driverName = "Daniel Moore",
        carModel = "Van",
        pickup = pickup.name,
        destination = destination.name,
        departureTime = "18:00",
        serviceDate = date,
        seatsLeft = 4
    )

    @Test fun outOfOrderDateResponsesAreDroppedAndOldCardsClearedImmediately() = runBlocking(Dispatchers.Main) {
        val gateDate1 = CompletableDeferred<List<RideListItem>>()
        val gateDate2 = CompletableDeferred<List<RideListItem>>()
        val gateDate3 = CompletableDeferred<List<RideListItem>>()

        val gateway = object : RideGateway {
            override suspend fun syncAndLoadLocations() = listOf(pickup) to listOf(destination)
            override suspend fun availableRides(pickupId: Int, destinationId: Int, serviceDate: String, time: ScheduleTime) =
                when (serviceDate) {
                    "2026-11-16" -> gateDate1.await()
                    "2026-11-17" -> gateDate2.await()
                    else -> gateDate3.await()
                }
            override suspend fun bookRide(rideId: Int, studentId: Int, serviceDate: String, time: ScheduleTime) =
                BookResult.SUCCESS to 1
            override suspend fun route(pickup: Location, destination: Location) =
                RouteResult.Failure(RouteError.MISSING_KEY)
        }

        val vm = MainViewModel(app, gateway) { fixedTime }
        vm.search(pickup, destination)
        assertTrue(vm.state.value.searching)

        // Change dates twice while the initial search is still pending.
        vm.setDate("2026-11-17")
        assertTrue(vm.state.value.rides.isEmpty())
        vm.setDate("2026-11-18")
        assertTrue(vm.state.value.rides.isEmpty())

        // Complete the latest query first, then older queries out of order.
        val expected = listOf(card("2026-11-18", rideId = 99))
        gateDate3.complete(expected)
        gateDate1.complete(listOf(card("2026-11-16", rideId = 1)))
        gateDate2.complete(listOf(card("2026-11-17", rideId = 2)))

        // Drain main looper tasks.
        kotlinx.coroutines.yield()
        assertEquals("2026-11-18", vm.state.value.date)
        assertEquals(expected, vm.state.value.rides)
        assertFalse(vm.state.value.searching)
    }

    @Test fun staleCardCannotBookForWrongDate() = runBlocking(Dispatchers.Main) {
        var bookedCalled = false
        val gateway = object : RideGateway {
            override suspend fun syncAndLoadLocations() = listOf(pickup) to listOf(destination)
            override suspend fun availableRides(pickupId: Int, destinationId: Int, serviceDate: String, time: ScheduleTime) =
                listOf(card(serviceDate))
            override suspend fun bookRide(rideId: Int, studentId: Int, serviceDate: String, time: ScheduleTime): Pair<BookResult, Int?> {
                bookedCalled = true
                return BookResult.SUCCESS to 1
            }
            override suspend fun route(pickup: Location, destination: Location) =
                RouteResult.Failure(RouteError.MISSING_KEY)
        }

        val vm = MainViewModel(app, gateway) { fixedTime }
        vm.setDate("2026-11-17")
        vm.book(card("2026-11-16"))
        kotlinx.coroutines.yield()

        assertFalse(bookedCalled)
        assertEquals(R.string.stale_ride_error, vm.state.value.message)
    }

    @Test fun savedBookingStillOpensRideDetailsEvenIfPostBookRefreshThrows() = runBlocking(Dispatchers.Main) {
        var calls = 0
        val gateway = object : RideGateway {
            override suspend fun syncAndLoadLocations() = listOf(pickup) to listOf(destination)
            override suspend fun availableRides(pickupId: Int, destinationId: Int, serviceDate: String, time: ScheduleTime): List<RideListItem> {
                calls++
                if (calls > 1) throw IllegalStateException("refresh failed")
                return listOf(card(serviceDate))
            }
            override suspend fun bookRide(rideId: Int, studentId: Int, serviceDate: String, time: ScheduleTime) =
                BookResult.SUCCESS to 42
            override suspend fun route(pickup: Location, destination: Location) =
                RouteResult.Failure(RouteError.MISSING_KEY)
        }

        val vm = MainViewModel(app, gateway) { fixedTime }
        vm.search(pickup, destination)
        kotlinx.coroutines.yield()
        vm.book(card("2026-11-16"))
        kotlinx.coroutines.yield()

        assertEquals(42, vm.state.value.openBookingId)
        assertEquals(R.string.book_success, vm.state.value.message)
    }

    @Test fun searchAndBookingErrorsUseDistinctMessages() = runBlocking(Dispatchers.Main) {
        var failSearch = true
        val gateway = object : RideGateway {
            override suspend fun syncAndLoadLocations() = listOf(pickup) to listOf(destination)
            override suspend fun availableRides(pickupId: Int, destinationId: Int, serviceDate: String, time: ScheduleTime): List<RideListItem> {
                if (failSearch) throw IllegalStateException("search boom")
                return listOf(card(serviceDate))
            }
            override suspend fun bookRide(rideId: Int, studentId: Int, serviceDate: String, time: ScheduleTime): Pair<BookResult, Int?> {
                throw IllegalStateException("book boom")
            }
            override suspend fun route(pickup: Location, destination: Location) =
                RouteResult.Failure(RouteError.MISSING_KEY)
        }

        val vm = MainViewModel(app, gateway) { fixedTime }
        vm.search(pickup, destination)
        kotlinx.coroutines.yield()
        assertEquals(R.string.search_error, vm.state.value.message)
        vm.messageShown()

        failSearch = false
        vm.search(pickup, destination)
        kotlinx.coroutines.yield()
        vm.book(card("2026-11-16"))
        kotlinx.coroutines.yield()
        assertNull(vm.state.value.openBookingId)
        assertEquals(R.string.booking_error, vm.state.value.message)
    }
}
