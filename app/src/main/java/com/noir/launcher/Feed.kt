package com.noir.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * Page « Lire » : tout vient de Wikipédia en français (API publique, sans compte).
 *  - À la une : l'article du jour.
 *  - Ce jour-là : événements de la date du jour.
 *  - Plus lus : les articles les plus consultés.
 *  - Thèmes (Marseille, Photo, Web, IA) : des articles tirés d'une recherche, qui change chaque jour.
 * Pour changer ou ajouter un thème, modifier [TOPIC_QUERIES].
 */
data class WikiItem(
    val section: String,
    val meta: String,
    val title: String,
    val text: String,
    val link: String,
)

data class Fact(val title: String, val text: String, val link: String?)

data class WikiData(val fact: Fact?, val items: List<WikiItem>, val day: String, val ts: Long)

const val SEC_TODAY = "Ce jour-là"
const val SEC_READ = "Plus lus"

/** Thème affiché -> recherche Wikipédia. */
private val TOPIC_QUERIES = linkedMapOf(
    "Marseille" to "Marseille",
    "Photo" to "photographie",
    "Web" to "développement web",
    "IA" to "intelligence artificielle",
)

val SECTIONS: List<String> = listOf(SEC_TODAY, SEC_READ) + TOPIC_QUERIES.keys

private const val REFRESH_MS = 6 * 60 * 60 * 1000L
private const val KEY_WIKI = "wiki_json"
private const val AGENT = "Noir/1.0 (Android launcher; https://github.com/ulysse31-dev/Noir)"
private val NAMESPACES = Regex("^(Spécial|Wikipédia|Portail|Fichier|Catégorie|Aide|Projet|Modèle|Utilisateur|Discussion|Wikipedia|Special|Help|Category|File|Portal)\\s?:")

private fun prefs(context: Context) =
    context.getSharedPreferences("noir", Context.MODE_PRIVATE)

private fun httpGet(address: String): String {
    val conn = URL(address).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", AGENT)
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Accept-Language", "fr")
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

private fun shorten(text: String, max: Int): String {
    val t = text.trim().replace(Regex("\\s+"), " ")
    if (t.length <= max) return t
    val cut = t.substring(0, max)
    val end = cut.lastIndexOf(". ")
    return if (end > max / 3) cut.substring(0, end + 1) else cut.substringBeforeLast(' ') + "…"
}

private fun pageLink(o: JSONObject): String? =
    o.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page")?.takeIf { it.isNotBlank() }

private fun pageTitle(o: JSONObject): String =
    o.optJSONObject("titles")?.optString("normalized")?.takeIf { it.isNotBlank() }
        ?: o.optString("title").replace('_', ' ')

private fun wikiLink(title: String): String =
    "https://fr.m.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")

// ---------- À la une, ce jour-là, plus lus ----------

private fun fetchFeatured(day: LocalDate): JSONObject {
    val url = "https://fr.wikipedia.org/api/rest_v1/feed/featured/" +
        "%04d/%02d/%02d".format(day.year, day.monthValue, day.dayOfMonth)
    return JSONObject(httpGet(url))
}

private fun parseFeatured(feed: JSONObject): Triple<Fact?, List<WikiItem>, List<WikiItem>> {
    val fact = feed.optJSONObject("tfa")?.let { t ->
        val extract = t.optString("extract")
        if (extract.isBlank()) null else Fact(pageTitle(t), shorten(extract, 230), pageLink(t))
    }

    val today = mutableListOf<WikiItem>()
    val events = feed.optJSONArray("onthisday")
    if (events != null) {
        val rows = (0 until events.length()).mapNotNull { i ->
            val e = events.optJSONObject(i) ?: return@mapNotNull null
            val text = e.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = e.optJSONArray("pages")?.optJSONObject(0)?.let { pageLink(it) } ?: return@mapNotNull null
            Triple(e.optInt("year", 0), text, link)
        }.sortedByDescending { it.first }.take(12)
        rows.forEach { (year, text, link) ->
            today += WikiItem(SEC_TODAY, if (year != 0) year.toString() else "", shorten(text, 200), "", link)
        }
    }

    val read = mutableListOf<WikiItem>()
    val articles = feed.optJSONObject("mostread")?.optJSONArray("articles")
    if (articles != null) {
        val number = NumberFormat.getIntegerInstance(Locale.FRANCE)
        for (i in 0 until articles.length()) {
            val a = articles.optJSONObject(i) ?: continue
            val title = pageTitle(a)
            if (NAMESPACES.containsMatchIn(title) || title == "Accueil") continue
            val link = pageLink(a) ?: wikiLink(title)
            val views = a.optInt("views", 0)
            val desc = a.optString("description").replaceFirstChar { it.titlecase(Locale.FRANCE) }
            read += WikiItem(SEC_READ, if (views > 0) number.format(views) + " lectures" else "", title, desc, link)
            if (read.size >= 10) break
        }
    }
    return Triple(fact, today, read)
}

// ---------- Thèmes ----------

private fun fetchTopic(section: String, query: String): List<WikiItem> {
    val offset = (LocalDate.now().dayOfYear % 8) * 6
    val url = "https://fr.wikipedia.org/w/api.php?action=query&format=json&generator=search" +
        "&gsrsearch=" + URLEncoder.encode(query, "UTF-8") +
        "&gsrnamespace=0&gsrlimit=6&gsroffset=$offset" +
        "&prop=extracts&exintro=1&explaintext=1&exsentences=2&exlimit=max&redirects=1"
    val pages = JSONObject(httpGet(url)).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
    val rows = pages.keys().asSequence().mapNotNull { pages.optJSONObject(it) }.sortedBy { it.optInt("index", 99) }
    return rows.mapNotNull { p ->
        val title = p.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val extract = p.optString("extract")
        if (extract.isBlank() || extract.contains("peut désigner")) return@mapNotNull null
        WikiItem(section, "", title, shorten(extract, 170), wikiLink(title))
    }.toList()
}

// ---------- Cache et chargement ----------

private fun toJson(d: WikiData): String {
    val items = JSONArray()
    d.items.forEach {
        items.put(
            JSONObject().put("section", it.section).put("meta", it.meta)
                .put("title", it.title).put("text", it.text).put("link", it.link),
        )
    }
    val o = JSONObject().put("day", d.day).put("ts", d.ts).put("items", items)
    d.fact?.let { o.put("fact", JSONObject().put("title", it.title).put("text", it.text).put("link", it.link ?: "")) }
    return o.toString()
}

private fun fromJson(raw: String): WikiData {
    val o = JSONObject(raw)
    val arr = o.getJSONArray("items")
    val items = (0 until arr.length()).map {
        val i = arr.getJSONObject(it)
        WikiItem(i.getString("section"), i.getString("meta"), i.getString("title"), i.getString("text"), i.getString("link"))
    }
    val fact = o.optJSONObject("fact")?.let {
        Fact(it.getString("title"), it.getString("text"), it.optString("link").takeIf { l -> l.isNotBlank() })
    }
    return WikiData(fact, items, o.optString("day"), o.optLong("ts"))
}

fun cachedWiki(context: Context): WikiData? {
    val raw = prefs(context).getString(KEY_WIKI, null) ?: return null
    return runCatching { fromJson(raw) }.getOrNull()
}

/** Données à jour, ou null si le cache est encore bon ou si tout a échoué. */
suspend fun loadWiki(context: Context): WikiData? = withContext(Dispatchers.IO) {
    val today = LocalDate.now()
    val old = cachedWiki(context)
    if (old != null && old.day == today.toString() && System.currentTimeMillis() - old.ts < REFRESH_MS) {
        return@withContext null
    }

    coroutineScope {
        val featuredJob = async {
            runCatching { fetchFeatured(today) }.getOrNull()
                ?: runCatching { fetchFeatured(today.minusDays(1)) }.getOrNull()
        }
        val topicJobs = TOPIC_QUERIES.map { (section, query) ->
            async { runCatching { fetchTopic(section, query) }.getOrDefault(emptyList()) }
        }
        val featured = featuredJob.await()?.let { runCatching { parseFeatured(it) }.getOrNull() }
        val topics = topicJobs.awaitAll().flatten()

        val items = (featured?.second.orEmpty()) + (featured?.third.orEmpty()) + topics
        if (items.isEmpty() && featured?.first == null) return@coroutineScope null

        // Si une partie a échoué, on garde l'ancienne version de ce qui manque.
        val merged = if (old != null) {
            val have = items.map { it.section }.toSet()
            items + old.items.filter { it.section !in have }
        } else items
        val data = WikiData(featured?.first ?: old?.fact, merged, today.toString(), System.currentTimeMillis())
        prefs(context).edit().putString(KEY_WIKI, toJson(data)).apply()
        data
    }
}
