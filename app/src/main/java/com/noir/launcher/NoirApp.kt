package com.noir.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoirApp(resetTick: Int, resumeTick: Int) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

    // Retour à l'accueil : recherche vidée, page d'accueil (pas l'agenda).
    LaunchedEffect(resetTick) {
        query = ""
        if (pagerState.currentPage != 0) pagerState.scrollToPage(0)
    }

    // Liste des applis rechargée à chaque retour au premier plan (installations/désinstallations).
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.Default) { loadApps(context) }
    }

    // Actif seulement si utile, pour ne pas gêner la fermeture du clavier par « retour ».
    BackHandler(enabled = pagerState.currentPage != 0 || query.isNotEmpty()) {
        if (pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
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
        if (page == 0) {
            HomePage(
                query = query,
                onQueryChange = { query = it },
                apps = apps,
                active = pagerState.currentPage == 0,
                resetTick = resetTick,
                resumeTick = resumeTick,
            )
        } else {
            AgendaPage(resumeTick = resumeTick)
        }
    }
}
