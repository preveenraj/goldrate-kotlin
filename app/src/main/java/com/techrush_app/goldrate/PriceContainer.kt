package com.techrush_app.goldrate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.OffWhite
import com.techrush_app.goldrate.ui.theme.Yellow


@Composable
public fun PriceContainer(
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