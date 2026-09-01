package com.techrush_app.goldrate

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Backup for goodreturns' silver page, so the silver tab can't be taken out by
 * one host the way the gold tab was.
 *
 * bankbazaar publishes a Kerala-specific silver rate that matched goodreturns
 * to the rupee on both days the two overlap (₹2,55,000/kg today, ₹2,60,000
 * yesterday, on 1 Sep 2026), and carries an explicit "Updated on" stamp, so a
 * stale page shows its real date rather than being passed off as today's.
 *
 * What it deliberately does NOT carry: the month-by-month history. bankbazaar's
 * monthly high/low disagrees with goodreturns on every month the two publish —
 * they sample differently — so mixing them would show one source's rate above
 * another source's history. [SilverResult.months] stays empty here and the UI
 * drops that section.
 */
const val BACKUP_SILVER_URL = "https://www.bankbazaar.com/silver-rate-kerala.html"

/** Heading above the two-day table this parser reads. */
private const val TODAY_YESTERDAY_ANCHOR = "Today & Yesterday"

private val BACKUP_SILVER_TAG_REGEX = Regex("<[^>]+>")
private val BACKUP_SILVER_RUPEE_REGEX = Regex("""₹\s*([\d,]+)""")
private val PER_KG_ROW_REGEX = Regex("""\b1\s*kg\b""", RegexOption.IGNORE_CASE)
private val PER_GRAM_ROW_REGEX = Regex("""\b1\s*gram\b""", RegexOption.IGNORE_CASE)
// "Updated on 01 Sep 2026", printed above the rate.
private val UPDATED_ON_REGEX =
    Regex("""Updated on\s+(\d{1,2})\s+([A-Z][a-z]{2})[a-z]*\s+(\d{4})""")

private fun decodeBackupSilverEntities(html: String): String = html
    .replace("&#x20b9;", "₹")
    .replace("&#8377;", "₹")
    .replace("&rupee;", "₹")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")

/**
 * A table row's visible text. Splitting on `<tr` leaves that tag's own
 * attributes at the head of the chunk with no `<` to strip them by, so they're
 * cut at the first `>` before the remaining tags come out — otherwise class
 * names end up in the parsed text.
 */
private fun rowText(row: String): String {
    val body = row.substringAfter('>', row)
    return BACKUP_SILVER_TAG_REGEX.replace(body, " ").split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

/** The day before [date], in the same "1-Sep-26" form. Null if it can't be parsed. */
private fun previousDay(date: String): String? = try {
    val format = SimpleDateFormat("dd-MMM-yy", Locale.US)
    val parsed = format.parse(date)
    if (parsed == null) {
        null
    } else {
        val calendar = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_MONTH, -1)
        }
        format.format(calendar.time).trimStart('0')
    }
} catch (e: Exception) {
    Log.e("fetchBackupSilver", "Could not step back from $date", e)
    null
}

/**
 * Fetches today's Kerala silver rate from the backup source. Returns null on
 * any network or layout failure so the caller can fall through to the error
 * state rather than show invented numbers.
 */
suspend fun fetchBackupSilver(): SilverResult? {
    val html = fetchPageHtml(BACKUP_SILVER_URL) ?: return null
    return parseBackupSilverPage(html)
}

/**
 * Turns the fetched page into a [SilverResult]. Split out from the network call
 * so it can be exercised against a saved copy of the real page.
 */
fun parseBackupSilverPage(rawHtml: String): SilverResult? {
    val html = decodeBackupSilverEntities(rawHtml)
    val start = html.indexOf(TODAY_YESTERDAY_ANCHOR)
    if (start < 0) {
        Log.e("fetchBackupSilver", "Today/yesterday silver table not found")
        return null
    }
    val end = html.indexOf("</table>", start).let { if (it < 0) html.length else it }

    // The row reads "1 kg ₹2,55,000 ₹2,60,000 ₹5,000 ▼": today, yesterday, then
    // the day's change, which is why only the first two amounts are taken.
    var perKg: List<Int>? = null
    var perGram: List<Int>? = null
    for (row in html.substring(start, end).split("<tr")) {
        val text = rowText(row)
        val amounts = BACKUP_SILVER_RUPEE_REGEX.findAll(text)
            .mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }
            .toList()
        if (amounts.size < 2) continue
        when {
            PER_KG_ROW_REGEX.containsMatchIn(text) && perKg == null -> perKg = amounts.take(2)
            PER_GRAM_ROW_REGEX.containsMatchIn(text) && perGram == null -> perGram = amounts.take(2)
        }
    }

    // Silver is quoted per kg; the per-gram row is the fallback if the kg row
    // ever disappears, since the two are the same number scaled by 1000.
    val rates = perKg ?: perGram?.map { it * 1000 }
    if (rates == null) {
        Log.e("fetchBackupSilver", "Today/yesterday table found but no rate row parsed")
        return null
    }
    val (today, yesterday) = rates

    val stamped = UPDATED_ON_REGEX.find(html)
    val date = if (stamped != null) {
        val (day, month, year) = stamped.destructured
        "${day.toInt()}-$month-${year.takeLast(2)}"
    } else {
        // No stamp: fall back to the device date rather than dropping the rate,
        // and let getDayStatus describe it as it finds it.
        SimpleDateFormat("d-MMM-yy", Locale.US).format(Date())
    }

    val history = buildList {
        previousDay(date)?.let { add(RatePoint(it, yesterday)) }
        add(RatePoint(date, today))
    }
    return SilverResult(
        perKg = today,
        date = date,
        dayStatus = getDayStatus(date),
        change = today - yesterday,
        history = history,
        months = emptyList(),
        periodLabel = "2-Day",
    )
}
