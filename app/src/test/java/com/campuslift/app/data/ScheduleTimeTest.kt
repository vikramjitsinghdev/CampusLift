package com.campuslift.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTimeTest {
    private val evening = ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 17, 5))

    @Test fun rideLaterTodayIsBookedForToday() {
        assertEquals("2026-11-16", evening.nextServiceDate("18:00"))
    }

    @Test fun rideThatAlreadyLeftIsBookedForTomorrow() {
        assertEquals("2026-11-17", evening.nextServiceDate("16:00"))
        assertEquals("2026-11-17", evening.nextServiceDate("17:05"))
    }

    @Test fun rideCannotBeBookedOnceItsTimeOrDateHasPassed() {
        assertEquals(false, evening.hasDeparted("2026-11-16", "18:00"))
        assertEquals(true, evening.hasDeparted("2026-11-16", "17:05"))
        assertEquals(true, evening.hasDeparted("2026-11-15", "23:00"))
        assertEquals(false, evening.hasDeparted("2026-11-17", "09:00"))
    }

    @Test fun activeWindowReachesBackAcrossMidnight() {
        val afterMidnight = ScheduleTime.of(LocalDateTime.of(2026, 11, 17, 0, 10))
        assertEquals("2026-11-16", afterMidnight.activeDate)
        assertEquals("23:40", afterMidnight.activeTime)
        assertEquals("2026-11-17", afterMidnight.today)
        assertEquals("2026-11-18", afterMidnight.tomorrow)
    }

    @Test fun campusTimeIgnoresDeviceTimezone() {
        // 03:30 UTC on Nov 17 is 23:30 AST on Nov 16 in America/Halifax, and 12:30 JST on Nov 17 in Tokyo.
        val instant = Instant.parse("2026-11-17T03:30:00Z")
        val utcClock = Clock.fixed(instant, ZoneId.of("UTC"))
        val tokyoClock = Clock.fixed(instant, ZoneId.of("Asia/Tokyo"))
        val halifaxClock = Clock.fixed(instant, ScheduleTime.CAMPUS_ZONE)

        assertEquals(ScheduleTime.now(halifaxClock), ScheduleTime.now(utcClock))
        assertEquals(ScheduleTime.now(halifaxClock), ScheduleTime.now(tokyoClock))
        assertEquals("2026-11-16", ScheduleTime.now(tokyoClock).today)
        assertEquals("23:30", ScheduleTime.now(tokyoClock).nowTime)
        assertEquals(LocalDate.of(2026, 11, 16), ScheduleTime.today(tokyoClock))
    }

    @Test fun activeWindowRespectsDstTransitions() {
        // Spring forward in America/Halifax: 2026-03-08 02:00 AST -> 03:00 ADT (06:00Z).
        // 15 minutes after spring-forward is 03:15 ADT (06:15Z); 30 elapsed minutes earlier is 01:45 AST.
        val afterSpring = Clock.fixed(Instant.parse("2026-03-08T06:15:00Z"), ZoneId.of("UTC"))
        val springTime = ScheduleTime.now(afterSpring)
        assertEquals("03:15", springTime.nowTime)
        assertEquals("01:45", springTime.activeTime)

        // Fall back in America/Halifax: 2026-11-01 02:00 ADT -> 01:00 AST (05:00Z).
        // 15 minutes after fall-back is 01:15 AST (05:15Z); 30 elapsed minutes earlier is 01:45 ADT.
        val afterFall = Clock.fixed(Instant.parse("2026-11-01T05:15:00Z"), ZoneId.of("UTC"))
        val fallTime = ScheduleTime.now(afterFall)
        assertEquals("01:15", fallTime.nowTime)
        assertEquals("01:45", fallTime.activeTime)
    }

    @Test fun exactThirtyMinuteBoundarySeparatesActiveAndCompleted() {
        val booking = MyBookingItem(
            bookingId = 1, pickupId = 4, destinationId = 6,
            driverName = "Driver", carModel = "Van", phone = "555-0100",
            pickup = "SUB", destination = "Mall", departureTime = "18:00",
            serviceDate = "2026-11-16", status = BookingStatus.CONFIRMED,
            plate = "DEMO-101", demoStage = 0, demoStartedAt = null
        )
        assertEquals(RideState.ACTIVE, ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 18, 30)).stateOf(booking))
        assertEquals(RideState.COMPLETED, ScheduleTime.of(LocalDateTime.of(2026, 11, 16, 18, 31)).stateOf(booking))
    }
}
