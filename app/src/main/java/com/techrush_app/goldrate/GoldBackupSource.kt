package com.techrush_app.goldrate

import android.util.Log

/**
 * Backup for keralagold.com, which went unreachable for a day and left the
 * screen empty.
 *
 * goodreturns publishes the same Kerala 22K figure — checked against
 * keralagold.com across the ten days to 1 Sep 2026 and identical to the rupee
 * on every one of them (see `GoldBackupParserTest`) — and the app already reads
 * this domain for silver, so the fallback adds no new dependency.
 *
 * The one difference: it carries ten days where keralagold carries the whole
 * month, so a [Result] built from here narrows its own high/low label via
 * [Result.periodLabel] rather than claiming a month it doesn't have.
 */
const val BACKUP_GOLD_URL = "https://www.goodreturns.in/gold-rates/kerala.html"

/** Heading above the table this parser reads. */
private const val TEN_DAY_ANCHOR = "Last 10 Days"

private val BACKUP_TAG_REGEX = Regex("<[^>]+>")
// A table cell opener, used to walk the header row column by column.
private val CELL_REGEX = Regex("""<t[dh][\s>]""", RegexOption.IGNORE_CASE)
// "Sep 01, 2026" as printed in the ten-day table.
private val BACKUP_DATE_REGEX = Regex("""([A-Z][a-z]{2})[a-z]*\s+(\d{1,2}),\s*(\d{4})""")
// A rupee amount, e.g. "₹14,385". The day's change sits outside the ₹ prefix.
private val BACKUP_RUPEE_REGEX = Regex("""₹\s*([\d,]+)""")

private fun stripBackupTags(html: String): String =
    BACKUP_TAG_REGEX.replace(html, " ")

private fun decodeBackupEntities(html: String): String = html
    .replace("&#x20b9;", "₹")
    .replace("&#8377;", "₹")
    .replace("&rupee;", "₹")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")

/** Normalises "Sep 01, 2026" into the "1-Sep-26" form the rest of the app parses. */
private fun normaliseBackupDate(match: MatchResult): String {
    val (mon, day, year) = match.destructured
    return "${day.toInt()}-$mon-${year.takeLast(2)}"
}

/**
 * Which rupee column holds the 22K rate, counted among the columns that carry
 * an amount (so the leading date column doesn't count). The table currently
 * reads "Date | 24K | 22K" on both the mobile and desktop layouts, but reading
 * the header means an added 18K or per-8g column can't silently shift which
 * number the app shows as the rate.
 */
private fun rupeeColumnFor22K(headerRow: String): Int? {
    val cells = CELL_REGEX.split(headerRow).drop(1).map { stripBackupTags(it).trim() }
    val index = cells.indexOfFirst { it.equals("22K", ignoreCase = true) }
    return if (index > 0) index - 1 else null
}

/**
 * Fetches today's Kerala 22K rate and the last ten days from the backup source.
 * Returns null on any network or layout failure, so the caller can fall through
 * to the error state rather than show invented numbers.
 */
suspend fun fetchBackupGold(): Result? {
    val html = fetchPageHtml(BACKUP_GOLD_URL) ?: return null
    return parseBackupGoldPage(html)
}

/**
 * Turns the fetched page into a [Result]. Split out from the network call so it
 * can be exercised against a saved copy of the real page.
 */
fun parseBackupGoldPage(rawHtml: String): Result? {
    val html = decodeBackupEntities(rawHtml)
    val start = html.indexOf(TEN_DAY_ANCHOR)
    if (start < 0) {
        Log.e("fetchBackupGold", "Ten-day gold table not found")
        return null
    }
    val end = html.indexOf("</table>", start).let { if (it < 0) html.length else it }

    var column: Int? = null
    val points = mutableListOf<RatePoint>()
    for (row in html.substring(start, end).split("<tr")) {
        if (column == null) column = rupeeColumnFor22K(row)
        val date = BACKUP_DATE_REGEX.find(stripBackupTags(row)) ?: continue
        val amounts = BACKUP_RUPEE_REGEX.findAll(stripBackupTags(row))
            .mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }
            .toList()
        if (amounts.isEmpty()) continue
        // Fall back to the last column, where 22K sits today, if the header
        // couldn't be read — better a rate than an empty screen.
        val rate = amounts.getOrNull(column ?: -1) ?: amounts.last()
        points.add(RatePoint(normaliseBackupDate(date), rate))
    }
    if (points.isEmpty()) {
        Log.e("fetchBackupGold", "Ten-day gold table found but no rows parsed")
        return null
    }

    // The table is printed newest-first; the app charts left-to-right.
    val history = points.reversed()
    val latest = history.last()
    val previous = history.getOrNull(history.size - 2)
    return Result(
        rate = latest.rate,
        date = latest.date,
        session = null,
        dayStatus = getDayStatus(latest.date),
        change = if (previous != null) latest.rate - previous.rate else 0,
        high = history.maxByOrNull { it.rate } ?: latest,
        low = history.minByOrNull { it.rate } ?: latest,
        history = history,
        periodLabel = "10-Day",
    )
}
