package com.noir.shorts

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

private val GRAY = Color.rgb(140, 140, 140)

/** Écran unique, dans le style de Noir : état du bloqueur et raccourcis vers les réglages utiles. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val side = dp(28)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(side, dp(110), side, dp(40))
        }

        root.addView(text("Sans Shorts", 36f, Color.WHITE, Typeface.create("sans-serif-light", Typeface.NORMAL)))

        status = text("", 18f, GRAY).apply { setPadding(0, dp(12), 0, dp(48)) }
        root.addView(status)

        root.addView(link("Activer dans l'accessibilité") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(link("Infos de l'appli") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        })

        hint = text(
            "Interrupteur grisé (« Paramètre restreint ») ? Ouvre « Infos de l'appli », menu ⋮ en haut à droite, " +
                "« Autoriser les paramètres restreints », puis réessaie.",
            14f, GRAY,
        ).apply { setPadding(0, dp(32), 0, 0) }
        root.addView(hint)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val on = isBlockerEnabled()
        status.text = if (on) "Actif. Les Shorts se ferment tout seuls." else "Inactif."
        status.setTextColor(if (on) Color.WHITE else GRAY)
        hint.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun isBlockerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, ShortsBlockerService::class.java)
        val parts = TextUtils.SimpleStringSplitter(':')
        parts.setString(enabled)
        return parts.any { ComponentName.unflattenFromString(it) == me }
    }

    private fun text(value: String, sizeSp: Float, color: Int, face: Typeface = Typeface.SANS_SERIF) =
        TextView(this).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = face
        }

    private fun link(label: String, onTap: () -> Unit) =
        text(label, 20f, Color.WHITE).apply {
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            setOnClickListener { onTap() }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
