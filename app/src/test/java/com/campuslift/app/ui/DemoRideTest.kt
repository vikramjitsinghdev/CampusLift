package com.campuslift.app.ui

import org.junit.Assert.*
import org.junit.Test

class DemoRideTest {
    @Test fun qrRequiresTheRightBookingStageAndExpiry() {
        val code = DemoRide.code(12, 0, 1_000)
        assertTrue(DemoRide.accepts(code, 12, 0, 2_000))
        assertFalse(DemoRide.accepts(code, 13, 0, 2_000))
        assertFalse(DemoRide.accepts(code, 12, 1, 2_000))
        assertFalse(DemoRide.accepts(code, 12, 0, 122_000))
        assertFalse(DemoRide.accepts("invalid", 12, 0, 2_000))
    }

    @Test fun simulationStopsAtTheDestination() {
        assertEquals(0.0, DemoRide.progress(1000, 0), 0.0)
        assertEquals(0.5, DemoRide.progress(1000, 16000), 0.0)
        assertEquals(1.0, DemoRide.progress(1000, 41000), 0.0)
    }
}
