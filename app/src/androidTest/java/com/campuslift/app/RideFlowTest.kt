package com.campuslift.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
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
                await(home) {
                    it.findViewById<TextView>(R.id.details)?.text?.startsWith("Tomorrow 18:00") == true &&
                        it.findViewById<RecyclerView>(R.id.rides).adapter?.itemCount == 2 && routeShown(it)
                }
                home.onActivity { it.findViewById<View>(R.id.action).performClick() }

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
            await(home) { (it.findViewById<View>(R.id.action)?.height ?: 0) > 0 }
        }
    }

    private fun planRide(activity: MainActivity) {
        val pickup = activity.findViewById<Spinner>(R.id.pickup)
        val destination = activity.findViewById<Spinner>(R.id.destination)
        pickup.setSelection((0 until pickup.count).first { pickup.getItemAtPosition(it) == SUB })
        destination.setSelection((0 until destination.count).first { destination.getItemAtPosition(it) == MALL })
        activity.findViewById<View>(R.id.findRide).performClick()
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
