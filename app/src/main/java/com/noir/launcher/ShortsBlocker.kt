package com.noir.launcher

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

// Applis YouTube surveillées (l'officielle, et ReVanced au cas où).
private val YOUTUBE_PACKAGES = setOf(
    "com.google.android.youtube",
    "app.revanced.android.youtube",
)

// Conteneur du lecteur de Shorts. Si YouTube le renomme un jour, c'est ici qu'il faut l'ajouter.
private val SHORTS_VIEW_IDS = listOf("reel_player_page_container")

// Intervalle minimal entre deux inspections de l'écran (YouTube envoie des événements en rafale).
private const val CHECK_INTERVAL_MS = 300L

// Pause après un « Retour », le temps que YouTube quitte les Shorts.
private const val COOLDOWN_MS = 1200L

// Si les Shorts sont encore là après plusieurs « Retour » rapprochés, on renvoie à l'accueil.
private const val STRIKE_WINDOW_MS = 4000L
private const val STRIKES_BEFORE_HOME = 3

/**
 * Bloque les Shorts dans l'appli YouTube : dès que le lecteur de Shorts s'affiche, appuie sur « Retour ».
 * Le reste de YouTube (accueil, recherche, vidéos normales) n'est pas touché.
 * Ne lit rien d'autre que la présence du lecteur de Shorts, n'enregistre rien, n'utilise pas le réseau.
 */
class ShortsBlockerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var lastBlock = 0L
    private var strikes = 0
    private var pendingPackage: String? = null

    private val delayedCheck = Runnable {
        pendingPackage?.let { inspect(it) }
        pendingPackage = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in YOUTUBE_PACKAGES) return

        val now = SystemClock.uptimeMillis()
        val wait = CHECK_INTERVAL_MS - (now - lastCheck)
        if (wait > 0) {
            // Trop tôt : on vérifiera juste après, pour ne rater aucun écran.
            if (pendingPackage == null) {
                pendingPackage = pkg
                handler.postDelayed(delayedCheck, wait)
            }
            return
        }
        inspect(pkg)
    }

    private fun inspect(pkg: String) {
        val now = SystemClock.uptimeMillis()
        lastCheck = now
        if (now - lastBlock < COOLDOWN_MS) return

        val onShorts = try {
            shortsOnScreen(pkg)
        } catch (e: Exception) {
            false
        }
        if (!onShorts) return

        strikes = if (now - lastBlock < STRIKE_WINDOW_MS) strikes + 1 else 1
        lastBlock = now

        // « Retour » ramène à l'onglet précédent de YouTube (ou ferme le Short ouvert depuis un lien).
        // Si ça ne suffit pas, on sort carrément de YouTube.
        val action = if (strikes >= STRIKES_BEFORE_HOME) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK
        performGlobalAction(action)
        Toast.makeText(this, "Pas de Shorts.", Toast.LENGTH_SHORT).show()
    }

    private fun shortsOnScreen(pkg: String): Boolean {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        for (w in windows) {
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) w.root?.let { roots.add(it) }
        }
        return roots
            .filter { it.packageName?.toString() == pkg }
            .any { root ->
                SHORTS_VIEW_IDS.any { id ->
                    root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { isVisible(it) }
                }
            }
    }

    private fun isVisible(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        return node.isVisibleToUser && r.width() > 0 && r.height() > 0
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(delayedCheck)
        super.onDestroy()
    }
}
