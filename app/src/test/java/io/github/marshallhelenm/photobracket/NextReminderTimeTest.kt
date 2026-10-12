package io.github.marshallhelenm.photobracket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

class NextReminderTimeTest {

    private fun at(hour: Int, minute: Int = 0): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 12, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private val fixed7pm = Settings(reminderRandom = false, reminderMinuteOfDay = 19 * 60)
    private val random = Settings(reminderRandom = true)

    private fun Calendar.day() = get(Calendar.DAY_OF_MONTH)
    private fun Calendar.minuteOfDay() = get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)

    @Test
    fun fixedTimeLaterTodayFiresToday() {
        val next = nextReminderTime(at(10), fixed7pm, shownToday = false)
        assertEquals(12, next.day())
        assertEquals(19 * 60, next.minuteOfDay())
    }

    @Test
    fun fixedTimeAlreadyPassedFiresTomorrow() {
        val next = nextReminderTime(at(20), fixed7pm, shownToday = false)
        assertEquals(13, next.day())
        assertEquals(19 * 60, next.minuteOfDay())
    }

    @Test
    fun alreadyShownTodayAlwaysWaitsForTomorrow() {
        val next = nextReminderTime(at(10), fixed7pm, shownToday = true)
        assertEquals(13, next.day())
    }

    @Test
    fun randomTimeStaysInsideTheDaytimeWindowAndAfterNow() {
        repeat(200) { seed ->
            val now = at(15, 30)
            val next = nextReminderTime(now, random, shownToday = false, random = Random(seed))
            assertEquals(12, next.day())
            assertTrue(next.after(now))
            assertTrue(next.minuteOfDay() in RANDOM_WINDOW_START_MINUTE until RANDOM_WINDOW_END_MINUTE)
        }
    }

    @Test
    fun randomTimeAfterWindowClosesFiresTomorrow() {
        val next = nextReminderTime(at(22), random, shownToday = false, random = Random(1))
        assertEquals(13, next.day())
        assertTrue(next.minuteOfDay() in RANDOM_WINDOW_START_MINUTE until RANDOM_WINDOW_END_MINUTE)
    }
}
