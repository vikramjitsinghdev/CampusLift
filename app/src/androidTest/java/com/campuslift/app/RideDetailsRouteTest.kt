package com.campuslift.app

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campuslift.app.data.AppDatabase
import com.campuslift.app.data.BookResult
import com.campuslift.app.data.DemoStudent
import com.campuslift.app.data.ScheduleTime
import com.campuslift.app.routing.DrivingRoute
import com.campuslift.app.routing.GeoPoint
import com.campuslift.app.routing.RouteError
import com.campuslift.app.routing.RouteResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class RideDetailsRouteTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.getInstance(context)
    private val defaultFetcher = RideDetailsActivity.routeFetcher

    @After fun tearDown() {
        RideDetailsActivity.routeFetcher = defaultFetcher
        runBlocking { db.dao().clearAllBookings() }
    }

    @Test fun offlineFallbackRetriesOnceAndReplacesPreviewWithSingleLiveRoute() {
        runBlocking {
            db.syncSeed(context)
            db.dao().clearAllBookings()
            val tomorrow = ScheduleTime.now().tomorrow
            assertEquals(BookResult.SUCCESS, db.dao().bookRide(2, DemoStudent.ID, tomorrow, ScheduleTime.now()))
            val bookingId = db.dao().confirmedBookingId(2, DemoStudent.ID, tomorrow)!!

            val calls = AtomicInteger(0)
            val retryGate = CompletableDeferred<RouteResult>()
            RideDetailsActivity.routeFetcher = { _, _ ->
                if (calls.incrementAndGet() == 1) RouteResult.Failure(RouteError.NETWORK)
                else retryGate.await()
            }

            ActivityScenario.launch<RideDetailsActivity>(RideDetailsActivity.intent(context, bookingId)).use { scenario ->
                await(scenario) {
                    it.findViewById<View>(R.id.routePreview).visibility == View.VISIBLE &&
                        it.findViewById<View>(R.id.retryRoute).visibility == View.VISIBLE &&
                        it.findViewById<View>(R.id.map).visibility == View.GONE
                }

                // Repeated taps while a retry is in flight must not start duplicate requests.
                scenario.onActivity {
                    val retry = it.findViewById<View>(R.id.retryRoute)
                    retry.performClick()
                    retry.performClick()
                    retry.performClick()
                }
                await(scenario) { calls.get() == 2 }
                assertEquals(2, calls.get())

                retryGate.complete(
                    RouteResult.Success(
                        DrivingRoute(
                            distanceMeters = 2900.0,
                            durationSeconds = 420.0,
                            points = listOf(GeoPoint(45.94521, -66.64149), GeoPoint(45.93531, -66.66455))
                        )
                    )
                )

                await(scenario) {
                    it.findViewById<View>(R.id.map).visibility == View.VISIBLE &&
                        it.findViewById<View>(R.id.routePreview).visibility == View.GONE &&
                        it.findViewById<View>(R.id.retryRoute).visibility == View.GONE
                }
                scenario.onActivity {
                    assertEquals(3, it.findViewById<MapView>(R.id.map).overlays.size)
                }
            }
        }
    }

    @Test fun missingKeyShowsFallbackWithoutRetryOrDeveloperText() {
        runBlocking {
            db.syncSeed(context)
            db.dao().clearAllBookings()
            val tomorrow = ScheduleTime.now().tomorrow
            assertEquals(BookResult.SUCCESS, db.dao().bookRide(2, DemoStudent.ID, tomorrow, ScheduleTime.now()))
            val bookingId = db.dao().confirmedBookingId(2, DemoStudent.ID, tomorrow)!!

            RideDetailsActivity.routeFetcher = { _, _ -> RouteResult.Failure(RouteError.MISSING_KEY) }

            ActivityScenario.launch<RideDetailsActivity>(RideDetailsActivity.intent(context, bookingId)).use { scenario ->
                await(scenario) {
                    it.findViewById<View>(R.id.routePreview).visibility == View.VISIBLE &&
                        it.findViewById<View>(R.id.map).visibility == View.GONE
                }
                scenario.onActivity {
                    assertEquals(View.GONE, it.findViewById<View>(R.id.retryRoute).visibility)
                    val info = it.findViewById<TextView>(R.id.routeInfo).text.toString()
                    assertFalse(info.contains("ORS_API_KEY"))
                    assertFalse(info.contains("local.properties"))
                    assertTrue(info.contains("Route preview", ignoreCase = true) || info.contains("unavailable", ignoreCase = true))
                }
            }
        }
    }

    private fun await(scenario: ActivityScenario<RideDetailsActivity>, condition: (RideDetailsActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Timed out waiting for RideDetailsActivity route state")
    }
}
