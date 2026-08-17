package com.techrush_app.goldrate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.MonoFont
import com.techrush_app.goldrate.ui.theme.Silver
import com.techrush_app.goldrate.ui.theme.SilverSoft
import com.techrush_app.goldrate.ui.theme.TextPrimary
import com.techrush_app.goldrate.ui.theme.TextSecondary
import com.techrush_app.goldrate.ui.theme.TextTertiary
import com.techrush_app.goldrate.ui.theme.TrendDown
import com.techrush_app.goldrate.ui.theme.TrendDownBg
import com.techrush_app.goldrate.ui.theme.TrendFlat
import com.techrush_app.goldrate.ui.theme.TrendFlatBg
import com.techrush_app.goldrate.ui.theme.TrendUp
import com.techrush_app.goldrate.ui.theme.TrendUpBg
import kotlin.math.absoluteValue

/** The two units silver is quoted in. Kilogram is the market's own unit; gram
 *  is what shoppers compare, so the dashboard can show either. */
private val SILVER_UNITS = listOf("Gram", "Kilogram")

/** The silver tab's dashboard: today's rate, the last ten days and month history. */
@Composable
fun SilverDashboard(data: SilverResult, onNavigate: (Screen) -> Unit) {
    var unitIndex by remember { mutableIntStateOf(0) }
    val perKg = unitIndex == 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 28.dp),
    ) {
        SilverHeader(date = data.date, dayStatus = data.dayStatus)
        Spacer(Modifier.height(24.dp))

        SilverHeroCard(data = data, perKg = perKg, unitIndex = unitIndex) { unitIndex = it }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                label = if (perKg) "Per Gram" else "Per Kilogram",
                value = "₹" + formatINR(if (perKg) data.perGram else data.perKg),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Per 100g",
                value = "₹" + formatINR(data.perKg / 10),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        val high = data.history.maxByOrNull { it.rate }
        val low = data.history.minByOrNull { it.rate }
        if (high != null && low != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard(
                    label = "10-Day High",
                    value = "₹" + formatINR(scaled(high.rate, perKg)),
                    sub = formatDisplayDate(high.date),
                    valueColor = TrendUp,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "10-Day Low",
                    value = "₹" + formatINR(scaled(low.rate, perKg)),
                    sub = formatDisplayDate(low.date),
                    valueColor = TrendDown,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))

            SilverTrendCard(
                history = data.history.map { scaled(it.rate, perKg) },
                high = scaled(high.rate, perKg),
                low = scaled(low.rate, perKg),
                onClick = { onNavigate(Screen.SilverTrend) },
            )
            Spacer(Modifier.height(24.dp))
        }

        if (data.months.isNotEmpty()) {
            SectionHeading("MONTH BY MONTH")
            Spacer(Modifier.height(12.dp))
            data.months.forEach { month ->
                SilverMonthCard(month)
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = "999 fine silver · Kerala  ·  Source: goodreturns.in",
            color = TextTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The last-ten-days chart, opened from the trend card. */
@Composable
fun SilverTrendScreen(data: SilverResult, onBack: () -> Unit) {
    ChartScaffold(
        title = "Silver Trend",
        onBack = onBack,
        accent = Silver,
        accentSoft = SilverSoft,
    ) {
        RateChartCard(
            unitLabel = "SILVER · ₹ PER KG",
            hint = "Last 10 days · drag to inspect any day",
            points = data.history,
            accent = Silver,
        )
        Spacer(Modifier.height(16.dp))
        SeriesStats(points = data.history)
    }
}

/** Converts a per-kg reading to the unit the dashboard is currently showing. */
private fun scaled(perKg: Int, showPerKg: Boolean): Int =
    if (showPerKg) perKg else kotlin.math.round(perKg / 1000.0).toInt()

@Composable
private fun SilverHeader(date: String, dayStatus: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Kerala Silver",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "Live silver rate", color = TextSecondary, fontSize = 13.sp)
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SilverSoft)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Silver))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = dayStatus,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(text = formatDisplayDate(date), color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SilverHeroCard(
    data: SilverResult,
    perKg: Boolean,
    unitIndex: Int,
    onUnitChange: (Int) -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (perKg) "PER KILOGRAM" else "PER GRAM",
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            SegmentedToggle(
                options = SILVER_UNITS,
                selectedIndex = unitIndex,
                onSelect = onUnitChange,
                accent = Silver,
                accentSoft = SilverSoft,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "₹" + formatINR(if (perKg) data.perKg else data.perGram),
            color = TextPrimary,
            fontFamily = MonoFont,
            fontSize = if (perKg) 40.sp else 52.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        val change = if (perKg) data.change else data.changePerGram
        ChangeChip(change = change, base = if (perKg) data.perKg else data.perGram)
    }
}

/** Arrow + signed amount + percentage, coloured by direction. */
@Composable
private fun ChangeChip(change: Int, base: Int) {
    val previous = base - change
    val pct = if (previous != 0) change * 100.0 / previous else 0.0
    val color = when {
        change > 0 -> TrendUp
        change < 0 -> TrendDown
        else -> TrendFlat
    }
    val bg = when {
        change > 0 -> TrendUpBg
        change < 0 -> TrendDownBg
        else -> TrendFlatBg
    }
    val arrow = when {
        change > 0 -> "▲"
        change < 0 -> "▼"
        else -> "▬"
    }
    val amount = (if (change > 0) "+" else if (change < 0) "-" else "") +
        "₹" + formatINR(change.absoluteValue)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = arrow, color = color, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$amount  (${String.format(java.util.Locale.US, "%+.2f", pct)}%)",
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = MonoFont,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "vs yesterday", color = color.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun SilverTrendCard(history: List<Int>, high: Int, low: Int, onClick: () -> Unit) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "LAST 10 DAYS",
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = "Details ›", color = Silver, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Sparkline(
            points = history,
            lineColor = Silver,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Low ₹" + formatINR(low),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "High ₹" + formatINR(high),
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One published month: its open/close, its range and the direction it moved. */
@Composable
private fun SilverMonthCard(month: SilverMonth) {
    val color = when {
        month.trendPct > 0 -> TrendUp
        month.trendPct < 0 -> TrendDown
        else -> TrendFlat
    }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = shortMonthLabel(month.label),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatSignedPct(month.trendPct),
                color = color,
                fontFamily = MonoFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "₹" + formatINR(month.open) + " → ₹" + formatINR(month.close) + " per kg",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Text(
            text = "High ₹" + formatINR(month.high) + "  ·  Low ₹" + formatINR(month.low),
            color = TextTertiary,
            fontSize = 12.sp,
        )
    }
}

@Composable
fun SectionHeading(text: String) {
    Text(
        text = text,
        color = TextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}
