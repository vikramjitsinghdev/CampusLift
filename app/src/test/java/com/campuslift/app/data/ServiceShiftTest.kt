package com.campuslift.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ServiceShiftTest {
    @Test fun halfHourSlotsCoverTheOvernightShift() {
        assertEquals(22, ServiceShift.SLOT_COUNT)
        assertEquals("15:00–15:30", ServiceShift.range(0))
        assertEquals("01:30–02:00", ServiceShift.range(21))
        assertEquals(17, ServiceShift.slotOf("23:59"))
        assertEquals(18, ServiceShift.slotOf("00:00"))
        assertNull(ServiceShift.slotOf("02:00"))
        assertNull(ServiceShift.slotOf("14:59"))
        assertEquals("2026-11-16", ServiceShift.currentDate(
            ScheduleTime.of(LocalDateTime.of(2026, 11, 17, 1, 59))))
    }
}
