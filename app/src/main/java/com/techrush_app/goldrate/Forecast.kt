package com.techrush_app.goldrate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techrush_app.goldrate.ui.theme.CardBorder
import com.techrush_app.goldrate.ui.theme.Gold
import com.techrush_app.goldrate.ui.theme.TextPrimary
import com.techrush_app.goldrate.ui.theme.TextSecondary
import com.techrush_app.goldrate.ui.theme.TextTertiary
import com.techrush_app.goldrate.ui.theme.TrendDown
import com.techrush_app.goldrate.ui.theme.TrendFlat
import com.techrush_app.goldrate.ui.theme.TrendUp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A single projected reading at [daysAhead] days, with a plausible low–high range. */
data class ForecastPoint(
    val daysAhead: Int,
    val label: String,
    val value: Int,
    val low: Int,
    val high: Int,
)

/** A short-term trend projection derived from the recent daily series. */
data class Forecast(
    val series: List<RatePoint>,
    val slopePerDay: Double,
    val residualStd: Double,
    val weeklyPct: Double,
    val direction: String,
    val confidence: String,
    val rSquared: Double,
    val projections: List<ForecastPoint>,
    val lastValue: Int,
    val lastDay: Long,
)

/**
 * Fits an ordinary least-squares line to the recent per-gram series (x = day) and
 * projects it forward. The band widens with the horizon (∝ √days) from the trend's
 * own residual scatter — a rough uncertainty cone, not a guarantee. Returns null if
 * there aren't enough distinct readings to fit a line.
 */
fun computeForecast(series: List<RatePoint>): Forecast? {
    if (series.size < 5) return null
    val pts = series.mapNotNull { p -> epochDay(p.date)?.let { it to p.rate } }
    if (pts.size < 5) return null

    val x0 = pts.first().first
    val xs = pts.map { (it.first - x0).toDouble() }
    val ys = pts.map { it.second.toDouble() }
    val n = xs.size
    val xBar = xs.average()
    val yBar = ys.average()
    var sxx = 0.0
    var sxy = 0.0
    for (i in xs.indices) {
        val dx = xs[i] - xBar
        sxx += dx * dx
        sxy += dx * (ys[i] - yBar)
    }
    if (sxx == 0.0) return null
    val slope = sxy / sxx
    val intercept = yBar - slope * xBar

    var sse = 0.0
    var sst = 0.0
    for (i in xs.indices) {
        val yHat = slope * xs[i] + intercept
        sse += (ys[i] - yHat).pow(2)
        sst += (ys[i] - yBar).pow(2)
    }
    val residualStd = sqrt(sse / max(1, n - 2))
    val rSquared = if (sst > 0) (1 - sse / sst).coerceIn(0.0, 1.0) else 0.0

    val lastValue = ys.last().toInt()
    val lastDay = pts.last().first

    fun project(days: Int, label: String): ForecastPoint {
        val value = lastValue + slope * days
        val half = 1.28 * residualStd * sqrt(days / 7.0)
        return ForecastPoint(days, label, value.roundToInt(), (value - half).roundToInt(), (value + half).roundToInt())
    }
    val projections = listOf(project(7, "1 week"), project(14, "2 weeks"), project(28, "4 weeks"))

    val weeklyPct = slope * 7 / lastValue * 100
    val twoWeekMove = slope * 14
    val direction = when {
        abs(twoWeekMove) < 0.5 * residualStd -> "Sideways"
        slope > 0 -> "Upward"
        else -> "Downward"
    }
    val confidence = when {
        rSquared < 0.25 -> "Low"
        rSquared < 0.6 -> "Moderate"
        else -> "High"
    }

    return Forecast(
        series = series,
        slopePerDay = slope,
        residualStd = residualStd,
        weeklyPct = weeklyPct,
        direction = direction,
        confidence = confidence,
        rSquared = rSquared,
        projections = projections,
        lastValue = lastValue,
        lastDay = lastDay,
    )
}

private fun directionColor(direction: String): Color = when (direction) {
    "Upward" -> TrendUp
    "Downward" -> TrendDown
    else -> TrendFlat
}

@Composable
fun ForecastScreen(purity: Purity, onBack: () -> Unit) {
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey, purity) {
        isLoading = true
        forecast = computeForecast(fetchForecastSeries().at(purity))
        isLoading = false
    }

    ChartScaffold(title = "Forecast", onBack = onBack) {
        val f = forecast
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Gold) }

            f == null -> ForecastUnavailable { reloadKey++ }

            else -> {
                HeadlineCard(f)
                Spacer(Modifier.height(16.dp))
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = purity.label + " · ₹ PER GRAM · PROJECTION",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Recent trend, projected 4 weeks with an uncertainty band",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    ForecastChart(
                        forecast = f,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                f.projections.forEach { p ->
                    ProjectionRow(p, currentValue = f.lastValue)
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(4.dp))
                DisclaimerCard(f)
            }
        }
    }
}

@Composable
private fun HeadlineCard(f: Forecast) {
    val color = directionColor(f.direction)
    val arrow = when (f.direction) {
        "Upward" -> "▲"
        "Downward" -> "▼"
        else -> "▬"
    }
    val pctText = String.format(java.util.Locale.US, "%+.1f%%", f.weeklyPct)
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "NEXT FEW WEEKS",
            color = TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = arrow, color = color, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Trending ${f.direction.lowercase()}",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "≈ $pctText per week at the recent pace  ·  ${f.confidence.lowercase()} confidence",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ProjectionRow(p: ForecastPoint, currentValue: Int) {
    val delta = p.value - currentValue
    val deltaColor = when {
        delta > 0 -> TrendUp
        delta < 0 -> TrendDown
        else -> TrendFlat
    }
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "In ${p.label}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "range ₹${formatINR(p.low)} – ₹${formatINR(p.high)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹" + formatINR(p.value),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = (if (delta > 0) "+₹" else if (delta < 0) "-₹" else "₹") + formatINR(abs(delta)),
                    color = deltaColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DisclaimerCard(f: Forecast) {
    Text(
        text = "This is a statistical projection of the recent price trend (fit quality " +
            "R²=${String.format(java.util.Locale.US, "%.2f", f.rSquared)}), not a guarantee or " +
            "financial advice. Gold prices are volatile and can move sharply on global events. " +
            "Use it as a rough guide only.",
        color = TextTertiary,
        fontSize = 12.sp,
    )
}

@Composable
private fun ForecastUnavailable(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().height(320.dp),
    ) {
        Text(
            text = "Not enough recent data to project a trend.\nCheck your connection and try again.",
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
                .background(com.techrush_app.goldrate.ui.theme.GoldSoft)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ForecastChart(forecast: Forecast, modifier: Modifier = Modifier) {
    val actual = forecast.series.mapNotNull { p -> epochDay(p.date)?.let { it to p.rate } }
    if (actual.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Not enough data to chart.", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    val density = LocalDensity.current
    val axisTextPx = with(density) { 11.sp.toPx() }
    val lineColor = Gold
    val projColor = directionColor(forecast.direction)
    val axisColor = TextTertiary
    val gridColor = CardBorder

    val slope = forecast.slopePerDay
    val s = forecast.residualStd
    val lastValue = forecast.lastValue
    val lastDay = forecast.lastDay
    val horizon = 28

    val firstDay = actual.first().first
    val xMinDay = firstDay
    val xMaxDay = lastDay + horizon

    // Projection samples (center + band) across the horizon.
    val proj = (0..horizon).map { h ->
        val v = lastValue + slope * h
        val half = 1.28 * s * sqrt(h / 7.0)
        Triple((lastDay + h), v, half)
    }

    val actualMax = actual.maxOf { it.second }.toDouble()
    val actualMin = actual.minOf { it.second }.toDouble()
    val projMax = proj.maxOf { it.second + it.third }
    val projMin = proj.minOf { it.second - it.third }
    val maxV = max(actualMax, projMax)
    val minV = minOf(actualMin, projMin)
    val range = (maxV - minV).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val padLeft = 46.dp.toPx()
        val padRight = 12.dp.toPx()
        val padTop = 14.dp.toPx()
        val padBottom = 26.dp.toPx()
        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom
        val daySpan = (xMaxDay - xMinDay).toDouble().coerceAtLeast(1.0)

        fun xAt(day: Long) = padLeft + (chartW * (day - xMinDay) / daySpan).toFloat()
        fun yAt(v: Double) = padTop + (1f - ((v - minV) / range).toFloat()) * chartH

        val axisPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = axisColor.toArgb()
            textSize = axisTextPx
        }

        // Y gridlines + rupee labels.
        val steps = 4
        axisPaint.textAlign = android.graphics.Paint.Align.RIGHT
        for (i in 0..steps) {
            val y = padTop + chartH * i / steps
            val v = maxV - range * i / steps
            drawLine(gridColor, Offset(padLeft, y), Offset(size.width - padRight, y), 1f)
            drawContext.canvas.nativeCanvas.drawText(
                formatINR(v.roundToInt()), padLeft - 6.dp.toPx(), y + axisTextPx / 3f, axisPaint,
            )
        }

        // X labels: first, today, +4wk.
        axisPaint.textAlign = android.graphics.Paint.Align.CENTER
        for (day in listOf(xMinDay, lastDay, xMaxDay)) {
            drawContext.canvas.nativeCanvas.drawText(
                formatEpochDay(day), xAt(day), size.height - 6.dp.toPx(), axisPaint,
            )
        }

        // Uncertainty band (cone) over the projection.
        val band = Path()
        proj.forEachIndexed { i, (d, v, half) ->
            val x = xAt(d)
            val y = yAt(v + half)
            if (i == 0) band.moveTo(x, y) else band.lineTo(x, y)
        }
        for (i in proj.indices.reversed()) {
            val (d, v, half) = proj[i]
            band.lineTo(xAt(d), yAt(v - half))
        }
        band.close()
        drawPath(band, color = projColor.copy(alpha = 0.14f))

        // Actual line.
        val line = Path()
        actual.forEachIndexed { i, (d, v) ->
            val x = xAt(d)
            val y = yAt(v.toDouble())
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        drawPath(
            line, color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Projection center line (dashed).
        val projLine = Path().apply {
            moveTo(xAt(lastDay), yAt(lastValue.toDouble()))
            lineTo(xAt(xMaxDay), yAt(lastValue + slope * horizon))
        }
        drawPath(
            projLine, color = projColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            ),
        )

        // "Today" divider + marker.
        drawLine(
            color = axisColor,
            start = Offset(xAt(lastDay), padTop),
            end = Offset(xAt(lastDay), padTop + chartH),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
        )
        drawCircle(lineColor, 4.5.dp.toPx(), Offset(xAt(lastDay), yAt(lastValue.toDouble())))
        drawCircle(Color.White, 2.dp.toPx(), Offset(xAt(lastDay), yAt(lastValue.toDouble())))
    }
}
