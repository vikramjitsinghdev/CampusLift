package com.campuslift.app.ui

import android.content.Context
import com.campuslift.app.R
import com.campuslift.app.data.RideState
import com.campuslift.app.data.ScheduleTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Turns a stored date into "Today", "Tomorrow" or "Wed, Nov 18" using campus time. */
fun Context.dayText(serviceDate: String, today: LocalDate = ScheduleTime.today()): String {
    val date = runCatching { LocalDate.parse(serviceDate) }.getOrNull() ?: return serviceDate
    return when (date) {
        today -> getString(R.string.today)
        today.plusDays(1) -> getString(R.string.tomorrow)
        else -> date.format(DAY)
    }
}

/** "Today 17:00", "Tomorrow 17:00" or "Wed, Nov 18 17:00". */
fun Context.departureText(
    serviceDate: String,
    departureTime: String,
    today: LocalDate = ScheduleTime.today()
): String = getString(R.string.departure_day_time, dayText(serviceDate, today), departureTime)

/** The word shown for a booking's state. */
fun Context.stateText(state: RideState): String = getString(when (state) {
    RideState.ACTIVE -> R.string.state_confirmed
    RideState.COMPLETED -> R.string.state_completed
    RideState.CANCELLED -> R.string.state_cancelled
})
