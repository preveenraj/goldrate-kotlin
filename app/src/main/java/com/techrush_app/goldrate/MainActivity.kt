package com.techrush_app.goldrate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.techrush_app.goldrate.ui.theme.GoldRateTheme
import com.techrush_app.goldrate.ui.theme.ScreenBgTop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoldRateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBgTop,
                ) {
                    Container()
                }
            }
        }
    }
}
