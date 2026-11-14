package com.noir.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    // Touche Accueil : remise à zéro (recherche vidée, page d'accueil, clavier rangé).
    private val resetTick = mutableIntStateOf(0)

    // Retour au premier plan : rafraîchit la liste des applis et l'heure.
    private val resumeTick = mutableIntStateOf(0)

    private lateinit var widgets: WidgetController
    private var configureCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgets = WidgetController(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            NoirApp(
                resetTick = resetTick.intValue,
                resumeTick = resumeTick.intValue,
                widgets = widgets,
                configureWidget = ::configureWidget,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resetTick.intValue++
    }

    /** Ouvre l'écran de configuration du widget (si le widget en a un) et rappelle `done`. */
    private fun configureWidget(id: Int, done: (Boolean) -> Unit) {
        configureCallback = done
        try {
            widgets.host.startAppWidgetConfigureActivityForResult(
                this,
                id,
                0,
                WidgetController.REQUEST_CONFIGURE,
                null,
            )
        } catch (e: Exception) {
            configureCallback = null
            done(false)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WidgetController.REQUEST_CONFIGURE) {
            val cb = configureCallback
            configureCallback = null
            cb?.invoke(resultCode == RESULT_OK)
        }
    }
}
