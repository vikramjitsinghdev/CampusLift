package com.campuslift.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

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
}
