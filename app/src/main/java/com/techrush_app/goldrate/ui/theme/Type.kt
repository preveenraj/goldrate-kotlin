package com.techrush_app.goldrate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.R

/** Monospace family used for numeric values (gives the rate a clean "ticker" feel). */
val MonoFont = FontFamily(Font(R.font.jetbrainsmono))

// Labels and headings use the platform's default sans for a clean, minimal look.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)
