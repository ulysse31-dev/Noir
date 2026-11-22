package com.noir.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pages, de gauche à droite : Accueil, Agenda (widget). */
private const val HOME_PAGE = 0

@Composable
fun NoirApp(
    resetTick: Int,
    resumeTick: Int,
    widgets: WidgetController,
    configureWidget: (Int, (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = HOME_PAGE, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    // Retour à l'accueil (touche Accueil) : recherche vidée, page d'accueil.
    LaunchedEffect(resetTick) {
        query = ""
        if (pagerState.currentPage != HOME_PAGE) pagerState.scrollToPage(HOME_PAGE)
    }

    // Liste des applis rechargée à chaque retour au premier plan (installations/désinstallations).
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.Default) { loadApps(context) }
    }

    // Actif seulement si utile, pour ne pas gêner la fermeture du clavier par « retour ».
    BackHandler(enabled = pagerState.currentPage != HOME_PAGE || query.isNotEmpty()) {
        if (pagerState.currentPage != HOME_PAGE) {
            scope.launch { pagerState.animateScrollToPage(HOME_PAGE) }
        } else {
            query = ""
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxSize()
            .background(NoirColors.Black),
    ) { page ->
        // Les pages voisines reculent légèrement (échelle + opacité) pendant le glissement.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val distance = kotlin.math.abs(
                        pagerState.currentPage - page + pagerState.currentPageOffsetFraction,
                    ).coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * distance
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.7f * distance
                },
        ) {
            when (page) {
                HOME_PAGE -> HomePage(
                    query = query,
                    onQueryChange = { query = it },
                    apps = apps,
                    active = pagerState.currentPage == HOME_PAGE,
                    resetTick = resetTick,
                    resumeTick = resumeTick,
                )
                else -> WidgetPage(controller = widgets, configure = configureWidget)
            }
        }
    }
}
