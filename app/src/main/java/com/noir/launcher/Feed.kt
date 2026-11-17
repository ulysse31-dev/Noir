package com.noir.launcher

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale

/**
 * Page « Lire » : flux RSS par thème + article du jour de Wikipédia.
 * Pour changer les sources, modifier [FEEDS] (une liste d'adresses par thème).
 */
data class Article(
    val topic: String,
    val title: String,
    val source: String,
    val link: String,
    val time: Long,
)

data class Fact(val text: String, val link: String?)

private fun news(query: String): String =
    "https://news.google.com/rss/search?q=" + URLEncoder.encode(query, "UTF-8") +
        "&hl=fr&gl=FR&ceid=FR:fr"

val TOPICS = listOf("WordPress", "Marseille", "Photo", "IA")

private val FEEDS: Map<String, List<String>> = mapOf(
    "WordPress" to listOf(
        "https://wordpress.org/news/feed/",
        news("WordPress Elementor"),
    ),
    "Marseille" to listOf(
        "https://marsactu.fr/feed/",
        news("Marseille"),
    ),
    "Photo" to listOf(
        news("photographie vidéo"),
    ),
    "IA" to listOf(
        news("intelligence artificielle"),
    ),
)

private const val FEED_TTL_MS = 60 * 60 * 1000L
private const val PER_TOPIC = 8
private const val KEY_FEED = "feed_json"
private const val KEY_FEED_TS = "feed_ts"
private const val KEY_FACT_DAY = "fact_day"
private const val KEY_FACT_TEXT = "fact_text"
private const val KEY_FACT_LINK = "fact_link"
private const val AGENT = "Mozilla/5.0 (Android) Noir/1.0 (https://github.com/ulysse31-dev/Noir)"

private fun prefs(context: Context) =
    context.getSharedPreferences("noir", Context.MODE_PRIVATE)

private fun httpGet(address: String): String {
    val conn = URL(address).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", AGENT)
        conn.setRequestProperty("Accept-Language", "fr")
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

// ---------- Articles ----------

private val dateFormats = listOf(
    "EEE, dd MMM yyyy HH:mm:ss Z",
    "EEE, dd MMM yyyy HH:mm:ss zzz",
)

private fun parseDate(raw: String): Long {
    for (f in dateFormats) {
        runCatching { SimpleDateFormat(f, Locale.ENGLISH).parse(raw.trim())?.time }
            .getOrNull()?.let { return it }
    }
    return 0L
}

private fun hostOf(address: String): String =
    runCatching { URI(address).host.orEmpty().removePrefix("www.") }.getOrDefault("")

private fun parseRss(xml: String, topic: String, feedUrl: String): List<Article> {
    val parser = Xml.newPullParser()
    parser.setInput(xml.reader())
    val out = mutableListOf<Article>()
    var inItem = false
    var title = ""
    var link = ""
    var date = ""
    var source = ""
    var tag = ""
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                tag = parser.name
                if (tag == "item") {
                    inItem = true
                    title = ""; link = ""; date = ""; source = ""
                }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) {
                val t = parser.text.orEmpty()
                when (tag) {
                    "title" -> title += t
                    "link" -> link += t
                    "pubDate" -> date += t
                    "source" -> source += t
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "item" && inItem) {
                    inItem = false
                    val cleanTitle = title.trim().let { raw ->
                        // Google Actualités ajoute « - Média » à la fin du titre.
                        if (source.isNotBlank() && raw.endsWith(" - " + source.trim())) {
                            raw.removeSuffix(" - " + source.trim())
                        } else raw
                    }
                    if (cleanTitle.isNotBlank() && link.isNotBlank()) {
                        out += Article(
                            topic = topic,
                            title = cleanTitle,
                            source = source.trim().ifBlank { hostOf(feedUrl) },
                            link = link.trim(),
                            time = parseDate(date),
                        )
                    }
                }
                tag = ""
            }
        }
        event = parser.next()
    }
    return out
}

private fun toJson(list: List<Article>): String = JSONArray().also { arr ->
    list.forEach {
        arr.put(
            JSONObject()
                .put("topic", it.topic).put("title", it.title).put("source", it.source)
                .put("link", it.link).put("time", it.time),
        )
    }
}.toString()

private fun fromJson(raw: String): List<Article> {
    val arr = JSONArray(raw)
    return (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        Article(o.getString("topic"), o.getString("title"), o.getString("source"), o.getString("link"), o.getLong("time"))
    }
}

fun cachedFeed(context: Context): List<Article> {
    val raw = prefs(context).getString(KEY_FEED, null) ?: return emptyList()
    return runCatching { fromJson(raw) }.getOrDefault(emptyList())
}

/** Renvoie la liste à jour, ou null si le cache est encore frais ou si tout a échoué. */
suspend fun loadFeed(context: Context): List<Article>? = withContext(Dispatchers.IO) {
    val last = prefs(context).getLong(KEY_FEED_TS, 0L)
    if (System.currentTimeMillis() - last < FEED_TTL_MS) return@withContext null

    val fetched: List<Article> = coroutineScope {
        FEEDS.flatMap { (topic, urls) -> urls.map { topic to it } }
            .map { (topic, url) ->
                async {
                    runCatching { parseRss(httpGet(url), topic, url).take(12) }.getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
    }
    if (fetched.isEmpty()) return@withContext null

    val merged = TOPICS.flatMap { topic ->
        fetched.filter { it.topic == topic }
            .distinctBy { it.title.lowercase() }
            .sortedByDescending { it.time }
            .take(PER_TOPIC)
    }
    prefs(context).edit()
        .putString(KEY_FEED, toJson(merged))
        .putLong(KEY_FEED_TS, System.currentTimeMillis())
        .apply()
    merged
}

// ---------- Wikipédia du jour ----------

fun cachedFact(context: Context): Fact? {
    val p = prefs(context)
    val text = p.getString(KEY_FACT_TEXT, null) ?: return null
    return Fact(text, p.getString(KEY_FACT_LINK, null))
}

/** Première phrases de l'extrait, sans dépasser ~230 caractères. */
private fun shorten(text: String): String {
    val t = text.trim().replace(Regex("\\s+"), " ")
    if (t.length <= 230) return t
    val cut = t.substring(0, 230)
    val end = cut.lastIndexOf(". ")
    return if (end > 80) cut.substring(0, end + 1) else cut.substringBeforeLast(' ') + "…"
}

private fun parseSummary(o: JSONObject): Fact? {
    val extract = o.optString("extract").takeIf { it.isNotBlank() } ?: return null
    val link = o.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")
    return Fact(shorten(extract), link?.takeIf { it.isNotBlank() })
}

/** Article du jour de Wikipédia en français ; un article au hasard si le flux du jour échoue. Mis en cache pour la journée. */
suspend fun loadFact(context: Context): Fact? = withContext(Dispatchers.IO) {
    val today = LocalDate.now().toString()
    val p = prefs(context)
    if (p.getString(KEY_FACT_DAY, null) == today && p.getString(KEY_FACT_TEXT, null) != null) {
        return@withContext null
    }
    val day = LocalDate.now()
    val featured = runCatching {
        val url = "https://fr.wikipedia.org/api/rest_v1/feed/featured/" +
            "%04d/%02d/%02d".format(day.year, day.monthValue, day.dayOfMonth)
        parseSummary(JSONObject(httpGet(url)).getJSONObject("tfa"))
    }.getOrNull()
    val fact = featured ?: runCatching {
        parseSummary(JSONObject(httpGet("https://fr.wikipedia.org/api/rest_v1/page/random/summary")))
    }.getOrNull()
    fact?.also {
        p.edit()
            .putString(KEY_FACT_DAY, today)
            .putString(KEY_FACT_TEXT, it.text)
            .putString(KEY_FACT_LINK, it.link)
            .apply()
    }
}
