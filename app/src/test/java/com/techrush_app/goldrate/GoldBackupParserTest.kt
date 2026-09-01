package com.techrush_app.goldrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the backup gold source against a saved copy of the real goodreturns
 * Kerala page, and — the point of the whole thing — pins it against the primary
 * source captured the same day. A backup that quietly drifts from keralagold.com
 * is worse than no backup, because nobody would notice.
 */
class GoldBackupParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().use { it.readText() }

    private fun backup(): Result = parseBackupGoldPage(fixture("gold-goodreturns-kerala.html"))!!

    @Test
    fun `parses the backup page`() {
        assertNotNull(parseBackupGoldPage(fixture("gold-goodreturns-kerala.html")))
    }

    @Test
    fun `reads the 22K column, not 24K`() {
        // The row is "Sep 01, 2026  ₹15,693 (+16)  ₹14,385 (+15)": 24K then 22K.
        assertEquals(14385, backup().rate)
        assertEquals("1-Sep-26", backup().date)
    }

    @Test
    fun `carries ten days, oldest first`() {
        val history = backup().history
        assertEquals(10, history.size)
        assertEquals("23-Aug-26", history.first().date)
        assertEquals("1-Sep-26", history.last().date)
    }

    /**
     * The high/low cards say "Month" on the primary source. The backup only has
     * ten days, so it must not make the same claim.
     */
    @Test
    fun `labels its window honestly`() {
        assertEquals("10-Day", backup().periodLabel)
        assertEquals("Month", parseDailyPage(fixture("gold-per-gram.html"))!!.periodLabel)
    }

    /**
     * Both fixtures were captured on 1 Sep 2026. Gold is a price people act on,
     * so the two sources have to agree exactly, not approximately.
     */
    @Test
    fun `agrees with the primary source to the rupee`() {
        val primary = parseDailyPage(fixture("gold-per-gram.html"))!!
        val fallback = backup()
        assertEquals(primary.date, fallback.date)
        assertEquals(primary.rate, fallback.rate)
        assertEquals(primary.change, fallback.change)
    }

    /**
     * The overlap runs further back than today: every day the archived primary
     * month and the backup's ten-day table share must carry the same rate.
     */
    @Test
    fun `agrees with the primary archive across the whole overlap`() {
        val archive = parseDailyPage(fixture("gold-daily-per-gram-august-2026.html"))!!
        // The archive splits some days into sessions; the closing row is the day's rate.
        val primaryByDate = archive.history.associate { it.date to it.rate }
        val overlap = backup().history.filter { it.date in primaryByDate }
        assertTrue("expected an overlap to compare", overlap.size >= 8)
        for (point in overlap) {
            assertEquals("mismatch on ${point.date}", primaryByDate[point.date], point.rate)
        }
    }

    @Test
    fun `returns null when the ten-day table is gone`() {
        assertNull(parseBackupGoldPage("<html><body>Gold Rate in Kerala</body></html>"))
        assertNull(parseBackupGoldPage(""))
    }
}
