package com.campuslift.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal object ServiceShift {
    const val SLOT_COUNT = 22
    private const val START = 15 * 60
    private const val MINUTES_PER_SLOT = 30
    private const val MINUTES_PER_DAY = 24 * 60
    private val format = DateTimeFormatter.ofPattern("HH:mm")

    fun slotOf(departure: String): Int? {
        val time = LocalTime.parse(departure)
        val offset = (time.toSecondOfDay() / 60 - START + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return (offset / MINUTES_PER_SLOT).takeIf { it < SLOT_COUNT }
    }

    fun start(slot: Int) = LocalTime.ofSecondOfDay(((START + slot * MINUTES_PER_SLOT) % MINUTES_PER_DAY) * 60L)
    fun range(slot: Int) = "${start(slot).format(format)}–${start(slot + 1).format(format)}"

    fun dateOf(date: String, departure: String): String =
        if (departure < "02:00") LocalDate.parse(date).minusDays(1).toString() else date

    fun currentDate(time: ScheduleTime) = dateOf(time.today, time.nowTime)
}
