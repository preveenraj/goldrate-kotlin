package com.techrush_app.goldrate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Kerala silver rates come from goodreturns.in rather than keralagold.com,
 * which publishes no silver page at all. The two agree on gold — goodreturns'
 * Kerala 22K matches keralagold.com to the rupee — and its silver figure is
 * genuinely state-specific (Kerala ₹255/g vs Delhi ₹250/g on the same day),
 * not a national spot price dressed up per city.
 */
const val SILVER_URL = "https://www.goodreturns.in/silver-rates/kerala.html"

private const val FETCH_ATTEMPTS = 2

private const val UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * Fetches a page's raw HTML with a browser-ish UA. Returns null on failure.
 *
 * `Connection: close` is deliberate: HttpURLConnection otherwise pools the
 * socket, and a second request minutes later reliably picks up a connection the
 * CDN has already dropped — surfacing as "unexpected end of stream". One retry
 * covers the rest of the ordinary transient failures.
 */
private suspend fun fetchHtml(pageUrl: String): String? = withContext(Dispatchers.IO) {
    repeat(FETCH_ATTEMPTS) { attempt ->
        try {
            val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", UA)
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
            Log.e("fetchHtml", "Attempt ${attempt + 1} failed for $pageUrl", e)
        }
    }
    null
}

private val SILVER_TAG_REGEX = Regex("<[^>]+>")

// "Aug 17, 2026" as printed in the ten-day and movement tables.
private val LONG_DATE_REGEX = Regex("""([A-Z][a-z]{2})[a-z]*\s+(\d{1,2}),\s*(\d{4})""")
// A rupee amount following the ₹ sign, e.g. "₹2,55,000".
private val RUPEE_REGEX = Regex("""₹\s*([\d,]+)""")
// A comma-grouped amount inside the monthly movement tables, e.g. "2,55,000".
private val GROUPED_REGEX = Regex("""\d{1,3}(?:,\d{2,3})+""")
// The signed percentage in a "Rising (+8.51%)" trend cell.
private val TREND_PCT_REGEX = Regex("""\(([+-]?\d+(?:\.\d+)?)%\)""")
// "Silver Price Movement in Kerala, August 2026" — the accordion headings. The
// mobile layout drops the comma ("...in Kerala August 2026"), so it's optional.
private val MONTH_BLOCK_REGEX = Regex(
    """Silver Price Movement in [A-Za-z ]+?,?\s*([A-Z][a-z]+ \d{4})\s*</span>.*?<tbody>(.*?)</tbody>""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)

/** Minimal entity decoding — the source only uses the numeric rupee entity plus the usual suspects. */
private fun decodeEntities(html: String): String = html
    .replace("&#x20b9;", "₹")
    .replace("&#8377;", "₹")
    .replace("&rupee;", "₹")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")

private fun stripSilverTags(html: String): String =
    SILVER_TAG_REGEX.replace(html, " ")

/** Normalises "Aug 17, 2026" into the "17-Aug-26" form the rest of the app parses. */
private fun normaliseDate(match: MatchResult): String {
    val (mon, day, year) = match.destructured
    return "${day.toInt()}-$mon-${year.takeLast(2)}"
}

/**
 * Scrapes today's Kerala silver rate, the last ten days and the published
 * month-by-month movement. Returns null if the page can't be fetched or the
 * ten-day table can't be found, so a layout change degrades to the error state
 * instead of showing invented numbers.
 */
suspend fun fetchSilver(): SilverResult? {
    val html = fetchHtml(SILVER_URL) ?: return null
    return parseSilverPage(html)
}

/**
 * Turns the fetched page into a [SilverResult]. Split out from the network call
 * so it can be exercised against a saved copy of the real page.
 */
fun parseSilverPage(rawHtml: String): SilverResult? {
    val html = decodeEntities(rawHtml)
    val history = parseTenDayTable(html)
    if (history.isEmpty()) {
        Log.e("fetchSilver", "Ten-day silver table not found or unparseable")
        return null
    }

    val latest = history.last()
    val previous = history.getOrNull(history.size - 2)
    return SilverResult(
        perKg = latest.rate,
        date = latest.date,
        dayStatus = getDayStatus(latest.date),
        change = if (previous != null) latest.rate - previous.rate else 0,
        history = history,
        months = parseMonthBlocks(html),
    )
}

/**
 * Reads the "Silver Rate in Kerala for Last 10 Days" table and flips it into
 * chronological order so it charts left-to-right.
 *
 * The layout depends on the user agent: the desktop page gives three columns
 * (10g, 100g, 1kg) while the mobile page gives one ("Silver /kg"). Taking the
 * largest rupee amount in the row lands on the per-kg figure either way — the
 * day's change sits outside the ₹ prefix, so it can't be mistaken for a rate.
 */
private fun parseTenDayTable(html: String): List<RatePoint> {
    val start = html.indexOf("Last 10 Days")
    if (start < 0) return emptyList()
    val end = html.indexOf("</table>", start).let { if (it < 0) html.length else it }
    val out = mutableListOf<RatePoint>()
    for (row in html.substring(start, end).split("<tr")) {
        val text = stripSilverTags(row)
        val date = LONG_DATE_REGEX.find(text) ?: continue
        val perKg = RUPEE_REGEX.findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }
            .maxOrNull() ?: continue
        out.add(RatePoint(normaliseDate(date), perKg))
    }
    return out.reversed()
}

/**
 * Reads the "Silver Price Movement in Kerala, <Month> <Year>" accordions. Each
 * body lists open, close, high and low per kilogram in that order, then a
 * signed trend percentage.
 */
private fun parseMonthBlocks(html: String): List<SilverMonth> =
    MONTH_BLOCK_REGEX.findAll(html).mapNotNull { match ->
        val label = match.groupValues[1]
        val text = stripSilverTags(match.groupValues[2])
        val values = GROUPED_REGEX.findAll(text)
            .mapNotNull { it.value.replace(",", "").toIntOrNull() }
            .toList()
        if (values.size < 4) return@mapNotNull null
        SilverMonth(
            label = label,
            open = values[0],
            close = values[1],
            high = values[2],
            low = values[3],
            trendPct = TREND_PCT_REGEX.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0,
        )
    }.toList()

/** "August 2026" -> "Aug 2026", for the compact month-history rows. */
fun shortMonthLabel(label: String): String {
    val parts = label.split(" ")
    if (parts.size != 2) return label
    return parts[0].take(3) + " " + parts[1]
}

/** Formats a signed percentage as "+8.51%" / "-4.08%". */
fun formatSignedPct(pct: Double): String = String.format(Locale.US, "%+.2f%%", pct)
