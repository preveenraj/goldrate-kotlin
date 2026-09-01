package com.techrush_app.goldrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the gold scraper against saved copies of the real keralagold.com
 * pages, so a change to their markup shows up here rather than as an empty
 * screen on someone's phone. The silver side has had this cover since 3.6.0;
 * gold went without.
 */
class GoldParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().use { it.readText() }

    /** The live per-gram page, which carries only the current month to date. */
    private fun perGram(): String = fixture("gold-per-gram.html")

    /** A `daily-gold-prices-*` archive: a full month, per pavan, with session splits. */
    private fun archive(): String = fixture("gold-daily-august-2026.html")

    @Test
    fun `parses the per-gram page`() {
        val result = parseDailyPage(perGram())
        assertNotNull(result)
        assertEquals("1-Sep-26", result!!.date)
        assertEquals(14385, result.rate)
    }

    /**
     * The rate cell reads "Rs. 14,385" on today's row and a bare "14370" on the
     * others — both must come through as plain integers.
     */
    @Test
    fun `reads both the prefixed and bare rate cells`() {
        val history = parseDailyPage(perGram())!!.history
        assertEquals(listOf(14370, 14385), history.map { it.rate })
    }

    @Test
    fun `parses a full month archive`() {
        val result = parseDailyPage(archive())!!
        assertEquals("31-Aug-26", result.date)
        assertEquals(114960, result.rate)
        // 31 days plus the days the site split into intraday sessions.
        assertEquals(39, result.history.size)
    }

    @Test
    fun `derives high low and change from the month`() {
        val result = parseDailyPage(archive())!!
        assertEquals(120080, result.high.rate)
        assertEquals("25-Aug-26", result.high.date)
        assertTrue(result.low.rate <= result.high.rate)
        val history = result.history
        assertEquals(history[history.size - 1].rate - history[history.size - 2].rate, result.change)
    }

    /** Every row must carry a date the rest of the app can format and sort by. */
    @Test
    fun `every point has a parseable date`() {
        for (point in parseDailyPage(archive())!!.history) {
            assertNotNull("unparseable date: ${point.date}", epochDay(point.date))
        }
    }

    /**
     * A layout change should degrade to the "no data" state, not a crash or a
     * screen full of numbers scraped out of the page's navigation.
     */
    @Test
    fun `returns null when the rate table is gone`() {
        assertNull(parseDailyPage("<html><body><p>1-Sep-26 14385</p></body></html>"))
        assertNull(parseDailyPage(""))
    }
}
