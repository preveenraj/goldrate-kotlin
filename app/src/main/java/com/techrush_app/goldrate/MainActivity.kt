package com.techrush_app.goldrate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.GoldRateTheme
import com.techrush_app.goldrate.ui.theme.GradientBackground
import com.techrush_app.goldrate.ui.theme.LightBlue
import com.techrush_app.goldrate.ui.theme.OffWhite
import com.techrush_app.goldrate.ui.theme.Yellow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import it.skrape.core.*
import it.skrape.fetcher.*
import it.skrape.selects.html5.table

data class Result(val rateString: String, val dateString: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoldRateTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    Container()
                }
            }
        }
    }

    @Composable
    @Preview
    private fun Container() {
        val backgrundGradient = GradientBackground.let {
            Brush.verticalGradient(
                colors = it,
                startY = 0f,
                endY = Float.POSITIVE_INFINITY
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgrundGradient)
                .padding(top = 24.dp)
                .padding(bottom = 12.dp)
                .padding(start = 24.dp)
                .padding(end = 24.dp)
        ) {
            Text(text="₹",
                fontSize = 48.sp,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.BottomStart)
            )
            Box(modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
            ) {
                val data = fetchData()
                DateContainer(date = data.dateString)
                Column (
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    PriceContainer(
                        type = "Gram",
                        price = data.rateString,
                        isLarge = true,
                    )
                    Spacer(modifier = Modifier
                        .height(24.dp)
                    )
                    PriceContainer(
                        type = "Pavan",
                        price = (data.rateString.toInt() * 8).toString(),
                        isLarge = false,
                    )
                }
            }
        }
    }

    @Composable
    private fun DateContainer(date: String) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date,
                color = LightBlue,
                fontSize = 24.sp
            )
            Divider(
                color = Color(0xFFD9D9D9),
                thickness = 1.dp,
                modifier = Modifier
                    .width(220.dp)
                    .padding(vertical = 12.dp)
            )
        }
    }

    @Composable
    private fun PriceContainer(
        type: String = "Gram",
        price: String = "₹ 5499",
        isLarge: Boolean = true,
    ) {
        Box(modifier = Modifier
            .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
            ) {
                Text(
                    text = type,
                    color = OffWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily(
                        Font(R.font.jetbrainsmono)
                    ),
                    fontSize = isLarge .let {
                        if (it) 24.sp else 20.sp
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                )
                Text(
                    text = price,
                    color = Yellow,
                    fontSize = isLarge .let {
                        if (it) 88.sp else 44.sp
                    },
                    modifier = Modifier
                        .padding(top=18.dp),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false,
                        ),
                        fontFamily = FontFamily(
                            Font(R.font.jetbrainsmono)
                        ),
                    )
                )
            }
        }

    }

    private fun fetchData(): Result {
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

                            val result = Result(rateString, dateString)
                            result
                        }
                    }
                }
            }
        }
        return result
    }
}



