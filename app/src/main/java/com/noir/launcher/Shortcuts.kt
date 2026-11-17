package com.noir.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class Shortcut(
    val id: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val launch: (Context) -> Unit,
)

private fun Context.start(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.startPackage(pkg: String) {
    val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
    start(intent)
}

/**
 * Raccourcis de l'accueil. Messages, téléphone, photo et Maps passent par le type d'appli
 * (et non par un nom de paquet), donc ils suivent tes applis par défaut (Samsung ou Google).
 */
val shortcuts: List<Shortcut> = listOf(
    Shortcut("whatsapp", R.drawable.ic_chat, R.string.sc_whatsapp) { it.startPackage("com.whatsapp") },
    Shortcut("messages", R.drawable.ic_sms, R.string.sc_messages) {
        it.start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
    },
    Shortcut("phone", R.drawable.ic_phone, R.string.sc_phone) { it.start(Intent(Intent.ACTION_DIAL)) },
    Shortcut("maps", R.drawable.ic_map, R.string.sc_maps) {
        it.start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS))
    },
    Shortcut("chrome", R.drawable.ic_web, R.string.sc_chrome) { it.startPackage("com.android.chrome") },
)

/** Affiché dans le coin en bas à droite. */
val cameraShortcut = Shortcut("camera", R.drawable.ic_camera, R.string.sc_camera) {
    it.start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
}

/** Ouvre claude.ai avec la question pré-remplie (l'appli Claude prend le lien si elle le gère). */
fun askClaude(context: Context, question: String) {
    val q = question.trim().removePrefix("?").trim()
    if (q.isEmpty()) return
    context.start(Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/new?q=" + Uri.encode(q))))
}

/** Recherche Google dans le navigateur (ou l'appli Google si elle prend le lien). */
fun searchGoogle(context: Context, query: String) {
    val q = query.trim()
    if (q.isEmpty()) return
    context.start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
}

/** Ouvre un lien dans le navigateur (ou l'appli qui le gère). */
fun openUrl(context: Context, url: String) {
    context.start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
