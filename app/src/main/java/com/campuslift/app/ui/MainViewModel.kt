package com.campuslift.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.campuslift.app.R
import com.campuslift.app.data.AppDatabase
import com.campuslift.app.data.BookResult
import com.campuslift.app.data.DemoStudent
import com.campuslift.app.data.Location
import com.campuslift.app.data.RideListItem
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.data.ServiceShift
import com.campuslift.app.routing.OpenRouteService
import com.campuslift.app.routing.RouteError
import com.campuslift.app.routing.RouteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val pickups: List<Location> = emptyList(),
    val destinations: List<Location> = emptyList(),
    val rides: List<RideListItem> = emptyList(),
    val date: String = ServiceShift.currentDate(ScheduleTime.now()), // the date the shift starts
    val slot: Int? = null,
    val loading: Boolean = true,
    val searching: Boolean = false,
    val booking: Boolean = false,
    val failed: Boolean = false,
    val searched: Boolean = false,
    val pickupId: Int? = null,
    val destinationId: Int? = null,
    val route: RouteResult? = null,
    val routing: Boolean = false,
    val message: Int? = null,
    val openBookingId: Int? = null                 // set after booking, so the screen can open Ride Details
)

internal interface RideGateway {
    suspend fun syncAndLoadLocations(): Pair<List<Location>, List<Location>>
    suspend fun availableRides(pickupId: Int, destinationId: Int, serviceDate: String, time: ScheduleTime): List<RideListItem>
    suspend fun bookRide(rideId: Int, studentId: Int, serviceDate: String, time: ScheduleTime): Pair<BookResult, Int?>
    suspend fun route(pickup: Location, destination: Location): RouteResult
}

private class RoomRideGateway(private val application: Application) : RideGateway {
    private val db = AppDatabase.getInstance(application)

    override suspend fun syncAndLoadLocations(): Pair<List<Location>, List<Location>> {
        db.syncSeed(application)
        val dao = db.dao()
        return dao.getPickups() to dao.getDestinations()
    }

    override suspend fun availableRides(
        pickupId: Int,
        destinationId: Int,
        serviceDate: String,
        time: ScheduleTime
    ): List<RideListItem> = db.dao().shiftRides(pickupId, destinationId, serviceDate, time)

    override suspend fun bookRide(
        rideId: Int,
        studentId: Int,
        serviceDate: String,
        time: ScheduleTime
    ): Pair<BookResult, Int?> {
        val dao = db.dao()
        val result = dao.bookRide(rideId, studentId, serviceDate, time)
        val bookingId = if (result == BookResult.SUCCESS) {
            dao.confirmedBookingId(rideId, studentId, serviceDate)
        } else null
        return result to bookingId
    }

    override suspend fun route(pickup: Location, destination: Location): RouteResult =
        OpenRouteService().route(pickup, destination)
}

class MainViewModel internal constructor(
    application: Application,
    private val gateway: RideGateway,
    private val now: () -> ScheduleTime = { ScheduleTime.now() }
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, RoomRideGateway(application))

    private val mutableState = MutableStateFlow(HomeState(date = ServiceShift.currentDate(now())))
    val state = mutableState.asStateFlow()
    private var ridesJob: Job? = null
    private var routeJob: Job? = null
    private var ridesRequestSeq = 0L
    private var routeRequestSeq = 0L

    init { load() }

    fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, failed = false) }
            try {
                val (pickups, destinations) = gateway.syncAndLoadLocations()
                mutableState.update { it.copy(pickups = pickups, destinations = destinations, loading = false) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** Clear the slot immediately so an old result cannot be booked for the new date. */
    fun setDate(date: String) {
        val current = mutableState.value
        if (current.booking || date == current.date) return
        mutableState.update {
            it.copy(date = date, slot = null, rides = emptyList(),
                searching = it.pickupId != null && it.destinationId != null && it.pickupId != it.destinationId)
        }
        fetchRides()
    }

    fun selectTrip(pickup: Location?, destination: Location?) {
        val current = mutableState.value
        if (current.booking || (current.pickupId == pickup?.locationId && current.destinationId == destination?.locationId)) return
        ridesJob?.cancel()
        routeJob?.cancel()
        ridesRequestSeq++
        routeRequestSeq++
        val valid = pickup != null && destination != null && pickup.locationId != destination.locationId
        mutableState.update {
            it.copy(
                searching = valid, searched = false, route = null, slot = null,
                rides = emptyList(), routing = false,
                pickupId = pickup?.locationId, destinationId = destination?.locationId
            )
        }
        if (valid) fetchRides()
    }

    fun selectSlot(slot: Int) {
        val current = mutableState.value
        val time = now()
        val available = current.rides.any {
            it.seatsLeft > 0 && ServiceShift.slotOf(it.departureTime) == slot &&
                !time.hasDeparted(it.serviceDate, it.departureTime)
        }
        if (!current.searching && !current.booking && available) {
            mutableState.update { it.copy(slot = slot, searched = false) }
        }
    }

    fun search(pickup: Location, destination: Location) {
        val current = mutableState.value
        if (current.booking || pickup.locationId == destination.locationId) return
        val sameTrip = current.pickupId == pickup.locationId && current.destinationId == destination.locationId
        selectTrip(pickup, destination)
        mutableState.update { it.copy(searched = true) }
        if (sameTrip && !current.searching) fetchRides()
        fetchRoute(pickup, destination)
    }

    fun book(ride: RideListItem) {
        val current = mutableState.value
        if (current.booking) return
        if (ServiceShift.dateOf(ride.serviceDate, ride.departureTime) != current.date || current.searching ||
            ride !in current.rides || (current.slot != null && ServiceShift.slotOf(ride.departureTime) != current.slot)) {
            mutableState.update { it.copy(rides = emptyList(), message = R.string.stale_ride_error) }
            if (current.searched) fetchRides()
            return
        }
        mutableState.update { it.copy(booking = true) }
        viewModelScope.launch {
            try {
                val (result, bookingId) = gateway.bookRide(ride.rideId, DemoStudent.ID, ride.serviceDate, now())
                val message = when (result) {
                    BookResult.SUCCESS -> R.string.book_success
                    BookResult.ALREADY_BOOKED -> R.string.already_booked
                    BookResult.RIDE_FULL -> R.string.ride_full
                    BookResult.DRIVER_FULL -> R.string.driver_full
                    BookResult.DEPARTED -> R.string.ride_departed
                    BookResult.NOT_FOUND -> R.string.ride_missing
                }
                mutableState.update { it.copy(message = message, openBookingId = bookingId) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(message = R.string.booking_error) }
                return@launch
            } finally {
                mutableState.update { it.copy(booking = false) }
            }
            refreshAfterBooking(current.date)
        }
    }

    fun refresh() {
        val current = mutableState.value
        if (current.pickupId == null || current.destinationId == null || current.booking) return
        val today = ServiceShift.currentDate(now())
        if (current.date < today) {
            mutableState.update { it.copy(date = today, slot = null, rides = emptyList()) }
        }
        fetchRides()
    }

    private fun fetchRides() {
        val current = mutableState.value
        val pickupId = current.pickupId ?: return
        val destinationId = current.destinationId ?: return
        if (pickupId == destinationId) return
        val date = current.date
        val seq = ++ridesRequestSeq
        ridesJob?.cancel()
        mutableState.update { it.copy(searching = true) }
        ridesJob = viewModelScope.launch {
            try {
                val rides = gateway.availableRides(pickupId, destinationId, date, now())
                mutableState.update { s ->
                    if (s.pickupId == pickupId && s.destinationId == destinationId &&
                        s.date == date && ridesRequestSeq == seq
                    ) {
                        s.copy(rides = rides, searching = false)
                    } else s
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { s ->
                    if (s.pickupId == pickupId && s.destinationId == destinationId &&
                        s.date == date && ridesRequestSeq == seq
                    ) {
                        s.copy(searching = false, message = R.string.search_error)
                    } else s
                }
            }
        }
    }

    private suspend fun refreshAfterBooking(serviceDate: String) {
        val current = mutableState.value
        val pickupId = current.pickupId ?: return
        val destinationId = current.destinationId ?: return
        if (current.date != serviceDate) return
        val seq = ++ridesRequestSeq
        try {
            val rides = gateway.availableRides(pickupId, destinationId, serviceDate, now())
            mutableState.update { s ->
                if (s.pickupId == pickupId && s.destinationId == destinationId &&
                    s.date == serviceDate && ridesRequestSeq == seq
                ) {
                    s.copy(rides = rides)
                } else s
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Leave the booking outcome message intact; onResume() will refresh seats later.
        }
    }

    private fun fetchRoute(pickup: Location, destination: Location) {
        val seq = ++routeRequestSeq
        routeJob?.cancel()
        mutableState.update { it.copy(routing = true, route = null) }
        routeJob = viewModelScope.launch {
            try {
                val result = gateway.route(pickup, destination)
                mutableState.update { s ->
                    if (s.pickupId == pickup.locationId && s.destinationId == destination.locationId &&
                        routeRequestSeq == seq
                    ) {
                        s.copy(route = result, routing = false)
                    } else s
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { s ->
                    if (s.pickupId == pickup.locationId && s.destinationId == destination.locationId &&
                        routeRequestSeq == seq
                    ) {
                        s.copy(route = RouteResult.Failure(RouteError.SERVICE), routing = false)
                    } else s
                }
            }
        }
    }

    fun messageShown() { mutableState.update { it.copy(message = null) } }
    fun bookingOpened() { mutableState.update { it.copy(openBookingId = null) } }
}
