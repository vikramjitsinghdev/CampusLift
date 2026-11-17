package com.campuslift.app

import android.content.Intent
import android.graphics.Color
import com.campuslift.app.ui.applySystemBars
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.campuslift.app.databinding.ActivityMainBinding
import com.campuslift.app.routing.DrivingRoute
import com.campuslift.app.routing.RouteError
import com.campuslift.app.routing.RouteResult
import com.campuslift.app.ui.HomeState
import com.campuslift.app.ui.MainViewModel
import com.campuslift.app.routing.RoutePreviews
import com.campuslift.app.ui.RideAdapter
import com.campuslift.app.ui.dayText
import com.campuslift.app.ui.departureText
import com.campuslift.app.data.RideListItem
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.data.ServiceShift
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.ceil

private const val DATE_PICKER = "date_picker"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val model: MainViewModel by viewModels()
    private val rides = RideAdapter { review(it) }
    private var drawnRoute: DrivingRoute? = null
    private var timeMenu: ListPopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A calendar restored after rotation has lost its listener, so close it.
        (supportFragmentManager.findFragmentByTag(DATE_PICKER) as? MaterialDatePicker<*>)?.dismiss()
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            osmdroidBasePath = File(cacheDir, "maps")
            osmdroidTileCache = File(cacheDir, "maps/tiles")
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        binding.toolbar.setTitle(R.string.app_name)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.rides.layoutManager = LinearLayoutManager(this)
        binding.rides.adapter = rides
        val tripListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val state = model.state.value
                model.selectTrip(state.pickups.getOrNull(binding.pickup.selectedItemPosition - 1),
                    state.destinations.getOrNull(binding.destination.selectedItemPosition - 1))
            }
        }
        binding.pickup.onItemSelectedListener = tripListener
        binding.destination.onItemSelectedListener = tripListener
        binding.findRide.setOnClickListener {
            val state = model.state.value
            val pickup = state.pickups.getOrNull(binding.pickup.selectedItemPosition - 1)
            val destination = state.destinations.getOrNull(binding.destination.selectedItemPosition - 1)
            if (pickup != null && destination != null && state.slot != null &&
                pickup.locationId == state.pickupId && destination.locationId == state.destinationId) model.search(pickup, destination)
            else Toast.makeText(this, R.string.select_locations, Toast.LENGTH_SHORT).show()
        }
        binding.date.setOnClickListener { pickDate() }
        binding.timeSlot.setOnClickListener { showTimeSlots() }
        binding.pickupMap.setOnClickListener { showPickupMap() }
        binding.safetyHelp.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.safety_help)
                .setMessage(R.string.safety_help_text).setPositiveButton(android.R.string.ok, null).show()
        }
        binding.myRides.setOnClickListener { startActivity(Intent(this, MyRidesActivity::class.java)) }
        binding.retry.setOnClickListener { model.load() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render(it) } }
        }
    }

    private fun render(state: HomeState) {
        if (binding.pickup.adapter == null && state.pickups.isNotEmpty()) {
            binding.pickup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.select_pickup)) + state.pickups.map { it.name })
            binding.destination.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.select_destination)) + state.destinations.map { it.name })
            state.pickupId?.let { id -> binding.pickup.setSelection(state.pickups.indexOfFirst { it.locationId == id }.plus(1).coerceAtLeast(0)) }
            state.destinationId?.let { id -> binding.destination.setSelection(state.destinations.indexOfFirst { it.locationId == id }.plus(1).coerceAtLeast(0)) }
        }
        binding.progress.visibility = if (state.loading || state.searching || state.booking || state.routing) View.VISIBLE else View.GONE
        val bookable = state.rides.filter { it.seatsLeft > 0 }
        val currentSlotAvailable = state.slot != null && bookable.any { ServiceShift.slotOf(it.departureTime) == state.slot }
        binding.findRide.isEnabled = !state.searching && !state.booking && currentSlotAvailable
        binding.pickupMap.isEnabled = state.pickups.isNotEmpty() && !state.booking
        binding.timeSlot.text = state.slot?.let { getString(R.string.time_slot_dropdown, slotLabel(it)) }
            ?: getString(R.string.choose_time_slot)
        val hasTrip = state.pickupId != null && state.destinationId != null && state.pickupId != state.destinationId
        binding.timeSlot.isEnabled = hasTrip && !state.searching && !state.booking
        if (!binding.timeSlot.isEnabled) timeMenu?.dismiss()
        binding.pickup.isEnabled = !state.loading && !state.booking
        binding.destination.isEnabled = !state.loading && !state.booking
        binding.retry.visibility = if (state.failed) View.VISIBLE else View.GONE
        binding.date.text = getString(R.string.date_button, dayText(state.date))
        binding.date.isEnabled = !state.loading && !state.booking
        binding.results.text = getString(R.string.results_for, dayText(state.date))
        binding.results.visibility = if (state.searched) View.VISIBLE else View.GONE
        val results = if (state.searched) bookable.filter { ServiceShift.slotOf(it.departureTime) == state.slot } else emptyList()
        binding.status.setText(when {
            state.loading -> R.string.loading
            state.failed -> R.string.database_error
            state.searching -> R.string.searching
            hasTrip && bookable.isEmpty() -> R.string.no_slot_available
            state.searched && results.isEmpty() -> R.string.no_rides
            else -> R.string.ready
        })
        rides.submit(results, state.booking || state.searching)
        val route = (state.route as? RouteResult.Success)?.route
        binding.routeInfo.text = when {
            state.routing -> getString(R.string.route_loading)
            route != null -> getString(R.string.route_summary,
                state.pickups.firstOrNull { it.locationId == state.pickupId }?.name.orEmpty(),
                state.destinations.firstOrNull { it.locationId == state.destinationId }?.name.orEmpty(),
                route.distanceMeters / 1000, ceil(route.durationSeconds / 60).toInt())
            state.route is RouteResult.Failure -> getString(when (state.route.error) {
                RouteError.MISSING_KEY -> R.string.key_missing
                RouteError.MISSING_COORDINATES -> R.string.coordinates_missing
                RouteError.NETWORK -> R.string.network_error
                RouteError.SERVICE -> R.string.route_error
                RouteError.NO_ROUTE -> R.string.no_route
                RouteError.INVALID_RESPONSE -> R.string.invalid_route
            })
            else -> ""
        }
        val preview = if (route == null && !state.routing && state.route != null)
            RoutePreviews.image(state.pickupId, state.destinationId) else null
        binding.routePreview.visibility = if (preview != null) View.VISIBLE else View.GONE
        preview?.let { binding.routePreview.setImageResource(it) }
        if (state.route is RouteResult.Failure) {
            binding.routeInfo.append("\n" + getString(if (preview != null) R.string.preview_fallback else R.string.preview_unavailable))
        }
        binding.map.visibility = if (route != null) View.VISIBLE else View.GONE
        binding.attribution.visibility = binding.map.visibility
        if (route != drawnRoute) {
            binding.map.overlays.clear()
            drawnRoute = route
            if (route != null) drawRoute(route, state)
            binding.map.invalidate()
        }
        state.message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); model.messageShown() }
        // A ride was just booked: show it on its own Ride Details screen.
        state.openBookingId?.let { startActivity(RideDetailsActivity.intent(this, it)); model.bookingOpened() }
    }

    private fun slotLabel(slot: Int): String {
        val range = ServiceShift.range(slot)
        return if (ServiceShift.start(slot).hour < 2) getString(R.string.next_day_slot, range) else range
    }

    private fun showTimeSlots() {
        val now = ScheduleTime.now()
        val enabled = model.state.value.rides.filter { it.seatsLeft > 0 && !now.hasDeparted(it.serviceDate, it.departureTime) }
            .mapNotNull { ServiceShift.slotOf(it.departureTime) }.toSet()
        val labels = (0 until ServiceShift.SLOT_COUNT).map {
            if (it in enabled) slotLabel(it) else getString(R.string.time_slot_unavailable, slotLabel(it))
        }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun areAllItemsEnabled() = false
            override fun isEnabled(position: Int) = position in enabled
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(if (position in enabled) Color.BLACK else getColor(android.R.color.darker_gray))
                    isEnabled = position in enabled
                }
        }
        timeMenu = ListPopupWindow(this).apply {
            anchorView = binding.timeSlot
            width = binding.timeSlot.width
            isModal = true
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                if (position in enabled) model.selectSlot(position)
                dismiss()
            }
            show()
        }
    }

    private fun review(ride: RideListItem) {
        val vehicle = ride.plate?.let { getString(R.string.vehicle_with_plate, ride.carModel, it) } ?: ride.carModel
        MaterialAlertDialogBuilder(this).setTitle(R.string.review_ride)
            .setMessage(getString(R.string.booking_review, ride.pickup, ride.destination,
                departureText(ride.serviceDate, ride.departureTime), ride.driverName, vehicle))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.confirm_booking) { _, _ -> model.book(ride) }
            .show()
    }

    private fun showPickupMap() {
        val map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            contentDescription = getString(R.string.pickup_map)
        }
        val name = TextView(this).apply {
            setText(R.string.pickup_map_hint)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (260 * resources.displayMetrics.density).toInt()))
            addView(TextView(context).apply { setText(R.string.map_attribution) })
        }
        var pickupIndex = 0
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.pickup_map)
            .setPositiveButton(R.string.use_pickup) { _, _ -> binding.pickup.setSelection(pickupIndex) }
            .setView(content).setNegativeButton(android.R.string.cancel, null).create()
        val points = model.state.value.pickups.mapIndexedNotNull { i, location ->
            val lat = location.latitude ?: return@mapIndexedNotNull null
            val lon = location.longitude ?: return@mapIndexedNotNull null
            val point = GeoPoint(lat, lon)
            map.overlays.add(Marker(map).apply {
                position = point
                title = location.name
                setOnMarkerClickListener { marker, _ ->
                    pickupIndex = i + 1
                    name.text = location.name
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    marker.showInfoWindow()
                    true
                }
            })
            point
        }
        dialog.setOnDismissListener { map.onPause(); map.onDetach() }
        dialog.show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
        map.onResume()
        if (points.isNotEmpty()) map.post {
            if (dialog.isShowing) map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 36)
        }
    }

    /** Opens the calendar. Only today and the next 30 days can be chosen. */
    private fun pickDate() {
        if (supportFragmentManager.findFragmentByTag(DATE_PICKER) != null) return
        fun millis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val today = LocalDate.parse(ServiceShift.currentDate(ScheduleTime.now()))
        val last = today.plusDays(ScheduleTime.BOOKING_DAYS_AHEAD)
        val limits = CalendarConstraints.Builder()
            .setStart(millis(today))
            .setEnd(millis(last))
            .setValidator(CompositeDateValidator.allOf(listOf<CalendarConstraints.DateValidator>(
                DateValidatorPointForward.from(millis(today)),
                DateValidatorPointBackward.before(millis(last)))))
            .build()
        val chosen = runCatching { LocalDate.parse(model.state.value.date) }.getOrDefault(today)
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_date)
            .setSelection(millis(if (chosen.isBefore(today)) today else chosen))
            .setCalendarConstraints(limits)
            .build()
        picker.addOnPositiveButtonClickListener { picked ->
            model.setDate(Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate().toString())
        }
        picker.show(supportFragmentManager, DATE_PICKER)
    }

    private fun drawRoute(route: DrivingRoute, state: HomeState) {
        val points = route.points.map { GeoPoint(it.latitude, it.longitude) }
        binding.map.overlays.add(Polyline().apply {
            setPoints(points)
            outlinePaint.color = getColor(R.color.route_blue)
            outlinePaint.strokeWidth = 9f
        })
        val pickup = state.pickups.first { it.locationId == state.pickupId }
        val destination = state.destinations.first { it.locationId == state.destinationId }
        listOf(pickup, destination).forEach { location ->
            binding.map.overlays.add(Marker(binding.map).apply {
                position = GeoPoint(requireNotNull(location.latitude), requireNotNull(location.longitude))
                title = location.name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
        binding.map.post {
            if (drawnRoute == route) binding.map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 40)
        }
    }

    override fun onResume() { super.onResume(); binding.map.onResume(); model.refresh() }
    override fun onPause() { timeMenu?.dismiss(); binding.map.onPause(); super.onPause() }
    override fun onDestroy() { binding.map.onDetach(); super.onDestroy() }
}
