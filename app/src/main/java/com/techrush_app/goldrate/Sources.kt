package com.techrush_app.goldrate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Root of every keralagold.com page we scrape. */
const val BASE = "https://www.keralagold.com/"

/** URL of the daily per-gram archive for a given month/year, e.g. "january" + 2026. */
fun dailyUrl(monthName: String, year: Int): String =
    BASE + "daily-gold-prices-${monthName.lowercase(Locale.US)}-$year.htm"

/** URL of the monthly (per-pavan) chart for a past year. */
fun monthlyUrl(year: Int): String = BASE + "monthly-gold-prices-$year.htm"

/** The current year's monthly chart lives at the un-suffixed URL. */
const val THIS_YEAR_MONTHLY_URL = BASE + "monthly-gold-prices.htm"

/** All-time (1925→present) yearly chart. */
const val YEARLY_URL = BASE + "yearly-gold-prices.htm"

private const val FETCH_ATTEMPTS = 2

private const val GOLD_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * Fetches a page's raw HTML. Returns null on any network failure.
 *
 * `Connection: close` is deliberate: HttpURLConnection otherwise pools the
 * socket, and a second request minutes later reliably picks up a connection the
 * CDN has already dropped — surfacing as "unexpected end of stream" and an
 * empty screen. One retry covers the rest of the ordinary transient failures.
 * Mirrors [SilverSource]'s fetcher, which has carried this fix since 3.6.0.
 */
internal suspend fun fetchPageHtml(pageUrl: String): String? = withContext(Dispatchers.IO) {
    repeat(FETCH_ATTEMPTS) { attempt ->
        try {
            val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", GOLD_UA)
                setRequestProperty("Accept", "text/html")
                setRequestProperty("Connection", "close")
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 20000
            }
            try {
                return@withContext conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e("fetchPageHtml", "Attempt ${attempt + 1} failed for $pageUrl", e)
        }
    }
    null
}

private val TAG_REGEX = Regex("<[^>]+>")
private val MONTH_NAME_REGEX = Regex(
    "January|February|March|April|May|June|July|August|September|October|November|December",
    RegexOption.IGNORE_CASE,
)
// "15-Jan-26" — a three-letter month, as used on the monthly tables.
private val SHORT_DATE_REGEX = Regex("""\d{1,2}-[A-Za-z]{3}-\d{2}""")
// "31-March-25" — a full month name, as used on the yearly table.
private val FULL_DATE_REGEX = Regex("""\d{1,2}-[A-Za-z]{3,}-\d{2}""")
// A rupee amount: comma-grouped ("1,05,320"), decimal ("13.75") or plain digits.
private val NUMBER_REGEX = Regex("""\d{1,3}(?:,\d{3})+|\d+(?:\.\d+)?""")
private val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")

private fun stripTags(row: String): String =
    TAG_REGEX.replace(row, " ").replace("&nbsp;", " ")

/**
 * Parses a monthly per-pavan table (rows like "January 15-Jan-26 105320"),
 * used for both the current year and the per-year archives. Each row carries a
 * full month name, a "dd-MMM-yy" date and the pavan (8g) rate.
 */
suspend fun fetchMonthlySeries(pageUrl: String): List<RatePoint> {
    val html = fetchPageHtml(pageUrl) ?: return emptyList()
    val out = mutableListOf<RatePoint>()
    for (row in html.split("<tr")) {
        val text = stripTags(row)
        if (!MONTH_NAME_REGEX.containsMatchIn(text)) continue
        val date = SHORT_DATE_REGEX.find(text) ?: continue
        val after = text.substring(date.range.last + 1)
        val rate = NUMBER_REGEX.find(after)?.value
            ?.replace(",", "")?.substringBefore(".")?.toIntOrNull() ?: continue
        out.add(RatePoint(date.value, rate))
    }
    return out
}

/**
 * Parses the yearly table (rows like "1925 31-March-25 13.75"). Requires a
 * hyphenated full-month date so the "Highest/Lowest ever" summary lines (which
 * use "29th January 2026") are skipped. The label is the four-digit year; the
 * rate is the first number after the date (the last row bleeds into trailing
 * nav markup, so we can't take the last number), rounded to whole rupees.
 */
/**
 * The per-gram series used for the short-term forecast. Built from the clean
 * monthly table (this year, falling back to include last year if the year is
 * young) converted from per-pavan to per-gram, with today's live rate appended
 * as the final anchor point. Avoids the daily archives, which are per-pavan and
 * parse noisily.
 */
suspend fun fetchForecastSeries(): List<RatePoint> {
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    var monthly = fetchMonthlySeries(THIS_YEAR_MONTHLY_URL)
    if (monthly.size < 5) {
        monthly = fetchMonthlySeries(monthlyUrl(thisYear - 1)) + monthly
    }
    val perGram = monthly.map { RatePoint(it.date, (it.rate / 8.0).roundToInt()) }
    val today = fetchData()
    return if (today != null) perGram + RatePoint(today.date, today.rate) else perGram
}

suspend fun fetchYearlySeries(pageUrl: String): List<RatePoint> {
    val html = fetchPageHtml(pageUrl) ?: return emptyList()
    val out = mutableListOf<RatePoint>()
    for (row in html.split("<tr")) {
        val text = stripTags(row)
        val date = FULL_DATE_REGEX.find(text) ?: continue
        val year = YEAR_REGEX.find(text)?.value ?: continue
        val after = text.substring(date.range.last + 1)
        val rate = NUMBER_REGEX.find(after)?.value
            ?.replace(",", "")?.toDoubleOrNull()?.roundToInt() ?: continue
        out.add(RatePoint(year, rate))
    }
    return out
}
