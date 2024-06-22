package com.techrush_app.goldrate

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.techrush_app.goldrate.ui.theme.GoldRateTheme
//import org.jsoup.Jsoup
//import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import it.skrape.core.*
import it.skrape.fetcher.*
import it.skrape.selects.html5.table

//import org.jsoup.Jsoup


data class Result(val rateString: String, val tempStringForDate: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = updatePage()
        setContent {
            GoldRateTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting(result)
                }
            }
        }
    }

    private fun updatePage(): Result {
        val time = SimpleDateFormat("HHmm", Locale.getDefault()).format(Calendar.getInstance().time)
        val timeint = time.toInt()
        Log.i("Teda", "Time: $timeint")

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
                            // print the text of the element
                            Log.i("Teda list", "Data: $this")
                            val tableString = this.toString()
                            val rateString = tableString.substring(tableString.lastIndexOf("Rs. "), tableString.lastIndexOf("Rs. ") + 8).substring(4)

                            var tempStringForDate = tableString.substring(tableString.lastIndexOf("Today") - 40, tableString.lastIndexOf("Today") - 1).trim()
                            Log.i("Teda", "Data: $tempStringForDate")
                            tempStringForDate = tempStringForDate.substring(tempStringForDate.indexOf("-") - 2, tempStringForDate.lastIndexOf("-") + 3)

                            val result = Result(rateString, tempStringForDate)
                            result
                        }
                    }
                }
            }
        }
        Log.i("Teda", "Data: ${result}")
        return result
    }
}

@Composable
fun Greeting(result: Result, modifier: Modifier = Modifier) {
    Column {
        Row {
            Text(
                text = "Today is ${result.tempStringForDate}",
                modifier = modifier
            )
        }
        Row {
            Text(
                text = "And the gold rate is: ${result.rateString}",
                modifier = modifier
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GoldRateTheme {
        Greeting(Result("100", "2022-01-01"), Modifier)
    }
}

