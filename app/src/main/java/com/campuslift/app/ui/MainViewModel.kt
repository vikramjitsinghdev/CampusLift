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
import com.campuslift.app.routing.OpenRouteService
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
    val date: String = ScheduleTime.now().today,   // the day chosen in the calendar
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, failed = false) }
            try {
                db.syncSeed(getApplication())
                val pickups = db.dao().getPickups()
                val destinations = db.dao().getDestinations()
                mutableState.update { it.copy(pickups = pickups, destinations = destinations, loading = false) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    /** Called when the student picks a day in the calendar. Refreshes the times if a trip is shown. */
    fun setDate(date: String) {
        if (mutableState.value.booking || date == mutableState.value.date) return
        mutableState.update { it.copy(date = date) }
        refresh()
    }

    fun search(pickup: Location, destination: Location) {
        if (mutableState.value.booking) return
        searchJob?.cancel()
        if (pickup.locationId == destination.locationId) {
            mutableState.update { it.copy(rides = emptyList(), route = null, searched = false,
                searching = false, routing = false, message = R.string.same_location) }
            return
        }
        searchJob = viewModelScope.launch {
            mutableState.update { it.copy(searching = true, searched = true, route = null,
                rides = emptyList(), routing = false, pickupId = pickup.locationId, destinationId = destination.locationId) }
            try {
                val rides = availableRides(pickup.locationId, destination.locationId)
                mutableState.update { it.copy(rides = rides, searching = false, routing = true) }
                val result = OpenRouteService().route(pickup, destination)
                mutableState.update { it.copy(route = result, routing = false) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(searching = false, routing = false, message = R.string.operation_error) }
            }
        }
    }

    fun book(ride: RideListItem) {
        if (mutableState.value.booking) return
        mutableState.update { it.copy(booking = true) }
        viewModelScope.launch {
            try {
                val dao = db.dao()
                val result = dao.bookRide(ride.rideId, DemoStudent.ID, ride.serviceDate, ScheduleTime.now())
                val message = when (result) {
                    BookResult.SUCCESS -> R.string.book_success
                    BookResult.ALREADY_BOOKED -> R.string.already_booked
                    BookResult.RIDE_FULL -> R.string.ride_full
                    BookResult.DRIVER_FULL -> R.string.driver_full
                    BookResult.DEPARTED -> R.string.ride_departed
                    BookResult.NOT_FOUND -> R.string.ride_missing
                }
                // After a successful booking the screen opens the Ride Details page for it.
                val bookingId = if (result == BookResult.SUCCESS) {
                    dao.confirmedBookingId(ride.rideId, DemoStudent.ID, ride.serviceDate)
                } else null
                refreshRides()
                mutableState.update { it.copy(message = message, openBookingId = bookingId) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(message = R.string.operation_error) }
            } finally {
                mutableState.update { it.copy(booking = false) }
            }
        }
    }

    fun refresh() {
        if (!mutableState.value.searched || mutableState.value.searching) return
        viewModelScope.launch {
            try { refreshRides()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutableState.update { it.copy(message = R.string.database_error) } }
        }
    }

    private suspend fun refreshRides() {
        val current = mutableState.value
        val pickup = current.pickupId ?: return
        val destination = current.destinationId ?: return
        val rides = availableRides(pickup, destination)
        mutableState.update {
            if (it.pickupId == pickup && it.destinationId == destination) it.copy(rides = rides) else it
        }
    }

    /** Times still available on the chosen date. A date that has passed is moved to today. */
    private suspend fun availableRides(pickupId: Int, destinationId: Int): List<RideListItem> {
        val time = ScheduleTime.now()
        if (mutableState.value.date < time.today) mutableState.update { it.copy(date = time.today) }
        return db.dao().availableRides(pickupId, destinationId, mutableState.value.date, time)
    }

    fun messageShown() { mutableState.update { it.copy(message = null) } }
    fun bookingOpened() { mutableState.update { it.copy(openBookingId = null) } }
}
