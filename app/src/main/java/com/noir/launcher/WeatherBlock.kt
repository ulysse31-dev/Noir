package com.noir.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

private val COL_WIDTH = 52.dp
private val CHART_HEIGHT = 124.dp

/**
 * Résumé en une phrase + courbe de température, barres de pluie et heures (défile à l'horizontale).
 * La courbe se dessine à chaque arrivée sur l'accueil ([playKey]).
 */
@Composable
fun WeatherBlock(points: List<HourPoint>, playKey: Int) {
    Column {
        BasicText(
            text = summary(points),
            style = NoirType.WeatherSummary,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .riseIn(delayMillis = 220, key = playKey),
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                // Fondu à droite : indique que ça défile.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.86f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .horizontalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.width(14.dp))
            WeatherChart(points, playKey)
            Spacer(Modifier.width(28.dp))
        }
    }
}

@Composable
private fun WeatherChart(points: List<HourPoint>, playKey: Int) {
    val measurer = rememberTextMeasurer()
    val tempStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = NoirColors.White)
    }
    val hourStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = NoirColors.Muted)
    }
    val nowStyle = remember {
        TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = NoirColors.White)
    }
    val nowLabel = stringResource(R.string.weather_now)
    val tMin = points.minOf { it.temp }
    val tMax = points.maxOf { it.temp }

    // 0 -> 1 : la courbe se trace, puis les barres poussent.
    val progress = remember(playKey) { Animatable(0f) }
    LaunchedEffect(playKey) {
        progress.animateTo(1f, tween(1600, delayMillis = 350, easing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)))
    }
    // Anneau qui pulse sur « maintenant » : quelques fois seulement, puis repos (économie d'énergie).
    val pulse = remember(playKey) { Animatable(1f) }
    LaunchedEffect(playKey) {
        delay(1800)
        repeat(3) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }

    Canvas(
        Modifier
            .width(COL_WIDTH * points.size)
            .height(CHART_HEIGHT),
    ) {
        val col = COL_WIDTH.toPx()
        val curveTop = 30.dp.toPx()
        val curveBottom = 62.dp.toPx()
        val rainBase = 100.dp.toPx()
        val rainMax = 26.dp.toPx()
        val hourY = 106.dp.toPx()
        val range = max(tMax - tMin, 1.0).toFloat()
        val p = progress.value
        val pulseV = pulse.value

        fun cx(i: Int): Float = col * (i + 0.5f)
        fun cy(t: Double): Float =
            curveBottom - ((t - tMin).toFloat() / range) * (curveBottom - curveTop)

        fun label(text: String, style: TextStyle, centerX: Float, top: Float, alpha: Float = 1f) {
            val layout = measurer.measure(text, style)
            drawText(layout, topLeft = Offset(centerX - layout.size.width / 2f, top), alpha = alpha)
        }

        // Séparation à minuit
        points.forEachIndexed { i, pt ->
            if (i > 0 && pt.time.hour == 0) {
                drawLine(
                    color = NoirColors.Hairline,
                    start = Offset(col * i, 0f),
                    end = Offset(col * i, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        // Courbe de température, tracée progressivement
        val path = Path()
        points.forEachIndexed { i, pt ->
            if (i == 0) {
                path.moveTo(cx(0), cy(pt.temp))
            } else {
                val x0 = cx(i - 1)
                val y0 = cy(points[i - 1].temp)
                val x1 = cx(i)
                val y1 = cy(pt.temp)
                val mx = (x0 + x1) / 2f
                path.cubicTo(mx, y0, mx, y1, x1, y1)
            }
        }
        val measure = PathMeasure().also { it.setPath(path, false) }
        val shown = Path()
        measure.getSegment(0f, measure.length * p, shown, true)
        drawPath(
            path = shown,
            color = NoirColors.White,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Barres de pluie (poussent après la courbe), points, températures toutes les 3 h, heures
        val rainP = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
        points.forEachIndexed { i, pt ->
            val x = cx(i)
            val reveal = (p * points.size - i).coerceIn(0f, 1f)

            val grow = (rainP * 1.6f - i.toFloat() / points.size * 0.6f).coerceIn(0f, 1f)
            val h = rainMax * (pt.rainProb.coerceIn(0, 100) / 100f) * grow
            if (h > 1f) {
                drawRoundRect(
                    color = NoirColors.White.copy(alpha = 0.3f),
                    topLeft = Offset(x - 6.dp.toPx(), rainBase - h),
                    size = Size(12.dp.toPx(), h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }

            if (i % 3 == 0) {
                val y = cy(pt.temp)
                drawCircle(
                    color = NoirColors.White.copy(alpha = reveal),
                    radius = 3.dp.toPx() * (0.4f + 0.6f * reveal),
                    center = Offset(x, y),
                )
                label("${pt.temp.roundToInt()}°", tempStyle, x, y - 26.dp.toPx(), alpha = reveal)
            }

            if (i == 0) label(nowLabel, nowStyle, x, hourY) else label("${pt.time.hour} h", hourStyle, x, hourY)
        }

        // Anneau « maintenant »
        val y0 = cy(points[0].temp)
        drawCircle(
            color = NoirColors.White.copy(alpha = (1f - pulseV) * 0.8f * p),
            radius = (4f + 14f * pulseV).dp.toPx(),
            center = Offset(cx(0), y0),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
