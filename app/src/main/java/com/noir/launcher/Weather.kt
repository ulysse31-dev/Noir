package com.noir.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

// Lieu de la météo (Allauch). Données : Open-Meteo, gratuit et sans compte.
const val WEATHER_LAT = 43.3358
const val WEATHER_LON = 5.4814
private const val WEATHER_ZONE = "Europe/Paris"
private const val REFRESH_MS = 30 * 60 * 1000L
private const val KEY_JSON = "weather_json"
private const val KEY_TS = "weather_ts"

data class HourPoint(
    val time: LocalDateTime,
    val temp: Double,
    val rainProb: Int,
    val rainMm: Double,
    val wind: Double,
    val code: Int,
)

fun parseWeather(json: String): List<HourPoint> {
    val h = JSONObject(json).getJSONObject("hourly")
    val times = h.getJSONArray("time")
    val temp = h.getJSONArray("temperature_2m")
    val prob = h.getJSONArray("precipitation_probability")
    val mm = h.getJSONArray("precipitation")
    val wind = h.getJSONArray("wind_speed_10m")
    val code = h.getJSONArray("weather_code")
    return (0 until times.length()).map { i ->
        HourPoint(
            time = LocalDateTime.parse(times.getString(i)),
            temp = temp.optDouble(i, 0.0),
            rainProb = prob.optInt(i, 0),
            rainMm = mm.optDouble(i, 0.0),
            wind = wind.optDouble(i, 0.0),
            code = code.optInt(i, 0),
        )
    }
}

private fun prefs(context: Context) =
    context.getSharedPreferences("noir", Context.MODE_PRIVATE)

fun cachedWeather(context: Context): List<HourPoint>? {
    val raw = prefs(context).getString(KEY_JSON, null) ?: return null
    return runCatching { parseWeather(raw) }.getOrNull()
}

private fun fetchRaw(): String {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=$WEATHER_LAT&longitude=$WEATHER_LON" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,wind_speed_10m,weather_code" +
            "&wind_speed_unit=kmh&timezone=Europe%2FParis&forecast_days=2",
    )
    val c = url.openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    try {
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

/** Prévisions en cache si elles ont moins de 30 minutes, sinon téléchargement (cache gardé en cas d'échec). */
suspend fun loadWeather(context: Context): List<HourPoint>? = withContext(Dispatchers.IO) {
    val p = prefs(context)
    val cached = cachedWeather(context)
    val age = System.currentTimeMillis() - p.getLong(KEY_TS, 0L)
    if (cached != null && age < REFRESH_MS) return@withContext cached
    val fresh = runCatching {
        val raw = fetchRaw()
        val parsed = parseWeather(raw)
        p.edit().putString(KEY_JSON, raw).putLong(KEY_TS, System.currentTimeMillis()).apply()
        parsed
    }.getOrNull()
    fresh ?: cached
}

/** Les 24 prochaines heures, à partir de l'heure en cours (vide si les données sont trop anciennes). */
fun next24Hours(
    all: List<HourPoint>,
    now: LocalDateTime = LocalDateTime.now(ZoneId.of(WEATHER_ZONE)),
): List<HourPoint> {
    val start = all.indexOfLast { !it.time.isAfter(now) }.coerceAtLeast(0)
    val slice = all.drop(start).take(24)
    return if (slice.size >= 6) slice else emptyList()
}

fun describe(code: Int): String = when (code) {
    0 -> "dégagé"
    1, 2 -> "peu nuageux"
    3 -> "couvert"
    45, 48 -> "brouillard"
    in 51..57 -> "bruine"
    in 61..67 -> "pluie"
    in 71..77 -> "neige"
    in 80..82 -> "averses"
    85, 86 -> "averses de neige"
    95, 96, 99 -> "orage"
    else -> ""
}

fun summary(points: List<HourPoint>): String {
    val first = points.first()
    val wet = points.firstOrNull { it.rainProb >= 50 || it.rainMm >= 0.3 }
    val rain = when {
        wet == null -> "pas de pluie prévue"
        wet === first -> "pluie en cours"
        else -> "pluie vers ${wet.time.hour} h"
    }
    return listOf("${first.temp.roundToInt()}°", describe(first.code), rain)
        .filter { it.isNotBlank() }
        .joinToString("  ·  ")
}
