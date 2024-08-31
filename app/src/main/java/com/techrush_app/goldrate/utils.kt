package com.techrush_app.goldrate

import android.util.Log
import androidx.compose.ui.platform.LocalInspectionMode
import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import it.skrape.selects.html5.table
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

public fun getDayStatus(dateString: String): String {
    val dateFormat = SimpleDateFormat("dd-MMM-yy", Locale.getDefault())
    val date = dateFormat.parse(dateString)
    val currentDate = Date()

    val diffInDays = ((currentDate.time - date.time) / (1000 * 60 * 60 * 24)).toInt()
    Log.d("diffInDays", diffInDays.toString())

    return when (diffInDays) {
        0 -> "Today"
        1 -> "Yesterday"
        in 2..7 -> "Last Week"
        else -> "Long Ago"
    }
}

public fun fetchData(): Result {
    val time = SimpleDateFormat("HHmm", Locale.getDefault()).format(Calendar.getInstance().time)
    val timeint = time.toInt()
    val result: Result = skrape(HttpFetcher) {
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
                        val tableString = this.toString()
                        val rateString = tableString.substring(tableString.lastIndexOf("Rs. "), tableString.lastIndexOf("Rs. ") + 8).substring(4)

                        var dateString = tableString.substring(tableString.lastIndexOf("Today") - 40, tableString.lastIndexOf("Today") - 1).trim()
                        dateString = dateString.substring(dateString.indexOf("-") - 2, dateString.lastIndexOf("-") + 3)

                        val dayStatus = getDayStatus(dateString)
                        val result = Result(rateString, dateString, dayStatus)
                        result
                    }
                }
            }
        }
    }
    return result
}
