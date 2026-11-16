package com.campuslift.app.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Where a booking belongs on the My Rides screen. */
enum class RideState { ACTIVE, COMPLETED, CANCELLED }

/**
 * A snapshot of "now" in the text formats the database uses.
 *
 * The schedule repeats daily and a booking is for one date. A confirmed booking is
 * active until [ACTIVE_MINUTES] after departure; after that it counts as completed.
 */
data class ScheduleTime(
    val today: String,        // "2026-11-16"
    val tomorrow: String,
    val nowTime: String,      // "17:05"
    val activeDate: String,   // date and time of (now - ACTIVE_MINUTES)
    val activeTime: String
) {
    /** The date of the next departure for a ride leaving at [departureTime] ("HH:mm"). */
    fun nextServiceDate(departureTime: String): String =
        if (departureTime > nowTime) today else tomorrow

    /** True when a ride on [serviceDate] at [departureTime] can no longer be booked. */
    fun hasDeparted(serviceDate: String, departureTime: String): Boolean =
        serviceDate < today || (serviceDate == today && departureTime <= nowTime)

    fun stateOf(booking: MyBookingItem): RideState = when {
        booking.status == BookingStatus.CANCELLED -> RideState.CANCELLED
        booking.demoStage == 3 -> RideState.COMPLETED
        booking.demoStage == 2 -> RideState.ACTIVE
        booking.serviceDate > activeDate ||
            (booking.serviceDate == activeDate && booking.departureTime >= activeTime) -> RideState.ACTIVE
        else -> RideState.COMPLETED
    }

    companion object {
        const val ACTIVE_MINUTES = 30L
        /** How far ahead the booking calendar goes. */
        const val BOOKING_DAYS_AHEAD = 30L
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")

        fun of(now: LocalDateTime): ScheduleTime {
            val cutoff = now.minusMinutes(ACTIVE_MINUTES)
            return ScheduleTime(
                today = now.toLocalDate().toString(),
                tomorrow = now.toLocalDate().plusDays(1).toString(),
                nowTime = now.format(TIME),
                activeDate = cutoff.toLocalDate().toString(),
                activeTime = cutoff.format(TIME)
            )
        }

        fun now(): ScheduleTime = of(LocalDateTime.now())
    }
}
