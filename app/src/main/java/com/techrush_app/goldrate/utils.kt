package com.techrush_app.goldrate

import android.util.Log
import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import it.skrape.selects.html5.table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

suspend fun fetchData(): Result? = withContext(Dispatchers.IO) {
    try {
        skrape(HttpFetcher) {
            // perform a GET request to the specified URL
            request {
                url = "https://www.keralagold.com/kerala-gold-rate-per-gram.htm"
            }

            response {
                // retrieve the HTML element from the
                // document as a string
                htmlDocument {
                    table {
                        withAttributes = listOf(
                            "cellspacing" to "0",
                            "width" to "280",
                        )
                        findAll {
                            parseTable(this.toString())
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("fetchData", "Failed to fetch or parse gold rate", e)
        null
    }
}

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
        history = points.map { it.rate },
    )
}
