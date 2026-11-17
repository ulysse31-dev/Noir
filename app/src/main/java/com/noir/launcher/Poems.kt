package com.noir.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Poèmes à apprendre. Seuls le titre et l'auteur sont écrits ici : le texte est chargé depuis
 * Wikisource (bibliothèque libre, textes du domaine public), puis gardé sur le téléphone.
 * Pour ajouter un poème : une ligne dans [POEMS]. [PoemRef.page] est facultatif : le titre exact
 * de la page Wikisource, utilisé en premier s'il est donné (sinon une recherche la trouve).
 */
data class PoemRef(
    val id: String,
    val title: String,
    val author: String,
    val page: String? = null,
)

val POEMS: List<PoemRef> = listOf(
    PoemRef("chanson-automne", "Chanson d'automne", "Paul Verlaine"),
    PoemRef("dormeur", "Le Dormeur du val", "Arthur Rimbaud"),
    PoemRef("albatros", "L'Albatros", "Charles Baudelaire"),
    PoemRef("mirabeau", "Le Pont Mirabeau", "Guillaume Apollinaire", page = "Alcools/Le Pont Mirabeau"),
    PoemRef("demain", "Demain, dès l'aube", "Victor Hugo"),
    PoemRef("sensation", "Sensation", "Arthur Rimbaud"),
    PoemRef("pleure", "Il pleure dans mon cœur", "Paul Verlaine"),
    PoemRef("ulysse", "Heureux qui, comme Ulysse", "Joachim du Bellay"),
    PoemRef("harmonie", "Harmonie du soir", "Charles Baudelaire"),
    PoemRef("mignonne", "Mignonne, allons voir si la rose", "Pierre de Ronsard"),
    PoemRef("boheme", "Ma Bohème", "Arthur Rimbaud"),
    PoemRef("recueillement", "Recueillement", "Charles Baudelaire"),
    PoemRef("brise", "Brise marine", "Stéphane Mallarmé"),
    PoemRef("desdichado", "El Desdichado", "Gérard de Nerval"),
    PoemRef("voyelles", "Voyelles", "Arthur Rimbaud"),
    PoemRef("passante", "À une passante", "Charles Baudelaire"),
    PoemRef("corbeau", "Le Corbeau et le Renard", "Jean de La Fontaine"),
    PoemRef("cigale", "La Cigale et la Fourmi", "Jean de La Fontaine"),
    PoemRef("invitation", "L'Invitation au voyage", "Charles Baudelaire"),
)

private const val KEY_LEARNED = "poem_learned"
private const val KEY_BAD = "poem_bad" // ancien réglage : on l'efface, un échec n'est plus définitif
private const val KEY_CURSOR = "poem_cursor"
private const val AGENT = "Noir/1.0 (Android launcher; https://github.com/ulysse31-dev/Noir)"
private const val API = "https://fr.wikisource.org/w/api.php"

/** Poèmes appris, poème en cours, textes gardés sur le téléphone. */
class PoemStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("noir", Context.MODE_PRIVATE)

    /** Poèmes dont le chargement a échoué : écartés jusqu'à la prochaine ouverture, jamais définitivement. */
    private val skipped = mutableSetOf<String>()

    init {
        // Une ancienne version marquait les poèmes non chargés comme « introuvables » pour toujours.
        prefs.edit().remove(KEY_BAD).apply()
    }

    fun learned(): Set<String> = prefs.getStringSet(KEY_LEARNED, emptySet()).orEmpty().toSet()
    private fun bad(): Set<String> = skipped

    fun markLearned(id: String) {
        prefs.edit().putStringSet(KEY_LEARNED, learned() + id).apply()
    }

    fun markBad(id: String) {
        skipped += id
    }

    /** Remet en jeu les poèmes écartés (bouton « Réessayer »). */
    fun clearSkipped() {
        skipped.clear()
    }

    /** Tous les poèmes ont été cochés. */
    fun allLearned(): Boolean = POEMS.all { it.id in learned() }

    fun reset() {
        prefs.edit().putStringSet(KEY_LEARNED, emptySet()).putInt(KEY_CURSOR, 0).apply()
    }

    fun setCursor(index: Int) {
        prefs.edit().putInt(KEY_CURSOR, index).apply()
    }

    fun cached(id: String): String? = prefs.getString("poem_$id", null)

    fun cache(id: String, text: String) {
        prefs.edit().putString("poem_$id", text).apply()
    }

    /** Poèmes encore à apprendre (hors ceux introuvables). */
    fun remaining(): Int {
        val l = learned()
        val b = bad()
        return POEMS.count { it.id !in l && it.id !in b }
    }

    /** Poèmes disponibles (hors introuvables), pour afficher « 3 / 19 ». */
    fun total(): Int = POEMS.count { it.id !in bad() }

    /** Prochain poème à apprendre après [from] (en bouclant), ou null s'il n'en reste aucun. */
    fun next(from: Int): Int? {
        val l = learned()
        val b = bad()
        val n = POEMS.size
        for (k in 1..n) {
            val i = (from + k) % n
            val id = POEMS[i].id
            if (id !in l && id !in b) return i
        }
        return null
    }

    /** Poème en cours : celui mémorisé, ou le suivant s'il est déjà appris. */
    fun current(): Int? {
        val n = POEMS.size
        val c = prefs.getInt(KEY_CURSOR, 0).coerceIn(0, n - 1)
        val id = POEMS[c].id
        return if (id !in learned() && id !in bad()) c else next(c)
    }
}

sealed interface PoemResult {
    data class Ok(val text: String) : PoemResult
    /** Aucun texte exploitable trouvé : on passe au suivant (le détail sert à comprendre l'échec). */
    data class Missing(val reason: String) : PoemResult
    /** Pas de connexion ou réponse refusée : on réessaiera. */
    data class Offline(val reason: String) : PoemResult
}

// ---------- Wikisource ----------

private fun get(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    return try {
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("User-Agent", AGENT)
        conn.setRequestProperty("Accept", "application/json")
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Lettres et chiffres seuls, sans accents ni casse : pour comparer des titres. */
private fun key(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "")

private fun searchTitles(query: String): List<String> {
    val url = "$API?action=query&format=json&list=search&srnamespace=0&srlimit=15&srsearch=${enc(query)}"
    val arr = JSONObject(get(url)).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("title")?.takeIf { t -> t.isNotBlank() } }
}

private fun wikitext(page: String): String? {
    val url = "$API?action=query&format=json&formatversion=2&prop=revisions&rvprop=content" +
        "&rvslots=main&redirects=1&titles=${enc(page)}"
    return try {
        val pages = JSONObject(get(url)).optJSONObject("query")?.optJSONArray("pages") ?: return null
        pages.optJSONObject(0)?.optJSONArray("revisions")?.optJSONObject(0)
            ?.optJSONObject("slots")?.optJSONObject("main")?.optString("content")
            ?.takeIf { it.isNotBlank() }
    } catch (e: FileNotFoundException) {
        null
    }
}

private val POEM_BLOCK = Regex("<poem[^>]*>(.*?)</poem>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val POEM_DIV = Regex("<div[^>]*class=\"[^\"]*\\bpoem\\b[^\"]*\"[^>]*>(.*?)</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

private fun decodeEntities(s: String): String =
    s.replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.let { c -> String(Character.toChars(c)) } ?: "" }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toIntOrNull(16)?.let { c -> String(Character.toChars(c)) } ?: "" }
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

/** Nettoyage commun : lignes propres, sans numéros romains seuls, taille raisonnable. */
private fun finishPoem(raw: String): String? {
    val lines = raw.lines()
        .map { it.replace('\u00A0', ' ').trim().trimStart(':').trim() }
        .filterNot { it.matches(Regex("^[IVXLC]+\\.?$")) }
    val text = lines.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    val nonEmpty = text.lines().count { it.isNotBlank() }
    return if (nonEmpty in 4..120 && text.length <= 6000) text else null
}

/** Extrait le poème du HTML rendu par Wikisource (blocs « poem »). */
internal fun extractPoemHtml(html: String): String? {
    val blocks = POEM_DIV.findAll(html).map { it.groupValues[1] }.toList()
    if (blocks.isEmpty()) return null
    var t = blocks.joinToString("\n\n")
    t = t.replace(Regex("<sup[^>]*>.*?</sup>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
    t = t.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    t = t.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
    t = t.replace(Regex("<[^>]+>"), "")
    return finishPoem(decodeEntities(t))
}

/** Extrait le texte du poème d'une page Wikisource (balises <poem>), nettoyé de la mise en forme wiki. */
internal fun extractPoem(wikitext: String): String? {
    val blocks = POEM_BLOCK.findAll(wikitext).map { it.groupValues[1] }.toList()
    if (blocks.isEmpty()) return null
    var t = blocks.joinToString("\n\n")
    t = t.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    t = t.replace(Regex("<ref[^>]*?/>"), "")
    t = t.replace(Regex("<ref[^>]*>.*?</ref>", RegexOption.DOT_MATCHES_ALL), "")
    repeat(5) { t = t.replace(Regex("\\{\\{[^{}]*\\}\\}"), "") }
    t = t.replace(Regex("\\[\\[(?:Fichier|File|Image)[^\\]]*\\]\\]", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]"), "$1")
    t = t.replace(Regex("\\[\\[([^\\]]*)\\]\\]"), "$1")
    t = t.replace("'''", "").replace("''", "")
    t = t.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    t = t.replace(Regex("<[^>]+>"), "")
    return finishPoem(decodeEntities(t))
}

/** Page rendue (HTML) : traite les modèles et les balises poem comme le site. */
private fun renderedHtml(page: String): String? {
    val url = "$API?action=parse&format=json&formatversion=2&prop=text&redirects=1&disablelimitreport=1&page=${enc(page)}"
    return try {
        val o = JSONObject(get(url))
        if (o.has("error")) null else o.optJSONObject("parse")?.optString("text")?.takeIf { it.isNotBlank() }
    } catch (e: FileNotFoundException) {
        null
    }
}

/** Charge le texte d'un poème depuis Wikisource. */
suspend fun fetchPoem(ref: PoemRef): PoemResult = withContext(Dispatchers.IO) {
    var found = 0
    var tried = 0
    try {
        val wanted = key(ref.title)
        val candidates = mutableListOf<String>()
        ref.page?.let { candidates += it }
        val raw = mutableListOf<String>()
        for (q in listOf("${ref.title} ${ref.author}", ref.title)) {
            val titles = searchTitles(q)
            found += titles.size
            if (raw.isEmpty()) raw += titles
            // Les pages de poème s'appellent « Recueil/Titre » : on compare la dernière partie.
            val exact = titles.filter { key(it.substringAfterLast('/')) == wanted }
            val partial = titles.filter { key(it.substringAfterLast('/')).contains(wanted) }
            candidates += (exact + partial).distinct()
            if (candidates.size >= 3) break
        }
        // En dernier recours : les premiers résultats de la recherche, tels quels.
        candidates += raw.take(3)
        for (page in candidates.distinct().take(8)) {
            tried++
            val text = renderedHtml(page)?.let { extractPoemHtml(it) }
                ?: wikitext(page)?.let { extractPoem(it) }
            if (text != null) return@withContext PoemResult.Ok(text)
        }
        PoemResult.Missing("recherche : $found résultats, $tried pages lues, aucun poème reconnu")
    } catch (e: IOException) {
        PoemResult.Offline(e.message ?: e.javaClass.simpleName)
    } catch (e: org.json.JSONException) {
        PoemResult.Missing("réponse illisible de Wikisource (${e.message?.take(60)})")
    }
}
