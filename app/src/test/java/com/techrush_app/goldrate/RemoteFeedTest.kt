package com.techrush_app.goldrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the rates feed parser against a real capture of what the workflow
 * publishes, and pins the rules that decide when the app must ignore the feed
 * and scrape for itself instead. Those rules are the whole safety story: the
 * feed is trusted over the app's own scrapers, so anything it can't fully
 * validate has to be rejected rather than shown.
 */
class RemoteFeedTest {

    private fun fixture(): String =
        javaClass.classLoader!!.getResourceAsStream("rates-feed.json")!!
            .bufferedReader().use { it.readText() }

    /** The instant the fixture was generated, so it reads as fresh. */
    private val generatedAt = 1_788_249_581L

    private fun parsed(nowSeconds: Long = generatedAt + 60) =
        parseRatesFeed(fixture(), nowSeconds)

    @Test
    fun `parses the published feed`() {
        assertNotNull(parsed())
    }

    @Test
    fun `reads gold with high and low derived from history`() {
        val gold = parsed()!!.gold!!
        assertEquals(14385, gold.rate)
        assertEquals("1-Sep-26", gold.date)
        assertEquals(15, gold.change)
        assertEquals("Month", gold.periodLabel)
        assertEquals(listOf(14370, 14385), gold.history.map { it.rate })
        assertEquals(14385, gold.high.rate)
        assertEquals(14370, gold.low.rate)
    }

    @Test
    fun `reads silver including the month history`() {
        val silver = parsed()!!.silver!!
        assertEquals(255_000, silver.perKg)
        assertEquals(255, silver.perGram)
        assertEquals("1-Sep-26", silver.date)
        assertEquals(-5_000, silver.change)
        assertEquals("10-Day", silver.periodLabel)
        assertEquals(10, silver.history.size)
        assertEquals(7, silver.months.size)
        assertEquals("August 2026", silver.months.first().label)
    }

    /** A JSON null session must not become the string "null" on screen. */
    @Test
    fun `a null session stays null`() {
        assertNull(parsed()!!.gold!!.session)
        assertTrue(parsed()!!.gold!!.history.all { it.session == null })
    }

    /**
     * The feed agrees with what the on-device scrapers produce from the same
     * day's pages — it is built from the same sources, and a drift between the
     * two would show users a different number depending on which path ran.
     */
    @Test
    fun `agrees with the on-device scrapers`() {
        val feed = parsed()!!
        val scrapedGold = parseDailyPage(
            javaClass.classLoader!!.getResourceAsStream("gold-per-gram.html")!!
                .bufferedReader().use { it.readText() }
        )!!
        assertEquals(scrapedGold.rate, feed.gold!!.rate)
        assertEquals(scrapedGold.date, feed.gold!!.date)
        assertEquals(scrapedGold.change, feed.gold!!.change)

        val scrapedSilver = parseSilverPage(
            javaClass.classLoader!!.getResourceAsStream("silver-kerala-mobile-20260901.html")!!
                .bufferedReader().use { it.readText() }
        )!!
        assertEquals(scrapedSilver.perKg, feed.silver!!.perKg)
        assertEquals(scrapedSilver.date, feed.silver!!.date)
        assertEquals(scrapedSilver.change, feed.silver!!.change)
        assertEquals(scrapedSilver.history.map { it.rate }, feed.silver!!.history.map { it.rate })
    }

    /**
     * A workflow that dies must not pin the app to a frozen rate. Six hours is
     * the cutoff; the workflow runs twice an hour, so this tolerates a long run
     * of failures before giving up on it.
     */
    @Test
    fun `rejects a feed that has gone stale`() {
        assertNotNull(parsed(generatedAt + 6 * 3600 - 10))
        assertNull(parsed(generatedAt + 6 * 3600 + 10))
    }

    /** A feed dated far in the future means a broken clock somewhere. */
    @Test
    fun `rejects a feed dated well into the future`() {
        assertNotNull(parsed(generatedAt - 60))
        assertNull(parsed(generatedAt - 7 * 3600))
    }

    /** An app that doesn't know the layout must scrape rather than misread it. */
    @Test
    fun `rejects an unknown schema`() {
        assertNull(parseRatesFeed(fixture().replace("\"schema\": 1", "\"schema\": 2"), generatedAt))
        assertNull(parseRatesFeed(fixture().replace("\"schema\": 1", "\"nope\": 1"), generatedAt))
    }

    @Test
    fun `rejects malformed json`() {
        assertNull(parseRatesFeed("", generatedAt))
        assertNull(parseRatesFeed("not json at all", generatedAt))
        assertNull(parseRatesFeed("{\"schema\":1,", generatedAt))
    }

    /**
     * A half-built feed must not blank one tab. Each metal is judged on its
     * own, so a missing block falls through to that metal's scrapers while the
     * other still uses the feed.
     */
    @Test
    fun `a missing or incomplete metal falls through on its own`() {
        val goldOnly = parseRatesFeed(
            fixture().replace("\"silver\"", "\"silverRemoved\""), generatedAt
        )!!
        assertNotNull(goldOnly.gold)
        assertNull(goldOnly.silver)

        val noGoldHistory = parseRatesFeed(
            fixture().replace("\"history\"", "\"historyRemoved\""), generatedAt
        )!!
        assertNull(noGoldHistory.gold)
        assertNull(noGoldHistory.silver)
    }
}
