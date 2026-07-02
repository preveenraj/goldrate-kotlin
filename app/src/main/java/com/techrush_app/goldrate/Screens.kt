package com.techrush_app.goldrate

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.techrush_app.goldrate.ui.theme.TextTertiary

private const val PAVAN_UNIT = "1 PAVAN · 8G · ₹"

/** 2026, month by month (per-pavan). */
@Composable
fun ThisYearScreen(onBack: () -> Unit) {
    AsyncChartScreen(
        title = "This Year",
        unitLabel = PAVAN_UNIT,
        hint = "This year · month by month",
        onBack = onBack,
        load = { fetchMonthlySeries(THIS_YEAR_MONTHLY_URL) },
    )
}

/** The full 1925 → present curve (per-pavan), on a log scale. */
@Composable
fun AllTimeScreen(onBack: () -> Unit) {
    AsyncChartScreen(
        title = "All-Time",
        unitLabel = PAVAN_UNIT,
        hint = "1925 → today · log scale",
        onBack = onBack,
        load = { fetchYearlySeries(YEARLY_URL) },
        logScale = true,
    )
}

/** History browser step 1: pick a year (2009 → last year). */
@Composable
fun HistoryYearsScreen(onBack: () -> Unit, onPickYear: (Int) -> Unit) {
    // Current-year (2026) data lives on the un-suffixed page reached via "This
    // Year"; the archives cover 2009 up to the previous year.
    val years = (2025 downTo 2009).toList()
    ChartScaffold(title = "History", onBack = onBack) {
        Text(
            text = "PER-PAVAN ARCHIVES",
            color = TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        years.forEach { year ->
            NavCard(
                title = year.toString(),
                subtitle = "Month-by-month rates",
                onClick = { onPickYear(year) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** History browser step 2: a year's monthly chart plus a list of its months. */
@Composable
fun HistoryYearScreen(year: Int, onBack: () -> Unit, onPickMonth: (String, String) -> Unit) {
    AsyncChartScreen(
        title = year.toString(),
        unitLabel = PAVAN_UNIT,
        hint = "$year · month by month",
        onBack = onBack,
        load = { fetchMonthlySeries(monthlyUrl(year)) },
        footer = { points ->
            SeriesStats(points)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "DAILY RATES BY MONTH",
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(12.dp))
            points.forEach { p ->
                val month = monthNameFromDate(p.date)
                if (month != null) {
                    NavCard(
                        title = "$month $year",
                        subtitle = "₹" + formatINR(p.rate) + " · per pavan (15th)",
                        onClick = { onPickMonth("$month $year", dailyUrl(month, year)) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
    )
}

/** History browser step 3: a single month's daily per-gram chart. */
@Composable
fun HistoryMonthScreen(label: String, url: String, onBack: () -> Unit) {
    AsyncChartScreen(
        title = label,
        unitLabel = PAVAN_UNIT,
        hint = "Daily rates · drag to inspect",
        onBack = onBack,
        load = { fetchDaily(url)?.history ?: emptyList() },
    )
}
