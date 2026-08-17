package com.techrush_app.goldrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the silver scraper against a saved copy of the real goodreturns
 * Kerala page, so a change to its markup shows up here rather than as an empty
 * screen on someone's phone.
 */
class SilverParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader().use { it.readText() }

    private fun fixture(): String = fixture("silver-kerala.html")

    private fun parsed(): SilverResult = parseSilverPage(fixture())!!

    /**
     * goodreturns serves a three-column table (10g/100g/1kg) to desktop agents
     * and a single "Silver /kg" column to mobile ones. The app is a phone, so
     * the mobile layout is the one that actually ships — both must parse.
     */
    private fun parsedMobile(): SilverResult =
        parseSilverPage(fixture("silver-kerala-mobile.html"))!!

    @Test
    fun `parses the page`() {
        assertNotNull(parseSilverPage(fixture()))
    }

    @Test
    fun `mobile layout parses to the same numbers as desktop`() {
        val desktop = parsed()
        val mobile = parsedMobile()
        assertEquals(desktop.perKg, mobile.perKg)
        assertEquals(desktop.date, mobile.date)
        assertEquals(desktop.change, mobile.change)
        assertEquals(desktop.history.map { it.rate }, mobile.history.map { it.rate })
        assertEquals(desktop.months, mobile.months)
    }

    @Test
    fun `mobile layout reads the per-kg column, not the day change`() {
        val mobile = parsedMobile()
        assertEquals(255_000, mobile.perKg)
        assertEquals(10, mobile.history.size)
        // 14 Aug shows a -5,000 delta beside a 2,55,000 rate; the rate must win.
        assertEquals(255_000, mobile.history.first { it.date == "14-Aug-26" }.rate)
    }

    @Test
    fun `latest reading is the newest row, in per-kg rupees`() {
        val result = parsed()
        assertEquals(255_000, result.perKg)
        assertEquals(255, result.perGram)
        assertEquals("17-Aug-26", result.date)
    }

    @Test
    fun `ten-day history runs oldest to newest`() {
        val history = parsed().history
        assertEquals(10, history.size)
        assertEquals("8-Aug-26", history.first().date)
        assertEquals("17-Aug-26", history.last().date)
        assertEquals(249_900, history.first().rate)
        assertEquals(255_000, history.last().rate)
    }

    @Test
    fun `change compares the latest two readings`() {
        // 17 Aug and 16 Aug both closed at 2,55,000.
        assertEquals(0, parsed().change)
        assertEquals(0, parsed().changePerGram)
    }

    @Test
    fun `monthly movement blocks carry open, close, high, low and trend`() {
        val months = parsed().months
        assertEquals(7, months.size)

        val august = months.first()
        assertEquals("August 2026", august.label)
        assertEquals(235_000, august.open)
        assertEquals(255_000, august.close)
        assertEquals(260_000, august.high)
        assertEquals(235_000, august.low)
        assertEquals(8.51, august.trendPct, 0.001)

        val march = months.first { it.label == "March 2026" }
        assertEquals(-23.08, march.trendPct, 0.001)

        // Every block must be internally consistent: the range brackets both ends.
        months.forEach { m ->
            assertTrue(m.label, m.high >= m.open && m.high >= m.close)
            assertTrue(m.label, m.low <= m.open && m.low <= m.close)
        }
    }

    @Test
    fun `label and percentage formatting`() {
        assertEquals("Aug 2026", shortMonthLabel("August 2026"))
        assertEquals("+8.51%", formatSignedPct(8.51))
        assertEquals("-4.08%", formatSignedPct(-4.08))
        assertEquals("+0.00%", formatSignedPct(0.0))
    }

    @Test
    fun `unrecognisable markup yields null rather than invented numbers`() {
        assertNull(parseSilverPage("<html><body>nothing here</body></html>"))
    }
}

/** Guards the 22K -> 24K conversion the gold tab's purity toggle relies on. */
class PurityTest {

    @Test
    fun `22K is the identity`() {
        assertEquals(14_270, Purity.K22.applyTo(14_270))
    }

    @Test
    fun `24K matches the rate aggregators' published figure`() {
        // goodreturns published 24K = 15,566 for the same day's 22K = 14,270.
        assertEquals(15_567, Purity.K24.applyTo(14_270))
        assertTrue(kotlin.math.abs(Purity.K24.applyTo(14_270) - 15_566) <= 1)
    }

    @Test
    fun `rescaling a Result moves every figure together`() {
        val base = Result(
            rate = 14_270,
            date = "17-Aug-26",
            session = null,
            dayStatus = "Today",
            change = 50,
            high = RatePoint("13-Aug-26", 14_500),
            low = RatePoint("1-Aug-26", 14_000),
            history = listOf(RatePoint("1-Aug-26", 14_000), RatePoint("17-Aug-26", 14_270)),
        )
        val k24 = base.at(Purity.K24)
        assertEquals(15_567, k24.rate)
        assertEquals(55, k24.change)
        assertEquals(15_818, k24.high.rate)
        assertEquals(15_273, k24.low.rate)
        assertEquals(listOf(15_273, 15_567), k24.history.map { it.rate })
        // 22K must pass through untouched.
        assertEquals(base, base.at(Purity.K22))
    }
}
