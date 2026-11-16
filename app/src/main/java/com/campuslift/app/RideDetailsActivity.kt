package com.campuslift.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.campuslift.app.data.AppDatabase
import com.campuslift.app.data.DemoStudent
import com.campuslift.app.data.MyBookingItem
import com.campuslift.app.data.RideState
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.databinding.ActivityRideDetailsBinding
import com.campuslift.app.routing.OpenRouteService
import com.campuslift.app.routing.RoutePreviews
import com.campuslift.app.routing.RouteResult
import com.campuslift.app.ui.applySystemBars
import com.campuslift.app.ui.DemoRide
import com.campuslift.app.ui.departureText
import com.campuslift.app.ui.stateText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.ceil

/**
 * One booked ride on its own screen: when it leaves, who drives, and the route.
 * Opens right after booking, and from any ride on the My Rides screen.
 */
class RideDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRideDetailsBinding
    private val db by lazy { AppDatabase.getInstance(this) }
    private val bookingId by lazy { intent.getIntExtra(EXTRA_BOOKING_ID, -1) }
    private var busy = false
    private var routeJob: Job? = null
    private var booking: MyBookingItem? = null
    private var routePoints = emptyList<GeoPoint>()
    private var demoPoints = emptyList<GeoPoint>()
    private var vehicle: Marker? = null
    private val camera = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::verifyCode)
    }
    private val image = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val code = withContext(Dispatchers.IO) { readQr(uri) }
            if (code == null) badCode() else verifyCode(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            osmdroidBasePath = File(cacheDir, "maps")
            osmdroidTileCache = File(cacheDir, "maps/tiles")
        }
        binding = ActivityRideDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        binding.toolbar.setTitle(R.string.ride_details_title)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationContentDescription(R.string.back)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.backHome.setOnClickListener { open(MainActivity::class.java) }
        binding.myRides.setOnClickListener { open(MyRidesActivity::class.java) }
        binding.retry.setOnClickListener { load() }
        binding.cancelRide.setOnClickListener { cancel() }
        binding.showCode.setOnClickListener { showCode() }
        binding.scanCode.setOnClickListener { chooseScanner() }
        binding.endRide.setOnClickListener { advanceDemo(2) }
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateTracking()
                    delay(500)
                }
            }
        }
    }

    // Reloading on every return keeps the status right (a ride can become completed meanwhile).
    override fun onStart() { super.onStart(); load() }

    /** Goes to a screen that may already be open underneath, instead of stacking a second copy. */
    private fun open(screen: Class<*>) {
        startActivity(Intent(this, screen).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun load() {
        if (busy) return
        setBusy(true)
        binding.retry.visibility = View.GONE
        lifecycleScope.launch {
            try {
                db.syncSeed(applicationContext)
                show(db.dao().getBooking(bookingId, DemoStudent.ID))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                binding.status.setText(R.string.database_error)
                binding.retry.visibility = View.VISIBLE
            } finally { setBusy(false) }
        }
    }

    private fun show(item: MyBookingItem?) {
        booking = item
        binding.rideCard.visibility = if (item == null) View.GONE else View.VISIBLE
        if (item == null) {
            binding.status.setText(R.string.booking_missing)
            binding.departure.text = ""
            return
        }
        val state = ScheduleTime.now().stateOf(item)
        binding.status.setText(when (state) {
            RideState.ACTIVE -> when (item.demoStage) {
                1 -> R.string.demo_passenger_checked
                2 -> R.string.demo_in_progress
                else -> R.string.confirmed
            }
            RideState.COMPLETED -> R.string.completed_ride
            RideState.CANCELLED -> R.string.cancelled_ride
        })
        binding.departure.text = departureText(item.serviceDate, item.departureTime)
        binding.activePickup.text = item.pickup
        binding.activeDestination.text = item.destination
        val car = item.plate?.let { getString(R.string.vehicle_with_plate, item.carModel, it) } ?: item.carModel
        binding.details.text = getString(R.string.booking_details, item.driverName, car,
            item.phone, stateText(state))
        val canCheckIn = state == RideState.ACTIVE && item.demoStage < 2
        binding.cancelRide.visibility = if (canCheckIn) View.VISIBLE else View.GONE
        binding.showCode.visibility = binding.cancelRide.visibility
        binding.scanCode.visibility = binding.cancelRide.visibility
        binding.showCode.setText(if (item.demoStage == 0) R.string.demo_student_code else R.string.demo_driver_code)
        binding.scanCode.setText(if (item.demoStage == 0) R.string.demo_scan_student else R.string.demo_scan_driver)
        if (routeJob == null) showRoute(item)   // ask for the route once, not on every return
        updateTracking()
    }

    private fun showCode() {
        val stage = booking?.demoStage ?: return
        val code = QRCodeWriter().encode(DemoRide.code(bookingId, stage, System.currentTimeMillis()), BarcodeFormat.QR_CODE, 384, 384)
        val pixels = IntArray(code.width * code.height) { i ->
            if (code[i % code.width, i / code.width]) Color.BLACK else Color.WHITE
        }
        val qr = ImageView(this).apply {
            contentDescription = getString(R.string.demo_qr_image)
            adjustViewBounds = true
            maxHeight = (260 * resources.displayMetrics.density).toInt()
            setImageBitmap(Bitmap.createBitmap(pixels, code.width, code.height, Bitmap.Config.ARGB_8888))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(binding.showCode.text)
            .setMessage(R.string.demo_qr_help)
            .setView(qr)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun chooseScanner() {
        MaterialAlertDialogBuilder(this)
            .setTitle(binding.scanCode.text)
            .setItems(arrayOf(getString(R.string.demo_camera), getString(R.string.demo_image))) { _, choice ->
                if (choice == 0) camera.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt(binding.scanCode.text.toString())
                }) else image.launch("image/*")
            }
            .show()
    }

    private fun readQr(uri: Uri): String? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val largest = maxOf(options.outWidth, options.outHeight)
        options.inSampleSize = (largest / 1600).coerceAtLeast(1)
        options.inJustDecodeBounds = false
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        bitmap?.let {
            val pixels = IntArray(it.width * it.height)
            it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
            val source = RGBLuminanceSource(it.width, it.height, pixels)
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        }
    } catch (_: Exception) { null }

    private fun verifyCode(code: String) {
        lifecycleScope.launch {
            try {
                val item = db.dao().getBooking(bookingId, DemoStudent.ID)
                if (item != null && ScheduleTime.now().stateOf(item) == RideState.ACTIVE &&
                    DemoRide.accepts(code, bookingId, item.demoStage, System.currentTimeMillis())) {
                    advanceDemo(item.demoStage)
                } else badCode()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                Toast.makeText(this@RideDetailsActivity, R.string.operation_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun badCode() =
        Toast.makeText(this, R.string.demo_bad_qr, Toast.LENGTH_LONG).show()

    private fun advanceDemo(stage: Int) {
        lifecycleScope.launch {
            try {
                val dao = db.dao()
                if (dao.advanceDemo(bookingId, DemoStudent.ID, stage, System.currentTimeMillis()) > 0) {
                    show(dao.getBooking(bookingId, DemoStudent.ID))
                } else if (stage < 2) badCode()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                Toast.makeText(this@RideDetailsActivity, R.string.operation_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateTracking() {
        val item = booking
        val started = item?.demoStartedAt?.takeIf { item.demoStage == 2 }
        val visibility = if (started != null) View.VISIBLE else View.GONE
        binding.demoTracking.visibility = visibility
        binding.demoProgress.visibility = visibility
        binding.endRide.visibility = visibility
        if (started == null) {
            vehicle?.let {
                binding.map.overlays.remove(it)
                binding.map.invalidate()
                if (routePoints.isEmpty() && item != null) fallback(item)
            }
            vehicle = null
            return
        }
        val progress = DemoRide.progress(started, System.currentTimeMillis())
        val percent = (progress * 100).toInt()
        binding.demoProgress.progress = percent
        binding.endRide.isEnabled = progress == 1.0
        binding.demoTracking.text = if (progress == 1.0) getString(R.string.demo_arrived)
            else getString(R.string.demo_tracking, percent)
        val points = routePoints.ifEmpty { demoPoints }
        if (points.size < 2) return
        val index = progress * points.lastIndex
        val start = index.toInt().coerceAtMost(points.lastIndex - 1)
        val a = points[start]
        val b = points[start + 1]
        val part = index - start
        if (vehicle == null) {
            if (routePoints.isEmpty()) {
                binding.routeInfo.setText(R.string.demo_direct_path)
                binding.routePreview.visibility = View.GONE
                binding.map.visibility = View.VISIBLE
                binding.attribution.visibility = View.VISIBLE
                binding.map.post { binding.map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 32) }
            }
            vehicle = Marker(binding.map).apply {
                title = getString(R.string.demo_vehicle)
                setIcon(getDrawable(android.R.drawable.ic_menu_directions))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                binding.map.overlays.add(this)
            }
        }
        vehicle?.position = GeoPoint(a.latitude + (b.latitude - a.latitude) * part,
            a.longitude + (b.longitude - a.longitude) * part)
        binding.map.invalidate()
    }

    /** Live map when OpenRouteService answers, otherwise the supplied screenshot for this trip. */
    private fun showRoute(item: MyBookingItem) {
        binding.routeInfo.setText(R.string.route_loading)
        routeJob = lifecycleScope.launch {
            try {
                val pickup = db.dao().getLocation(item.pickupId)
                val destination = db.dao().getLocation(item.destinationId)
                demoPoints = listOfNotNull(pickup, destination).mapNotNull { location ->
                    val lat = location.latitude
                    val lon = location.longitude
                    if (lat != null && lon != null) GeoPoint(lat, lon) else null
                }
                val result = if (pickup != null && destination != null) OpenRouteService().route(pickup, destination) else null
                if (result is RouteResult.Success) {
                    val route = result.route
                    binding.routeInfo.text = getString(R.string.route_summary, item.pickup, item.destination,
                        route.distanceMeters / 1000, ceil(route.durationSeconds / 60).toInt())
                    val points = route.points.map { GeoPoint(it.latitude, it.longitude) }
                    routePoints = points
                    binding.map.overlays.add(Polyline().apply {
                        setPoints(points)
                        outlinePaint.color = getColor(R.color.route_blue)
                        outlinePaint.strokeWidth = 9f
                    })
                    listOf(points.first(), points.last()).forEachIndexed { index, point ->
                        binding.map.overlays.add(Marker(binding.map).apply {
                            position = point
                            title = if (index == 0) item.pickup else item.destination
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    binding.map.visibility = View.VISIBLE
                    binding.attribution.visibility = View.VISIBLE
                    binding.map.post { binding.map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 32) }
                } else fallback(item)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { fallback(item) }
        }
    }

    private fun fallback(item: MyBookingItem) {
        if (booking?.demoStage == 2 && demoPoints.size == 2) {
            updateTracking()
            return
        }
        binding.map.visibility = View.GONE
        binding.attribution.visibility = View.GONE
        val image = RoutePreviews.image(item.pickupId, item.destinationId)
        binding.routeInfo.setText(if (image == null) R.string.preview_unavailable else R.string.preview_fallback)
        binding.routePreview.visibility = if (image == null) View.GONE else View.VISIBLE
        image?.let { binding.routePreview.setImageResource(it) }
    }

    private fun cancel() {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                val changed = db.dao().cancelBooking(bookingId, DemoStudent.ID)
                Toast.makeText(this@RideDetailsActivity,
                    if (changed > 0) R.string.cancel_success else R.string.cancel_missing, Toast.LENGTH_SHORT).show()
                show(db.dao().getBooking(bookingId, DemoStudent.ID))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                Toast.makeText(this@RideDetailsActivity, R.string.operation_error, Toast.LENGTH_LONG).show()
            } finally { setBusy(false) }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.cancelRide.isEnabled = !value
    }

    override fun onResume() { super.onResume(); binding.map.onResume() }
    override fun onPause() { binding.map.onPause(); super.onPause() }
    override fun onDestroy() { binding.map.onDetach(); super.onDestroy() }

    companion object {
        private const val EXTRA_BOOKING_ID = "bookingId"
        fun intent(context: Context, bookingId: Int): Intent =
            Intent(context, RideDetailsActivity::class.java).putExtra(EXTRA_BOOKING_ID, bookingId)
    }
}
