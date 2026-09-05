package com.techrush_app.goldrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pins the rule that keeps a stale feed off the screen.
 *
 * The feed is trusted over the app's own scrapers, and its own validation only
 * asks how old the *document* is. A publisher that ran before the sources had
 * posted the day's figure produces a document that is minutes old and carries
 * yesterday's rate — which the app once showed all morning, with refreshing
 * powerless to change it. These tests cover the two halves of the fix: knowing
 * that a date is not today, and choosing correctly once the scrapers have run.
 */
class StaleFeedFallbackTest {

    private fun daysFromToday(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("dd-MMM-yy", Locale.US).format(calendar.time)
    }

    private fun reading(date: String) = date

    @Test
    fun `today's date reads as today`() {
        assertTrue(isRateDateToday(daysFromToday(0)))
    }

    @Test
    fun `yesterday's date does not read as today`() {
        assertFalse(isRateDateToday(daysFromToday(-1)))
    }

    @Test
    fun `an unparseable date never reads as today`() {
        assertFalse(isRateDateToday("not-a-date"))
        assertFalse(isRateDateToday(""))
    }

    @Test
    fun `a scrape that has moved on beats a stale feed`() {
        val feed = reading(daysFromToday(-1))
        val scraped = reading(daysFromToday(0))
        assertEquals(scraped, preferNewer(feed, scraped) { it })
    }

    /**
     * Sunday and holidays: no source has a new rate, so both readings carry the
     * same older date. The feed wins, because it carries the fuller history the
     * chart draws.
     */
    @Test
    fun `an equally old scrape loses to the feed`() {
        val feed = reading(daysFromToday(-1))
        val scraped = reading(daysFromToday(-1))
        assertSame(feed, preferNewer(feed, scraped) { it })
    }

    @Test
    fun `an older scrape loses to the feed`() {
        val feed = reading(daysFromToday(-1))
        val scraped = reading(daysFromToday(-3))
        assertSame(feed, preferNewer(feed, scraped) { it })
    }

    @Test
    fun `either side missing falls back to the other`() {
        val reading = reading(daysFromToday(0))
        assertSame(reading, preferNewer(null, reading) { it })
        assertSame(reading, preferNewer(reading, null) { it })
        assertNull(preferNewer<String>(null, null) { it })
    }

    @Test
    fun `an unparseable date loses to a real one on either side`() {
        val real = reading(daysFromToday(-1))
        assertSame(real, preferNewer("not-a-date", real) { it })
        assertSame(real, preferNewer(real, "not-a-date") { it })
    }
}
