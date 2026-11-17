package com.campuslift.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campuslift.app.data.AppDatabase
import com.campuslift.app.data.DemoStudent
import com.campuslift.app.data.RideState
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.ui.MainViewModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val SUB = "Student Union Building (SUB)"
private const val MALL = "Regent Mall"

/** Drives the real screens: calendar date, available times, booking, Ride Details, cancel, My Rides. */
@RunWith(AndroidJUnit4::class)
class RideFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val db = AppDatabase.getInstance(context)

    @Test fun pickDateBookSeeRideDetailsCancelAndFindItInHistory() = runBlocking {
        db.syncSeed(context)
        db.dao().clearAllBookings()
        val tomorrow = ScheduleTime.now().tomorrow
        // Catches the Ride Details screen that should open by itself after booking.
        val monitor = instrumentation.addMonitor(RideDetailsActivity::class.java.name, null, false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { home ->
                await(home) { it.findViewById<Spinner>(R.id.pickup).count > 1 }
                // Pressing Plan Ride with the placeholders selected must not book anything.
                home.onActivity { it.findViewById<View>(R.id.findRide).performClick() }
                assertTrue(db.dao().allBookings(DemoStudent.ID).isEmpty())

                // Choose tomorrow (what the calendar does), then search.
                home.onActivity {
                    ViewModelProvider(it)[MainViewModel::class.java].setDate(tomorrow)
                    planRide(it)
                }
                chooseTimeAndPlan(home)
                await(home) {
                    it.findViewById<TextView>(R.id.details)?.text?.startsWith("Tomorrow 18:00") == true &&
                        it.findViewById<RecyclerView>(R.id.rides).adapter?.itemCount == 1 && routeShown(it)
                }
                home.onActivity { it.findViewById<View>(R.id.action).performClick() }
                assertTrue(db.dao().allBookings(DemoStudent.ID).isEmpty())
                tapText(context.getString(R.string.confirm_booking))

                val details = monitor.waitForActivityWithTimeout(15000)
                assertNotNull("Ride Details should open after booking", details)
                assertEquals(tomorrow, db.dao().activeBookings(DemoStudent.ID, ScheduleTime.now()).single().serviceDate)
                waitUntil("ride details") { details.findViewById<View>(R.id.rideCard).visibility == View.VISIBLE && routeShown(details) }
                instrumentation.runOnMainSync {
                    assertEquals("Ride confirmed", details.findViewById<TextView>(R.id.status).text.toString())
                    assertEquals("Tomorrow 18:00", details.findViewById<TextView>(R.id.departure).text.toString())
                    assertEquals(SUB, details.findViewById<TextView>(R.id.activePickup).text.toString())
                    assertEquals(MALL, details.findViewById<TextView>(R.id.activeDestination).text.toString())
                    details.findViewById<View>(R.id.cancelRide).performClick()
                }
                waitUntil("cancelled status") {
                    details.findViewById<TextView>(R.id.status).text.toString() == "Ride cancelled" &&
                        details.findViewById<View>(R.id.cancelRide).visibility == View.GONE
                }
                instrumentation.runOnMainSync { details.finish() }
            }
            assertEquals(4, db.dao().seatsLeft(2, tomorrow))

            ActivityScenario.launch(MyRidesActivity::class.java).use { history ->
                await(history) { it.findViewById<TabLayout>(R.id.tabs).getTabAt(2)?.text == "Cancelled (1)" }
                history.onActivity {
                    val tabs = it.findViewById<TabLayout>(R.id.tabs)
                    assertEquals("Active (0)", tabs.getTabAt(0)?.text)
                    assertEquals("Completed (0)", tabs.getTabAt(1)?.text)
                    tabs.getTabAt(2)?.select()
                }
                await(history) { it.findViewById<RecyclerView>(R.id.rides).adapter?.itemCount == 1 }
            }
            assertEquals(1, db.dao().bookingsIn(RideState.CANCELLED, DemoStudent.ID, ScheduleTime.now()).size)
        } finally {
            instrumentation.removeMonitor(monitor)
            db.dao().clearAllBookings()
        }
    }

    @Test fun homeScreenWorksInLandscape() = runBlocking {
        db.syncSeed(context)
        val tomorrow = ScheduleTime.now().tomorrow
        ActivityScenario.launch(MainActivity::class.java).use { home ->
            home.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await(home) {
                it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    it.findViewById<Spinner>(R.id.pickup).count > 1
            }
            home.onActivity {
                ViewModelProvider(it)[MainViewModel::class.java].setDate(tomorrow)
                planRide(it)
            }
            await(home) { it.findViewById<View>(R.id.timeSlot).isEnabled }
            home.onActivity { ViewModelProvider(it)[MainViewModel::class.java].selectSlot(6) }
            await(home) { it.findViewById<View>(R.id.findRide).isEnabled }
            home.onActivity { it.findViewById<View>(R.id.findRide).performClick() }
            await(home) { (it.findViewById<View>(R.id.action)?.height ?: 0) > 0 }
        }
    }

    @Test fun bookCheckInFromScreenshotsAndFinishDemo() = runBlocking {
        db.syncSeed(context)
        val dao = db.dao()
        dao.clearAllBookings()
        val now = ScheduleTime.now()
        assertEquals(com.campuslift.app.data.BookResult.SUCCESS, dao.bookRide(2, DemoStudent.ID, now.tomorrow, now))
        val id = dao.confirmedBookingId(2, DemoStudent.ID, now.tomorrow)!!
        val image = File(context.cacheDir, "ride-qr-test.png")
        val imagePicker = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == Intent.ACTION_GET_CONTENT && intent.type == "image/*") {
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(image)))
                } else null
        }
        instrumentation.addMonitor(imagePicker)
        try {
            ActivityScenario.launch<RideDetailsActivity>(RideDetailsActivity.intent(context, id)).use { details ->
                await(details) { it.findViewById<View>(R.id.showCode).visibility == View.VISIBLE }
                for (status in listOf(R.string.demo_passenger_checked, R.string.demo_in_progress)) {
                    details.onActivity { it.findViewById<View>(R.id.showCode).performClick() }
                    findText(context.getString(R.string.demo_qr_help))
                    instrumentation.waitForIdleSync()
                    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    image.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    tapText(context.getString(android.R.string.ok))
                    details.onActivity { it.findViewById<View>(R.id.scanCode).performClick() }
                    tapText(context.getString(R.string.demo_image))
                    await(details) { it.findViewById<TextView>(R.id.status).text == context.getString(status) }
                }
                await(details) { it.findViewById<View>(R.id.demoProgress).visibility == View.VISIBLE }
                val deadline = SystemClock.elapsedRealtime() + 35_000L
                var ended = false
                while (!ended && SystemClock.elapsedRealtime() < deadline) {
                    details.onActivity {
                        val end = it.findViewById<View>(R.id.endRide)
                        if (end.isEnabled) ended = end.performClick()
                    }
                    if (!ended) SystemClock.sleep(250)
                }
                assertTrue("The simulated vehicle should reach the destination", ended)
                await(details) { it.findViewById<TextView>(R.id.status).text == context.getString(R.string.completed_ride) }
            }
            assertTrue(dao.bookingsIn(RideState.COMPLETED, DemoStudent.ID, ScheduleTime.now()).any { it.bookingId == id })
        } finally {
            instrumentation.removeMonitor(imagePicker)
            image.delete()
            dao.clearAllBookings() // This screen test runs in the isolated emulator app.
        }
    }

    private fun planRide(activity: MainActivity) {
        val pickup = activity.findViewById<Spinner>(R.id.pickup)
        val destination = activity.findViewById<Spinner>(R.id.destination)
        pickup.setSelection((0 until pickup.count).first { pickup.getItemAtPosition(it) == SUB })
        destination.setSelection((0 until destination.count).first { destination.getItemAtPosition(it) == MALL })
    }

    private fun chooseTimeAndPlan(home: ActivityScenario<MainActivity>) {
        await(home) { it.findViewById<View>(R.id.timeSlot).isEnabled }
        home.onActivity { it.findViewById<View>(R.id.timeSlot).performClick() }
        val unavailable = findText("15:00–15:30 · Unavailable")
        assertFalse(unavailable.isEnabled)
        val bounds = Rect()
        findText("18:00–18:30").getBoundsInScreen(bounds)
        val pressedAt = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(pressedAt, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        await(home) { it.findViewById<View>(R.id.findRide).isEnabled }
        home.onActivity { it.findViewById<View>(R.id.findRide).performClick() }
    }

    private fun findText(text: String): android.view.accessibility.AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 5000
        do {
            val nodes = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)
            nodes?.firstOrNull {
                it.packageName?.toString() == context.packageName &&
                    it.text?.toString()?.equals(text, ignoreCase = true) == true
            }?.let { return it }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Could not find $text")
    }

    private fun tapText(text: String) {
        val node = findText(text)
        assertTrue(node.performAction(ACTION_CLICK) || node.parent?.performAction(ACTION_CLICK) == true)
    }

    /** With an ORS key and network the live map is shown; otherwise the supplied screenshot. */
    private fun routeShown(activity: Activity) =
        activity.findViewById<View>(R.id.routePreview).visibility == View.VISIBLE ||
            activity.findViewById<View>(R.id.map).visibility == View.VISIBLE

    private fun <T : Activity> await(scenario: ActivityScenario<T>, condition: (T) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Timed out waiting for the screen")
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20000
        do {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Timed out waiting for $what")
    }
}
