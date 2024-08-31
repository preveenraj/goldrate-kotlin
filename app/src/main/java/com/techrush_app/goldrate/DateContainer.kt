package com.techrush_app.goldrate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.LightBlue

@Composable
public fun DateContainer(date: String, dayStatus: String) {
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
        Text(
            text = dayStatus,
            color = Color.White,
            fontSize = 24.sp
        )
    }
}