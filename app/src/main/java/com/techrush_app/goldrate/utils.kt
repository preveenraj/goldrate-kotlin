package com.techrush_app.goldrate

import android.util.Log
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getDayStatus(dateString: String): String {
    var diffInDays = 9999
    try {
        val dateFormat = SimpleDateFormat("dd-MMM-yy", Locale.US)
        val date = dateFormat.parse(dateString)
        val currentDate = Date()

        date?.let {
            diffInDays = ((currentDate.time - date.time) / (1000 * 60 * 60 * 24)).toInt()
            Log.d("diffInDays", diffInDays.toString())
        }
    } catch (e: Exception) {
        Log.d("Exception while parsing date", e.toString())
    }

    return when (diffInDays) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..7 -> "This Week"
        else -> "Latest"
    }
}

/** Formats an integer rupee value with Indian digit grouping, e.g. 104680 -> "1,04,680". */
fun formatINR(value: Int): String =
    NumberFormat.getNumberInstance(Locale("en", "IN")).format(value)

/** Reformats a scraped "26-Jun-26" date into a friendly "26 Jun 2026". Falls back to the input. */
fun formatDisplayDate(dateString: String): String = try {
    val parsed = SimpleDateFormat("dd-MMM-yy", Locale.US).parse(dateString)
    if (parsed != null) SimpleDateFormat("dd MMM yyyy", Locale.US).format(parsed) else dateString
} catch (e: Exception) {
    dateString
}

/** Reformats a scraped "26-Jun-26" date into a compact "26 Jun" for chart axes. Falls back to the input. */
fun formatShortDate(dateString: String): String = try {
    val parsed = SimpleDateFormat("dd-MMM-yy", Locale.US).parse(dateString)
    if (parsed != null) SimpleDateFormat("dd MMM", Locale.US).format(parsed) else dateString
} catch (e: Exception) {
    dateString
}

/** Extracts the full month name ("January") from a "15-Jan-26" date, or null. */
fun monthNameFromDate(dateString: String): String? = try {
    val parsed = SimpleDateFormat("dd-MMM-yy", Locale.US).parse(dateString)
    if (parsed != null) SimpleDateFormat("MMMM", Locale.US).format(parsed) else null
} catch (e: Exception) {
    null
}

/** Converts a "15-Jan-26" date into a whole-day count since the epoch, or null. Used
 *  as the x-axis for trend fitting so gaps between readings are weighted correctly. */
fun epochDay(dateString: String): Long? = try {
    SimpleDateFormat("dd-MMM-yy", Locale.US).parse(dateString)?.let { it.time / 86_400_000L }
} catch (e: Exception) {
    null
}

/** Formats a whole-day epoch count back into a compact "26 Jun" label. */
fun formatEpochDay(day: Long): String =
    SimpleDateFormat("dd MMM", Locale.US).format(Date(day * 86_400_000L))

/**
 * Today's per-gram 22K rate.
 *
 * keralagold.com is the primary source. It was unreachable for a day and the
 * screen had nothing to fall back on, so a failure now retries against
 * goodreturns, which publishes the identical Kerala 22K figure — see
 * [fetchBackupGold]. Only a total failure of both reaches the error state.
 */
suspend fun fetchData(): Result? =
    fetchDaily(BASE + "kerala-gold-rate-per-gram.htm")
        ?: fetchBackupGold().also {
            if (it != null) Log.w("fetchData", "Primary source failed; served the backup")
        }

// The rate table on every daily page, e.g.
// `<table border=1 cellspacing=0 cellpadding=2 width="280" align="center">`.
// Attributes are matched individually rather than as one ordered pattern, and
// tolerate the page's mix of quoted and bare values.
private val TABLE_OPEN_REGEX = Regex("""<table[^>]*>""", RegexOption.IGNORE_CASE)
private val CELLSPACING_0_REGEX = Regex("""cellspacing\s*=\s*"?0(?!\d)""", RegexOption.IGNORE_CASE)
private val WIDTH_280_REGEX = Regex("""width\s*=\s*"?280(?!\d)""", RegexOption.IGNORE_CASE)

/**
 * Returns the inner HTML of the daily rate table, or null if the page doesn't
 * carry one. Isolating the table keeps [parseTable] from picking up dated rows
 * elsewhere on the page if the site ever adds a summary block.
 */
private fun dailyTableHtml(html: String): String? {
    for (open in TABLE_OPEN_REGEX.findAll(html)) {
        if (!CELLSPACING_0_REGEX.containsMatchIn(open.value)) continue
        if (!WIDTH_280_REGEX.containsMatchIn(open.value)) continue
        val end = html.indexOf("</table>", open.range.last + 1)
        if (end < 0) continue
        return html.substring(open.range.last + 1, end)
    }
    return null
}

/**
 * Scrapes a daily per-gram page (the current month, or a `daily-gold-prices-*`
 * archive — both use the same 280px table) into a [Result].
 *
 * Goes through [fetchPageHtml] rather than an HTML library so the gold screen
 * gets the same `Connection: close` and retry the silver fetcher has: without
 * them a second request minutes later picks up a pooled socket the CDN already
 * dropped, and the screen falls back to its "no data" state.
 */
suspend fun fetchDaily(pageUrl: String): Result? {
    val html = fetchPageHtml(pageUrl) ?: return null
    return parseDailyPage(html) ?: run {
        Log.e("fetchDaily", "No parseable rate table on $pageUrl")
        null
    }
}

/**
 * Parses a whole daily page into a [Result]: isolates the rate table, then
 * reads its rows. Kept separate from the fetch so it can be tested against
 * saved copies of the real pages — see `GoldParserTest`.
 */
internal fun parseDailyPage(html: String): Result? =
    dailyTableHtml(html)?.let { parseTable(it) }

// Matches a "dd-MMM-yy" date, e.g. "26-Jun-26".
private val DATE_REGEX = Regex("""\d{1,2}-[A-Za-z]{3}-\d{2}""")

// Matches a gold rate: either comma-grouped ("13,085") or 4+ plain digits ("12980").
private val RATE_REGEX = Regex("""\d{1,3}(?:,\d{3})+|\d{4,}""")

// Matches an intraday session label, e.g. "(Morning)".
private val SESSION_REGEX = Regex("""\((Morning|Afternoon|Evening|Night)\)""")

/**
 * Parses the scraped monthly table HTML into a [Result]. The page lists one row
 * per day (sometimes split into morning/evening sessions). We extract every
 * (date, rate) row, then derive the latest rate, the day-over-reading change,
 * the month high/low and the full history used for the trend chart.
 *
 * Returns null when no rows can be parsed, so a website layout change degrades
 * gracefully instead of crashing the app.
 */
private fun parseTable(tableString: String): Result? {
    val points = mutableListOf<RatePoint>()
    for (row in tableString.split("<tr")) {
        val dateMatch = DATE_REGEX.find(row) ?: continue
        // The rate always follows the date within the same row.
        val afterDate = row.substring(dateMatch.range.last + 1)
        val rate = RATE_REGEX.find(afterDate)?.value?.replace(",", "")?.toIntOrNull() ?: continue
        val session = SESSION_REGEX.find(row)?.groupValues?.get(1)
        points.add(RatePoint(dateMatch.value, rate, session))
    }
    if (points.isEmpty()) return null

    val latest = points.last()
    val previous = points.getOrNull(points.size - 2)
    val change = if (previous != null) latest.rate - previous.rate else 0
    val high = points.maxByOrNull { it.rate } ?: latest
    val low = points.minByOrNull { it.rate } ?: latest

    return Result(
        rate = latest.rate,
        date = latest.date,
        session = latest.session,
        dayStatus = getDayStatus(latest.date),
        change = change,
        high = high,
        low = low,
        history = points,
    )
}
