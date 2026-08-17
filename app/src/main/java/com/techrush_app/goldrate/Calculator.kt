package com.techrush_app.goldrate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.Gold
import com.techrush_app.goldrate.ui.theme.TextPrimary
import com.techrush_app.goldrate.ui.theme.TextSecondary
import com.techrush_app.goldrate.ui.theme.TextTertiary
import java.util.Locale

/** One-tap shortcuts covering the range Kerala shops usually quote. */
private val MAKING_PRESETS = listOf("8", "10", "15", "25")

private const val DEFAULT_MAKING_PERCENT = "10" // typical Kerala value addition
private const val GST_RATE = 0.03               // 3% GST on gold + making

/**
 * A jewellery-shop price estimator built on today's per-gram rate. It mirrors
 * keralagold.com's calculator: gold value + making charge + 3% GST. The making
 * percentage is editable, because shops quote anywhere from 8% to 35% depending
 * on the design. Also works in reverse — how much gold a budget buys.
 */
@Composable
fun CalculatorScreen(ratePerGram: Int, purity: Purity, onBack: () -> Unit) {
    var weightText by remember { mutableStateOf("8") }
    var budgetText by remember { mutableStateOf("") }
    var makingText by remember { mutableStateOf(DEFAULT_MAKING_PERCENT) }

    // Guard against a nonsense entry (or an empty field mid-typing) skewing the
    // estimate; 0–100% covers every real quote.
    val makingPercent = (makingText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
    val makingRate = makingPercent / 100.0
    val makingLabel = "Making charge · " + trimGrams(makingPercent) + "%"

    ChartScaffold(title = "Jewellery Calculator", onBack = onBack) {
        Text(
            text = "Based on today's rate of ₹" + formatINR(ratePerGram) +
                " / gram (" + purity.label + ").",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))

        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("MAKING CHARGES")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Set what your jeweller quotes. Applied to both estimates below.",
                color = TextTertiary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            NumberField(
                value = makingText,
                onChange = { makingText = it },
                label = "Making charge (%)",
            )
            Spacer(Modifier.height(12.dp))
            SegmentedToggle(
                options = MAKING_PRESETS.map { "$it%" },
                selectedIndex = MAKING_PRESETS.indexOfFirst { it.toDouble() == makingPercent },
                onSelect = { makingText = MAKING_PRESETS[it] },
            )
        }
        Spacer(Modifier.height(16.dp))

        // --- Weight → estimated shop price ---
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("ESTIMATED SHOP PRICE")
            Spacer(Modifier.height(12.dp))
            NumberField(
                value = weightText,
                onChange = { weightText = it },
                label = "Weight (grams)",
            )
            val grams = weightText.toDoubleOrNull() ?: 0.0
            val gold = grams * ratePerGram
            val making = gold * makingRate
            val gst = (gold + making) * GST_RATE
            val total = gold + making + gst
            Spacer(Modifier.height(14.dp))
            BreakdownRow("Gold value", gold)
            BreakdownRow(makingLabel, making)
            BreakdownRow("GST · 3%", gst)
            Spacer(Modifier.height(8.dp))
            BreakdownRow("Total", total, emphasize = true)
            if (grams > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "≈ ₹" + formatINR((total / grams).toInt()) + " per gram all-in" +
                        "  ·  " + trimGrams(grams / 8) + " pavan",
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Budget → gold you can buy ---
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            SectionLabel("WHAT YOUR BUDGET BUYS")
            Spacer(Modifier.height(12.dp))
            NumberField(
                value = budgetText,
                onChange = { budgetText = it },
                label = "Budget (₹)",
            )
            val budget = budgetText.toDoubleOrNull() ?: 0.0
            val allInPerGram = ratePerGram * (1 + makingRate) * (1 + GST_RATE)
            val grams = if (allInPerGram > 0) budget / allInPerGram else 0.0
            Spacer(Modifier.height(14.dp))
            BreakdownRow("Gold (grams)", grams, isRupee = false)
            BreakdownRow("Gold (pavan · 8g)", grams / 8, isRupee = false)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Includes " + trimGrams(makingPercent) + "% making + 3% GST.",
                color = TextTertiary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Making charges vary 8–35% by design; GST is 3%. Estimates are " +
                "indicative for Kerala — confirm with your jeweller.",
            color = TextTertiary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            focusedLabelColor = Gold,
            cursorColor = Gold,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BreakdownRow(label: String, amount: Double, emphasize: Boolean = false, isRupee: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = if (emphasize) TextPrimary else TextSecondary,
            fontSize = if (emphasize) 15.sp else 14.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = if (isRupee) "₹" + formatINR(amount.toInt()) else trimGrams(amount),
            color = if (emphasize) Gold else TextPrimary,
            fontSize = if (emphasize) 15.sp else 14.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** Formats a gram/pavan quantity with up to two decimals, trimming trailing zeros. */
private fun trimGrams(value: Double): String {
    val s = String.format(Locale.US, "%.2f", value)
    return s.trimEnd('0').trimEnd('.')
}
