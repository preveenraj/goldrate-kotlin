package com.techrush_app.goldrate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.GradientBackground

data class Result(val rateString: String, val dateString: String, val dayStatus: String)

@Composable
@Preview
fun Container() {
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

            val data: Result = when (isPreview) {
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




