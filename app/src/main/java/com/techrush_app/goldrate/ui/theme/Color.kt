package com.techrush_app.goldrate.ui.theme

import androidx.compose.ui.graphics.Color

// --- Surfaces (light, minimal) ---
val ScreenBgTop = Color(0xFFF8F9FB)
val ScreenBgBottom = Color(0xFFEEF0F4)
val CardBg = Color(0xFFFFFFFF)
val CardBorder = Color(0xFFECEDF1)

// Subtle vertical background gradient for the screen.
val GradientBackground = listOf(ScreenBgTop, ScreenBgBottom)

// --- Gold accent ---
val Gold = Color(0xFFC8A12B)        // readable gold on light surfaces
val GoldDeep = Color(0xFFA9851A)
val GoldSoft = Color(0x1FC8A12B)    // ~12% gold tint for fills

// --- Text ---
val TextPrimary = Color(0xFF15171C)
val TextSecondary = Color(0xFF6B7280)
val TextTertiary = Color(0xFF9AA1AD)

// --- Trend ---
val TrendUp = Color(0xFF12805C)
val TrendUpBg = Color(0xFFE5F4ED)
val TrendDown = Color(0xFFC62F45)
val TrendDownBg = Color(0xFFFBE8EB)
val TrendFlat = Color(0xFF6B7280)
val TrendFlatBg = Color(0xFFEDEEF1)

// --- Aliases kept for the theme scaffold ---
val Black = Color(0xFF000000)
val Yellow = Gold
