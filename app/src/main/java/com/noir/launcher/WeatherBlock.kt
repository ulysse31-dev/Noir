package com.noir.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

private val COL_WIDTH = 52.dp
private val CHART_HEIGHT = 176.dp

/** Résumé en une phrase + courbe de température, barres de pluie, heure et vent (défile à l'horizontale). */
@Composable
fun WeatherBlock(points: List<HourPoint>) {
    Column {
        BasicText(
            text = summary(points),
            style = NoirType.WeatherSummary,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Spacer(Modifier.width(2.dp))
            WeatherChart(points)
            Spacer(Modifier.width(28.dp))
        }
        BasicText(
            text = stringResource(R.string.weather_legend),
            style = NoirType.Legend,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun WeatherChart(points: List<HourPoint>) {
    val measurer = rememberTextMeasurer()
    val tempStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Light, fontSize = 14.sp, color = NoirColors.White)
    }
    val hourStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = NoirColors.White)
    }
    val rainStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = NoirColors.Muted)
    }
    val windStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Light, fontSize = 12.sp, color = NoirColors.Muted)
    }
    val tMin = points.minOf { it.temp }
    val tMax = points.maxOf { it.temp }

    Canvas(
        Modifier
            .width(COL_WIDTH * points.size)
            .height(CHART_HEIGHT),
    ) {
        val col = COL_WIDTH.toPx()
        val curveTop = 30.dp.toPx()
        val curveBottom = 76.dp.toPx()
        val rainBase = 132.dp.toPx()
        val rainMax = 30.dp.toPx()
        val hourY = 140.dp.toPx()
        val windY = 160.dp.toPx()
        val range = max(tMax - tMin, 1.0).toFloat()

        fun cx(i: Int): Float = col * (i + 0.5f)
        fun cy(t: Double): Float =
            curveBottom - ((t - tMin).toFloat() / range) * (curveBottom - curveTop)

        fun label(text: String, style: TextStyle, centerX: Float, top: Float) {
            val layout = measurer.measure(text, style)
            drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, top))
        }

        // Séparation à minuit
        points.forEachIndexed { i, p ->
            if (i > 0 && p.time.hour == 0) {
                drawLine(
                    color = NoirColors.Hairline,
                    start = Offset(col * i, 0f),
                    end = Offset(col * i, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        // Courbe de température
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) {
                path.moveTo(cx(0), cy(p.temp))
            } else {
                val x0 = cx(i - 1)
                val y0 = cy(points[i - 1].temp)
                val x1 = cx(i)
                val y1 = cy(p.temp)
                val mx = (x0 + x1) / 2f
                path.cubicTo(mx, y0, mx, y1, x1, y1)
            }
        }
        drawPath(
            path = path,
            color = NoirColors.White,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Points, barres de pluie, étiquettes
        points.forEachIndexed { i, p ->
            val x = cx(i)
            val h = rainMax * (p.rainProb.coerceIn(0, 100) / 100f)
            if (h > 0f) {
                drawRect(
                    color = NoirColors.White.copy(alpha = 0.3f),
                    topLeft = Offset(x - 7.dp.toPx(), rainBase - h),
                    size = Size(14.dp.toPx(), h),
                )
            }
            if (p.rainProb >= 20) label("${p.rainProb}%", rainStyle, x, rainBase - h - 15.dp.toPx())
            drawCircle(color = NoirColors.White, radius = 2.5.dp.toPx(), center = Offset(x, cy(p.temp)))
            label("${p.temp.roundToInt()}°", tempStyle, x, cy(p.temp) - 24.dp.toPx())
            label("${p.time.hour}h", hourStyle, x, hourY)
            label("${p.wind.roundToInt()}", windStyle, x, windY)
        }
    }
}
