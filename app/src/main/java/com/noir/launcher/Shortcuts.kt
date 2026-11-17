package com.noir.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
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
    Shortcut("spotify", R.drawable.ic_music, R.string.sc_spotify) { it.startPackage("com.spotify.music") },
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

private const val PKG_GEMINI = "com.google.android.apps.bard"

/**
 * Lance Gemini directement : l'appli Gemini d'abord ; à défaut l'assistant du téléphone
 * (touche « assistant »), puis le site gemini.google.com.
 */
fun openGemini(context: Context) {
    val pm = context.packageManager
    val app = pm.getLaunchIntentForPackage(PKG_GEMINI)
    when {
        app != null -> context.start(app)
        Intent(Intent.ACTION_ASSIST).resolveActivity(pm) != null -> context.start(Intent(Intent.ACTION_ASSIST))
        else -> openUrl(context, "https://gemini.google.com/app")
    }
}

private const val PKG_CLOCK = "com.google.android.deskclock"

/** Ouvre l'appli Horloge de Google ; à défaut, l'horloge par défaut du téléphone. */
fun openClock(context: Context) {
    val app = context.packageManager.getLaunchIntentForPackage(PKG_CLOCK)
    context.start(app ?: Intent(AlarmClock.ACTION_SHOW_ALARMS))
}

private const val SECURE_DALTONIZER_ON = "accessibility_display_daltonizer_enabled"
private const val SECURE_DALTONIZER = "accessibility_display_daltonizer"
private const val DALTONIZER_MONOCHROME = 0

/**
 * Bascule l'écran du téléphone en noir et blanc (filtre « monochromie » d'Android).
 * Si l'autorisation WRITE_SECURE_SETTINGS a été donnée une fois (adb), ça bascule d'un tap ;
 * sinon on ouvre les réglages d'accessibilité.
 */
fun toggleGrayscale(context: Context) {
    val granted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
        try {
            val cr = context.contentResolver
            val on = Settings.Secure.getInt(cr, SECURE_DALTONIZER_ON, 0) == 1 &&
                Settings.Secure.getInt(cr, SECURE_DALTONIZER, -1) == DALTONIZER_MONOCHROME
            if (on) {
                Settings.Secure.putInt(cr, SECURE_DALTONIZER_ON, 0)
            } else {
                Settings.Secure.putInt(cr, SECURE_DALTONIZER, DALTONIZER_MONOCHROME)
                Settings.Secure.putInt(cr, SECURE_DALTONIZER_ON, 1)
            }
            return
        } catch (_: SecurityException) {
            // on retombe sur les réglages
        }
    }
    Toast.makeText(context, R.string.grayscale_hint, Toast.LENGTH_LONG).show()
    val opened = runCatching {
        context.startActivity(Intent("android.settings.COLOR_CORRECTION_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) context.start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"

/**
 * Micro : lance l'assistant en mode écoute (reconnaissance vocale directe) plutôt que d'ouvrir
 * simplement l'appli Gemini. On essaie les points d'entrée « voix » d'Android, du plus précis au plus
 * général ; le premier qui répond gagne, et l'ouverture classique de Gemini reste le dernier recours.
 */
fun openGeminiVoice(context: Context) {
    val attempts = listOf(
        Intent(Intent.ACTION_VOICE_COMMAND).setPackage(PKG_GEMINI),
        Intent(Intent.ACTION_VOICE_COMMAND).setPackage("com.google.android.googlequicksearchbox"),
        Intent(ACTION_VOICE_ASSIST).setPackage("com.google.android.googlequicksearchbox"),
        Intent(ACTION_VOICE_ASSIST).setPackage(PKG_GEMINI),
        Intent(Intent.ACTION_VOICE_COMMAND),
        Intent(ACTION_VOICE_ASSIST),
    )
    for (intent in attempts) {
        val ok = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return
    }
    openGemini(context)
}
