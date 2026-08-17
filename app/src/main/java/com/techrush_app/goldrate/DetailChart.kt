package com.techrush_app.goldrate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.CardBorder
import com.techrush_app.goldrate.ui.theme.Gold
import com.techrush_app.goldrate.ui.theme.GoldSoft
import com.techrush_app.goldrate.ui.theme.TextPrimary
import com.techrush_app.goldrate.ui.theme.TextSecondary
import com.techrush_app.goldrate.ui.theme.TextTertiary
import com.techrush_app.goldrate.ui.theme.TrendDown
import com.techrush_app.goldrate.ui.theme.TrendUp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Detailed view of the current month's per-gram history: the interactive chart
 * plus month high/low stats. A thin wrapper over the shared chart scaffold.
 */
@Composable
fun RateDetailScreen(data: Result, purity: Purity, onBack: () -> Unit) {
    ChartScaffold(title = "Monthly Trend", onBack = onBack) {
        RateChartCard(
            unitLabel = purity.label + " · ₹ PER GRAM",
            hint = "Drag across the chart to inspect any day",
            points = data.history,
        )
        Spacer(Modifier.height(16.dp))
        SeriesStats(points = data.history)
    }
}

/**
 * Reusable screen that fetches a series, then shows the interactive chart with
 * loading/error handling. Used by the This Year, All-Time and History screens.
 */
@Composable
fun AsyncChartScreen(
    title: String,
    unitLabel: String,
    hint: String,
    onBack: () -> Unit,
    load: suspend () -> List<RatePoint>,
    logScale: Boolean = false,
    accent: Color = Gold,
    accentSoft: Color = GoldSoft,
    footer: @Composable ColumnScope.(List<RatePoint>) -> Unit = { SeriesStats(it) },
) {
    var points by remember { mutableStateOf<List<RatePoint>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(reloadKey) {
        isLoading = true
        points = load()
        isLoading = false
    }

    ChartScaffold(title = title, onBack = onBack, accent = accent, accentSoft = accentSoft) {
        val current = points
        when {
            isLoading -> ChartMessage("Loading…", showSpinner = true, accent = accent)
            current.isNullOrEmpty() ->
                RetryMessage(onRetry = { reloadKey++ }, accentSoft = accentSoft)
            else -> {
                RateChartCard(
                    unitLabel = unitLabel,
                    hint = hint,
                    points = current,
                    logScale = logScale,
                    accent = accent,
                )
                Spacer(Modifier.height(16.dp))
                footer(current)
            }
        }
    }
}

/** Shared page chrome: system-bar padding, a back button + title, scrollable body. */
@Composable
fun ChartScaffold(
    title: String,
    onBack: () -> Unit,
    accent: Color = Gold,
    accentSoft: Color = GoldSoft,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentSoft)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(text = "‹", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Back",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(20.dp))
        content()
    }
}

/** The card wrapping the interactive line chart, with a unit label and hint. */
@Composable
fun RateChartCard(
    unitLabel: String,
    hint: String,
    points: List<RatePoint>,
    logScale: Boolean = false,
    accent: Color = Gold,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = unitLabel,
            color = TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = hint, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        DetailChart(
            points = points,
            logScale = logScale,
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        )
    }
}

/** Generic Latest / Readings / High / Low summary derived from the series. */
@Composable
fun SeriesStats(points: List<RatePoint>) {
    if (points.isEmpty()) return
    val latest = points.last()
    val high = points.maxByOrNull { it.rate } ?: latest
    val low = points.minByOrNull { it.rate } ?: latest
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatCard(
            label = "Latest",
            value = "₹" + formatINR(latest.rate),
            sub = formatShortDate(latest.date),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Readings",
            value = points.size.toString(),
            sub = "data points",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatCard(
            label = "High",
            value = "₹" + formatINR(high.rate),
            sub = formatShortDate(high.date),
            valueColor = TrendUp,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Low",
            value = "₹" + formatINR(low.rate),
            sub = formatShortDate(low.date),
            valueColor = TrendDown,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChartMessage(text: String, showSpinner: Boolean = false, accent: Color = Gold) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = accent)
        } else {
            Text(text = text, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RetryMessage(onRetry: () -> Unit, accentSoft: Color = GoldSoft) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        Text(
            text = "Couldn't load this data.\nCheck your connection and try again.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Try again",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accentSoft)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DetailChart(
    points: List<RatePoint>,
    modifier: Modifier = Modifier,
    logScale: Boolean = false,
    accent: Color = Gold,
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "Not enough data to chart.", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    // -1 means "no selection": the chart shows its resting state.
    var selected by remember(points) { mutableIntStateOf(-1) }

    val density = LocalDensity.current
    val axisTextPx = with(density) { 11.sp.toPx() }
    val tipTextPx = with(density) { 13.sp.toPx() }
    val tipSubPx = with(density) { 11.sp.toPx() }

    val lineColor = accent
    val axisColor = TextTertiary
    val gridColor = CardBorder
    val markerHigh = TrendUp
    val markerLow = TrendDown

    val rates = points.map { it.rate }
    val maxV = rates.max()
    val minV = rates.min()
    val highIndex = rates.indexOf(maxV)
    val lowIndex = rates.indexOf(minV)

    // Map a rate onto a 0..1 vertical position. A log scale keeps decades of
    // growth (e.g. ₹14 in 1925 → ₹1L today) legible instead of a flat line.
    val useLog = logScale && minV > 0
    val tMin = if (useLog) log10(minV.toDouble()) else minV.toDouble()
    val tMax = if (useLog) log10(maxV.toDouble()) else maxV.toDouble()
    val tRange = (tMax - tMin).coerceAtLeast(1e-9)
    fun frac(v: Int): Float {
        val t = if (useLog) log10(v.toDouble()) else v.toDouble()
        return ((t - tMin) / tRange).toFloat()
    }

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                val n = points.size
                fun indexAt(x: Float): Int {
                    val padLeft = 44.dp.toPx()
                    val padRight = 12.dp.toPx()
                    val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                    val f = ((x - padLeft) / chartW).coerceIn(0f, 1f)
                    return (f * (n - 1)).roundToInt().coerceIn(0, n - 1)
                }
                detectHorizontalDragGestures(
                    onDragStart = { selected = indexAt(it.x) },
                    onHorizontalDrag = { change, _ -> selected = indexAt(change.position.x) },
                )
            }
            .pointerInput(points) {
                val n = points.size
                detectTapGestures { pos ->
                    val padLeft = 44.dp.toPx()
                    val padRight = 12.dp.toPx()
                    val chartW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                    val f = ((pos.x - padLeft) / chartW).coerceIn(0f, 1f)
                    selected = (f * (n - 1)).roundToInt().coerceIn(0, n - 1)
                }
            },
    ) {
        val n = points.size
        val padLeft = 44.dp.toPx()
        val padRight = 12.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 26.dp.toPx()
        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom

        fun xAt(i: Int) = padLeft + chartW * i / (n - 1)
        fun yAt(v: Int) = padTop + (1f - frac(v)) * chartH

        val axisPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = axisColor.toArgb()
            textSize = axisTextPx
        }

        // Horizontal gridlines with rupee labels down the Y axis.
        val gridSteps = 4
        axisPaint.textAlign = android.graphics.Paint.Align.RIGHT
        for (s in 0..gridSteps) {
            val y = padTop + chartH * s / gridSteps
            // Value at this gridline (invert the fractional position from the top).
            val fromTop = s.toFloat() / gridSteps
            val t = tMax - fromTop * tRange
            val v = if (useLog) 10.0.pow(t).roundToInt() else t.roundToInt()
            drawLine(
                color = gridColor,
                start = Offset(padLeft, y),
                end = Offset(size.width - padRight, y),
                strokeWidth = 1f,
            )
            drawContext.canvas.nativeCanvas.drawText(
                formatINR(v),
                padLeft - 6.dp.toPx(),
                y + axisTextPx / 3f,
                axisPaint,
            )
        }

        // Date labels along the X axis (first, thirds, last).
        axisPaint.textAlign = android.graphics.Paint.Align.CENTER
        val labelIndices = listOf(0, (n - 1) / 3, 2 * (n - 1) / 3, n - 1).distinct()
        for (i in labelIndices) {
            drawContext.canvas.nativeCanvas.drawText(
                formatShortDate(points[i].date),
                xAt(i),
                size.height - 6.dp.toPx(),
                axisPaint,
            )
        }

        // Gradient fill under the line.
        val fill = Path()
        val line = Path()
        points.forEachIndexed { i, p ->
            val x = xAt(i)
            val y = yAt(p.rate)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, padTop + chartH)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(xAt(n - 1), padTop + chartH)
        fill.close()
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                startY = padTop,
                endY = padTop + chartH,
            ),
        )
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Month high / low markers (hollow rings).
        fun ring(i: Int, color: Color) {
            val c = Offset(xAt(i), yAt(points[i].rate))
            drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = c)
            drawCircle(color = color, radius = 4.5.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        }
        ring(highIndex, markerHigh)
        ring(lowIndex, markerLow)

        // Selection: vertical guide, highlighted dot and a floating tooltip.
        if (selected in 0 until n) {
            val sx = xAt(selected)
            val sy = yAt(points[selected].rate)
            drawLine(
                color = axisColor,
                start = Offset(sx, padTop),
                end = Offset(sx, padTop + chartH),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(sx, sy))
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(sx, sy))

            val sel = points[selected]
            val rateLabel = "₹" + formatINR(sel.rate)
            val dateLabel = formatShortDate(sel.date) + (sel.session?.let { " · $it" } ?: "")
            val tipPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = TextPrimary.toArgb()
                textSize = tipTextPx
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
            val subPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = TextSecondary.toArgb()
                textSize = tipSubPx
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val padH = 10.dp.toPx()
            val padV = 8.dp.toPx()
            val textW = maxOf(tipPaint.measureText(rateLabel), subPaint.measureText(dateLabel))
            val boxW = textW + padH * 2
            val boxH = tipTextPx + tipSubPx + padV * 2 + 4.dp.toPx()
            var boxX = sx - boxW / 2
            boxX = boxX.coerceIn(padLeft, size.width - padRight - boxW)
            val boxY = padTop + 2.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
            drawRoundRect(
                color = CardBorder,
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
            drawContext.canvas.nativeCanvas.drawText(
                rateLabel,
                boxX + padH,
                boxY + padV + tipTextPx,
                tipPaint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                dateLabel,
                boxX + padH,
                boxY + padV + tipTextPx + tipSubPx + 4.dp.toPx(),
                subPaint,
            )
        }
    }
}
