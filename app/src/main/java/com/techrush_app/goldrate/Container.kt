package com.techrush_app.goldrate

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.Gold
import com.techrush_app.goldrate.ui.theme.GoldSoft
import com.techrush_app.goldrate.ui.theme.GradientBackground
import com.techrush_app.goldrate.ui.theme.MonoFont
import com.techrush_app.goldrate.ui.theme.TextPrimary
import com.techrush_app.goldrate.ui.theme.TextSecondary
import com.techrush_app.goldrate.ui.theme.TextTertiary
import com.techrush_app.goldrate.ui.theme.TrendDown
import com.techrush_app.goldrate.ui.theme.TrendDownBg
import com.techrush_app.goldrate.ui.theme.TrendFlat
import com.techrush_app.goldrate.ui.theme.TrendFlatBg
import com.techrush_app.goldrate.ui.theme.TrendUp
import com.techrush_app.goldrate.ui.theme.TrendUpBg
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** A destination pushed onto the in-app navigation stack. Home is the base. */
sealed interface Screen {
    data object MonthDetail : Screen
    data object ThisYear : Screen
    data object AllTime : Screen
    data object Forecast : Screen
    data object Calculator : Screen
    data object HistoryYears : Screen
    data class HistoryYear(val year: Int) : Screen
    data class HistoryMonth(val label: String, val url: String) : Screen
}

/** Routes a [Screen] to its composable. Screens needing today's rate render
 *  nothing if it's somehow absent (they're only reachable once it has loaded). */
@Composable
private fun AppScreen(
    screen: Screen,
    data: Result?,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    when (screen) {
        Screen.MonthDetail -> data?.let { RateDetailScreen(it, onBack) }
        Screen.ThisYear -> ThisYearScreen(onBack)
        Screen.AllTime -> AllTimeScreen(onBack)
        Screen.Forecast -> ForecastScreen(onBack)
        Screen.Calculator -> data?.let { CalculatorScreen(it.rate, onBack) }
        Screen.HistoryYears -> HistoryYearsScreen(onBack) { onNavigate(Screen.HistoryYear(it)) }
        is Screen.HistoryYear ->
            HistoryYearScreen(screen.year, onBack) { label, url ->
                onNavigate(Screen.HistoryMonth(label, url))
            }
        is Screen.HistoryMonth -> HistoryMonthScreen(screen.label, screen.url, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun Container() {
    val backgroundGradient = Brush.verticalGradient(GradientBackground)

    val isPreview = LocalInspectionMode.current
    var data by remember { mutableStateOf<Result?>(null) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var isRefreshing by remember { mutableStateOf(false) }
    val navStack = remember { mutableStateListOf<Screen>() }
    val scope = rememberCoroutineScope()

    if (isPreview) {
        val previewRates = listOf(
            14320, 14310, 14275, 14000, 13905, 14040, 13645, 13545, 13350,
            13620, 13665, 13890, 13850, 13705, 13370, 13390, 13430, 13255,
            13230, 13100, 12845, 12955, 12980, 13085,
        )
        data = Result(
            rate = 13085,
            date = "26-Jun-26",
            session = "Evening",
            dayStatus = "Today",
            change = 105,
            high = RatePoint("1-Jun-26", 14320),
            low = RatePoint("25-Jun-26", 12845, "Morning"),
            history = previewRates.mapIndexed { i, r -> RatePoint("${i + 1}-Jun-26", r) },
        )
    } else {
        LaunchedEffect(Unit) {
            data = fetchData()
            isLoading = false
        }
    }

    // Re-fetch on demand (pull-to-refresh / retry). Keeps the existing data if
    // the fetch fails, so a transient network blip doesn't wipe the screen.
    val refresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            val fresh = fetchData()
            if (fresh != null) data = fresh
            isLoading = false
            isRefreshing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        val currentData = data
        val top = navStack.lastOrNull()
        BackHandler(enabled = navStack.isNotEmpty()) { navStack.removeAt(navStack.lastIndex) }
        when {
            top != null -> AppScreen(
                screen = top,
                data = currentData,
                onNavigate = { navStack.add(it) },
                onBack = { navStack.removeAt(navStack.lastIndex) },
            )
            isLoading -> LoadingState()
            currentData == null -> ErrorState(onRetry = refresh)
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                RateDashboard(currentData, onNavigate = { navStack.add(it) })
            }
        }
    }
}

@Composable
private fun RateDashboard(data: Result, onNavigate: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 28.dp)
    ) {
        Header(date = data.date, session = data.session, dayStatus = data.dayStatus)
        Spacer(Modifier.height(24.dp))

        HeroCard(rate = data.rate, change = data.change)
        Spacer(Modifier.height(14.dp))

        val pavan = data.rate * 8
        val changeText = (if (data.change > 0) "+" else if (data.change < 0) "-" else "") +
                "₹" + formatINR(data.change.absoluteValue)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                label = "Pavan · 8g",
                value = "₹" + formatINR(pavan),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Day Change",
                value = changeText,
                valueColor = trendColor(data.change),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                label = "Month High",
                value = "₹" + formatINR(data.high.rate),
                sub = formatDisplayDate(data.high.date),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Month Low",
                value = "₹" + formatINR(data.low.rate),
                sub = formatDisplayDate(data.low.date),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        TrendCard(
            history = data.history.map { it.rate },
            high = data.high.rate,
            low = data.low.rate,
            onClick = { onNavigate(Screen.MonthDetail) },
        )
        Spacer(Modifier.height(24.dp))

        Text(
            text = "EXPLORE",
            color = TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(12.dp))
        NavCard("This Year", "Month-by-month, this year") { onNavigate(Screen.ThisYear) }
        Spacer(Modifier.height(12.dp))
        NavCard("All-Time", "1925 → today · the long view") { onNavigate(Screen.AllTime) }
        Spacer(Modifier.height(12.dp))
        NavCard("Forecast", "Where the trend points next") { onNavigate(Screen.Forecast) }
        Spacer(Modifier.height(12.dp))
        NavCard("Jewellery Calculator", "Shop price · making + GST") { onNavigate(Screen.Calculator) }
        Spacer(Modifier.height(12.dp))
        NavCard("History", "Browse 2009–2025 archives") { onNavigate(Screen.HistoryYears) }
        Spacer(Modifier.height(20.dp))

        Text(
            text = "22 Carat · 916 gold  ·  Source: keralagold.com",
            color = TextTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Header(date: String, session: String?, dayStatus: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Kerala Gold",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Live 22K gold rate",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        DatePill(date = date, session = session, dayStatus = dayStatus)
    }
}

@Composable
private fun DatePill(date: String, session: String?, dayStatus: String) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GoldSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Gold)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = dayStatus + (session?.let { " · $it" } ?: ""),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = formatDisplayDate(date),
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HeroCard(rate: Int, change: Int) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PER GRAM · 22K",
            color = TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "₹" + formatINR(rate),
            color = TextPrimary,
            fontFamily = MonoFont,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        TrendChip(rate = rate, change = change)
    }
}

@Composable
private fun TrendChip(rate: Int, change: Int) {
    val previous = rate - change
    val pct = if (previous != 0) change * 100.0 / previous else 0.0
    val arrow = when {
        change > 0 -> "▲"
        change < 0 -> "▼"
        else -> "▬"
    }
    val color = trendColor(change)
    val bg = when {
        change > 0 -> TrendUpBg
        change < 0 -> TrendDownBg
        else -> TrendFlatBg
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
        Text(text = "vs last", color = color.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
private fun TrendCard(history: List<Int>, high: Int, low: Int, onClick: () -> Unit) {
    SurfaceCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "THIS MONTH",
                color = TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Details ›",
                color = Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Sparkline(
            points = history,
            lineColor = Gold,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
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

@Composable
private fun LoadingState() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(color = Gold)
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            text = "Unable to load the gold rate.\nCheck your connection and try again.",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Try again",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(GoldSoft)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

private fun trendColor(change: Int): Color = when {
    change > 0 -> TrendUp
    change < 0 -> TrendDown
    else -> TrendFlat
}
