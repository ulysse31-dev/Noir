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

    // Retour à l'accueil (touche Home, retour depuis une autre appli) : remise à zéro + clavier.
    private val resetTick = mutableIntStateOf(0)

    // Simple retour au premier plan (ex. après une demande de permission) : rafraîchit les données.
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            NoirApp(resetTick = resetTick.intValue, resumeTick = resumeTick.intValue)
        }
    }

    override fun onStart() {
        super.onStart()
        resetTick.intValue++
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resetTick.intValue++
    }
}
