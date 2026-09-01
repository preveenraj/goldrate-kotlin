package com.techrush_app.goldrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the backup silver source, and pins it against a capture of the
 * primary source taken the same day. A backup that quietly drifts is worse than
 * no backup, because nobody would notice.
 */
class SilverBackupParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().use { it.readText() }

    private fun backup(): SilverResult =
        parseBackupSilverPage(fixture("silver-bankbazaar-kerala.html"))!!

    /** goodreturns, captured the same day as the bankbazaar fixture. */
    private fun primarySameDay(): SilverResult =
        parseSilverPage(fixture("silver-kerala-mobile-20260901.html"))!!

    @Test
    fun `parses the backup page`() {
        assertNotNull(parseBackupSilverPage(fixture("silver-bankbazaar-kerala.html")))
    }

    /**
     * The row is "1 kg ₹2,55,000 ₹2,60,000 ₹5,000 ▼" — today, yesterday, then
     * the change. Reading the change as a rate, or the per-gram row as per-kg,
     * would both be off by orders of magnitude.
     */
    @Test
    fun `reads the per-kg row, today then yesterday`() {
        assertEquals(255_000, backup().perKg)
        assertEquals(-5_000, backup().change)
        assertEquals(255, backup().perGram)
    }

    @Test
    fun `dates the rate from the page's own stamp`() {
        assertEquals("1-Sep-26", backup().date)
    }

    @Test
    fun `carries two days, oldest first`() {
        val history = backup().history
        assertEquals(2, history.size)
        assertEquals(RatePoint("31-Aug-26", 260_000), history.first())
        assertEquals(RatePoint("1-Sep-26", 255_000), history.last())
    }

    /**
     * bankbazaar's monthly high/low disagrees with goodreturns on every month
     * they share, so the backup must not publish months at all — the UI would
     * otherwise show one source's rate above another source's history.
     */
    @Test
    fun `publishes no month history`() {
        assertTrue(backup().months.isEmpty())
        assertTrue(primarySameDay().months.isNotEmpty())
    }

    @Test
    fun `labels its window honestly`() {
        assertEquals("2-Day", backup().periodLabel)
        assertEquals("10-Day", primarySameDay().periodLabel)
    }

    /**
     * Both fixtures were captured on 1 Sep 2026. Silver is a price people act
     * on, so the two sources have to agree exactly, not approximately — on
     * yesterday as well as today, which is the whole overlap bankbazaar offers.
     */
    @Test
    fun `agrees with the primary source to the rupee`() {
        val primary = primarySameDay()
        val fallback = backup()
        assertEquals(primary.date, fallback.date)
        assertEquals(primary.perKg, fallback.perKg)
        assertEquals(primary.change, fallback.change)

        val primaryByDate = primary.history.associate { it.date to it.rate }
        for (point in fallback.history) {
            assertEquals("mismatch on ${point.date}", primaryByDate[point.date], point.rate)
        }
    }

    @Test
    fun `returns null when the rate table is gone`() {
        assertNull(parseBackupSilverPage("<html><body>Silver Rate in Kerala</body></html>"))
        assertNull(parseBackupSilverPage(""))
    }
}
