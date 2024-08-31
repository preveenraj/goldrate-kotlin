package com.techrush_app.goldrate

import android.util.Log
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.GradientBackground
import com.techrush_app.goldrate.ui.theme.LightBlue
import com.techrush_app.goldrate.ui.theme.OffWhite
import com.techrush_app.goldrate.ui.theme.Yellow
import it.skrape.core.htmlDocument
import it.skrape.fetcher.HttpFetcher
import it.skrape.fetcher.response
import it.skrape.fetcher.skrape
import it.skrape.selects.html5.table
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Result(val rateString: String, val dateString: String, val dayStatus: String)

@Composable
@Preview
public fun Container() {
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
            val isPreview = LocalInspectionMode.current
            var data: Result;

            data = when (isPreview) {
                true -> {
                    Result("5656", "31-Jan-24", "Today")
                }

                false -> {
                    fetchData()
                }
            }
            DateContainer(date = data.dateString, dayStatus = data.dayStatus)
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




