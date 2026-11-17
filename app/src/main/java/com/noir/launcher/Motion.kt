package com.noir.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Appui : l'élément se tasse puis rebondit (ressort), sans ripple. */
@Composable
fun Modifier.springTappable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Entrée : montée + agrandissement avec rebond, après un délai. Rejoué quand [key] change. */
@Composable
fun Modifier.popIn(delayMillis: Int, key: Any? = Unit): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMillis.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val s = 0.55f + 0.45f * v
        scaleX = s
        scaleY = s
        translationY = (1f - v) * 16.dp.toPx()
    }
}

/** Entrée : léger glissement vers le haut avec fondu. Rejoué quand [key] change. */
@Composable
fun Modifier.riseIn(delayMillis: Int = 0, key: Any? = Unit): Modifier {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMillis.toLong())
        p.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 18.dp.toPx()
    }
}

/** Entrée des icônes de recherche : arrivent de la droite en tournant, avec rebond. */
@Composable
fun Modifier.spinIn(delayMillis: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val s = 0.4f + 0.6f * v
        scaleX = s
        scaleY = s
        rotationZ = (1f - v) * -40f
        translationX = (1f - v) * 22.dp.toPx()
    }
}
