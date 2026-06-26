package com.techrush_app.goldrate

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp

/**
 * A minimal line chart of the month's rates with a soft gradient fill and a
 * marker on the latest point.
 */
@Composable
fun Sparkline(
    points: List<Int>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val maxV = points.max()
        val minV = points.min()
        val range = (maxV - minV).coerceAtLeast(1).toFloat()

        // Inset vertically so peaks/troughs and the marker aren't clipped.
        val vPad = size.height * 0.12f
        val usableH = size.height - vPad * 2f
        val stepX = size.width / (points.size - 1)

        fun xAt(i: Int) = stepX * i
        fun yAt(v: Int) = vPad + (1f - (v - minV) / range) * usableH

        val line = Path()
        val fill = Path()
        points.forEachIndexed { i, v ->
            val x = xAt(i)
            val y = yAt(v)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(xAt(points.size - 1), size.height)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
            ),
        )
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Marker on the latest reading.
        val lastX = xAt(points.size - 1)
        val lastY = yAt(points.last())
        drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
    }
}
