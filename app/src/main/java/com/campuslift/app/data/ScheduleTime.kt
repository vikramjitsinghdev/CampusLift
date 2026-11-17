package com.campuslift.app.data

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Where a booking belongs on the My Rides screen. */
enum class RideState { ACTIVE, COMPLETED, CANCELLED }

/**
 * A snapshot of "now" in the text formats the database uses, always evaluated in campus time
 * ([CAMPUS_ZONE], `America/Halifax`) regardless of the device's local timezone.
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
        val CAMPUS_ZONE: ZoneId = ZoneId.of("America/Halifax")
        const val ACTIVE_MINUTES = 30L
        /** How far ahead the booking calendar goes. */
        const val BOOKING_DAYS_AHEAD = 30L
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")

        fun of(now: ZonedDateTime): ScheduleTime {
            val campusNow = now.withZoneSameInstant(CAMPUS_ZONE)
            val cutoff = campusNow.minusMinutes(ACTIVE_MINUTES)
            return ScheduleTime(
                today = campusNow.toLocalDate().toString(),
                tomorrow = campusNow.toLocalDate().plusDays(1).toString(),
                nowTime = campusNow.format(TIME),
                activeDate = cutoff.toLocalDate().toString(),
                activeTime = cutoff.format(TIME)
            )
        }

        fun of(now: LocalDateTime): ScheduleTime = of(now.atZone(CAMPUS_ZONE))

        fun today(clock: Clock = Clock.system(CAMPUS_ZONE)): LocalDate =
            ZonedDateTime.ofInstant(clock.instant(), CAMPUS_ZONE).toLocalDate()

        fun now(clock: Clock = Clock.system(CAMPUS_ZONE)): ScheduleTime =
            of(ZonedDateTime.ofInstant(clock.instant(), CAMPUS_ZONE))
    }
}
