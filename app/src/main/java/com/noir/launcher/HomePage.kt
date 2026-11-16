package com.noir.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Accueil : fond noir, clavier fermé (il s'ouvre en touchant la barre de recherche).
 * Recherche vide : heure, météo 24 h, raccourcis et appareil photo dans le coin.
 * Dès qu'on tape : résultats (texte seul) et ligne « Demander à Claude ».
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomePage(
    query: String,
    onQueryChange: (String) -> Unit,
    apps: List<AppEntry>,
    active: Boolean,
    resetTick: Int,
    resumeTick: Int,
) {
    val context = LocalContext.current
    val results = remember(apps, query) { searchApps(apps, query) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible

    var forecast by remember { mutableStateOf(cachedWeather(context)) }
    LaunchedEffect(resumeTick) { loadWeather(context)?.let { forecast = it } }
    val next24 = remember(forecast, resumeTick) { forecast?.let { next24Hours(it) }.orEmpty() }

    // Clavier fermé par défaut : rangé à chaque retour à l'accueil et en changeant de page.
    LaunchedEffect(active, resetTick) {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(results) {
        if (AUTO_LAUNCH_SINGLE_RESULT && query.length >= 2 && results.size == 1) {
            launchApp(context, results[0])
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                query.isNotBlank() -> {
                    // reverseLayout : l'index 0 (meilleur résultat) est en bas, près du champ.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    ) {
                        itemsIndexed(
                            results,
                            key = { _, app -> app.packageName + "/" + app.activityName },
                        ) { index, app ->
                            BasicText(
                                text = app.label,
                                style = NoirType.Result.copy(
                                    color = if (index == 0) NoirColors.White else NoirColors.Muted,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tappable(onLongClick = { openAppInfo(context, app) }) {
                                        launchApp(context, app)
                                    }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                // Clavier ouvert, rien de tapé : seulement l'heure, pour laisser la place.
                imeVisible -> ClockBlock(
                    resumeTick = resumeTick,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 28.dp),
                )
                else -> HomeContent(next24 = next24, resumeTick = resumeTick)
            }
        }

        if (query.isNotBlank()) {
            ClaudeRow(onAsk = { askClaude(context, query) })
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onGo = {
                val q = query.trim()
                val ask = q.startsWith("?") || q.endsWith("?")
                if (ask || results.isEmpty()) askClaude(context, query) else launchApp(context, results[0])
            },
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun HomeContent(next24: List<HourPoint>, resumeTick: Int) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ClockBlock(
                resumeTick = resumeTick,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp),
            )
            if (next24.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                WeatherBlock(next24)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 28.dp, bottom = 12.dp),
        ) {
            shortcuts.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .tappable { s.launch(context) }
                        .padding(end = 24.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Image(
                        painter = painterResource(s.icon),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(18.dp))
                    BasicText(text = stringResource(s.label), style = NoirType.Shortcut)
                }
            }
        }

        // Appareil photo : dans le coin en bas à droite
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 12.dp)
                .size(64.dp)
                .tappable { cameraShortcut.launch(context) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(cameraShortcut.icon),
                contentDescription = stringResource(cameraShortcut.label),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun ClaudeRow(onAsk: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .tappable(onClick = onAsk)
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_spark),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        BasicText(
            text = stringResource(R.string.ask_claude),
            style = NoirType.Shortcut,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClockBlock(resumeTick: Int, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = remember(resumeTick) { DateFormat.is24HourFormat(context) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }

    LaunchedEffect(resumeTick) {
        while (true) {
            now = ZonedDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 20L)
        }
    }

    val timeFormat = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()) }

    Column(modifier) {
        BasicText(text = now.format(timeFormat), style = NoirType.Clock)
        BasicText(text = now.format(dateFormat), style = NoirType.Date)
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
    focusRequester: FocusRequester,
) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NoirColors.Hairline),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = NoirType.Search,
            cursorBrush = SolidColor(NoirColors.White),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                    if (query.isEmpty()) {
                        BasicText(text = stringResource(R.string.search_hint), style = NoirType.Hint)
                    }
                    innerTextField()
                }
            },
        )
    }
}
