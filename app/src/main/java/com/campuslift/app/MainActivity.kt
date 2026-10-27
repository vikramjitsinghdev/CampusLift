package com.campuslift.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.campuslift.app.ui.MyRidesActivity
import com.campuslift.app.ui.RideAdapter
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val model: MainViewModel by viewModels()
    private val rides = RideAdapter { model.book(it) }
    private var drawnRoute: DrivingRoute? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            osmdroidBasePath = File(cacheDir, "maps")
            osmdroidTileCache = File(cacheDir, "maps/tiles")
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setTitle(R.string.app_name)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.rides.layoutManager = LinearLayoutManager(this)
        binding.rides.adapter = rides
        binding.findRide.setOnClickListener {
            val state = model.state.value
            val pickup = state.pickups.getOrNull(binding.pickup.selectedItemPosition)
            val destination = state.destinations.getOrNull(binding.destination.selectedItemPosition)
            if (pickup != null && destination != null) model.search(pickup, destination)
        }
        binding.myRides.setOnClickListener { startActivity(Intent(this, MyRidesActivity::class.java)) }
        binding.retry.setOnClickListener { model.load() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render(it) } }
        }
    }

    private fun render(state: HomeState) {
        if (binding.pickup.adapter == null && state.pickups.isNotEmpty()) {
            binding.pickup.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, state.pickups.map { it.name })
            binding.destination.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, state.destinations.map { it.name })
            state.pickupId?.let { id -> binding.pickup.setSelection(state.pickups.indexOfFirst { it.locationId == id }.coerceAtLeast(0)) }
            state.destinationId?.let { id -> binding.destination.setSelection(state.destinations.indexOfFirst { it.locationId == id }.coerceAtLeast(0)) }
        }
        binding.progress.visibility = if (state.loading || state.searching || state.booking || state.routing) View.VISIBLE else View.GONE
        binding.findRide.isEnabled = !state.loading && !state.failed && !state.searching && !state.booking && state.pickups.isNotEmpty()
        binding.pickup.isEnabled = !state.loading && !state.booking
        binding.destination.isEnabled = !state.loading && !state.booking
        binding.retry.visibility = if (state.failed) View.VISIBLE else View.GONE
        binding.status.setText(when {
            state.loading -> R.string.loading
            state.failed -> R.string.database_error
            state.searching -> R.string.searching
            state.searched && state.rides.isEmpty() -> R.string.no_rides
            else -> R.string.ready
        })
        rides.submit(state.rides, state.booking)
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
        binding.map.visibility = if (route != null) View.VISIBLE else View.GONE
        binding.attribution.visibility = binding.map.visibility
        if (route != drawnRoute) {
            binding.map.overlays.clear()
            drawnRoute = route
            if (route != null) drawRoute(route, state)
            binding.map.invalidate()
        }
        state.message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); model.messageShown() }
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
    override fun onPause() { binding.map.onPause(); super.onPause() }
    override fun onDestroy() { binding.map.onDetach(); super.onDestroy() }
}
