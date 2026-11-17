package com.noir.launcher

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PKG_CLAUDE = "com.anthropic.claude"
private const val PKG_GOOGLE = "com.google.android.googlequicksearchbox"

/**
 * Accueil : fond noir, clavier fermé (il s'ouvre en touchant la barre de recherche).
 * Recherche vide : heure, météo 24 h, raccourcis et appareil photo dans le coin.
 * Dès qu'on tape : résultats (texte seul) et, à droite du champ, deux icônes grises
 * (Claude, Google).
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

    // Compteur d'arrivées sur l'accueil : relance les animations d'entrée.
    var plays by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, resetTick) {
        if (active) plays++
        // Clavier fermé par défaut : rangé à chaque retour à l'accueil et en changeant de page.
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
                                    .riseIn(delayMillis = index * 45)
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
                    playKey = plays,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 28.dp, end = 28.dp, top = 36.dp),
                )
                else -> HomeContent(next24 = next24, resumeTick = resumeTick, playKey = plays)
            }
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onClaude = { askClaude(context, query) },
            onGoogle = { searchGoogle(context, query) },
            onMic = { openGemini(context) },
            onGo = {
                val q = query.trim()
                val ask = q.startsWith("?") || q.endsWith("?")
                when {
                    ask -> askClaude(context, query)
                    results.isEmpty() -> searchGoogle(context, query)
                    else -> launchApp(context, results[0])
                }
            },
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun HomeContent(next24: List<HourPoint>, resumeTick: Int, playKey: Int) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        // Bloc heure + météo descendu : centré dans l'espace libre, un peu au-dessus du centre.
        Column(Modifier.fillMaxSize()) {
            ClockBlock(
                resumeTick = resumeTick,
                playKey = playKey,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 100.dp),
            )
            if (next24.isNotEmpty()) {
                Spacer(Modifier.height(48.dp))
                WeatherBlock(next24, playKey)
            }
        }

        // Pictos seuls, alignés sur une ligne ; l'appareil photo est le dernier, dans le coin à droite.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (shortcuts + cameraShortcut).forEachIndexed { i, s ->
                Box(
                    Modifier
                        .size(56.dp)
                        .popIn(delayMillis = 500 + i * 60, key = playKey)
                        .springTappable { s.launch(context) },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(s.icon),
                        contentDescription = stringResource(s.label),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockBlock(resumeTick: Int, playKey: Int, modifier: Modifier) {
    val context = LocalContext.current
    val is24 = remember(resumeTick) { DateFormat.is24HourFormat(context) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }

    LaunchedEffect(resumeTick) {
        while (true) {
            now = ZonedDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 20L)
        }
    }

    // Les deux-points « respirent » quelques fois à chaque arrivée, puis restent fixes (économie d'énergie).
    val colon = remember(playKey) { Animatable(1f) }
    LaunchedEffect(playKey) {
        repeat(4) {
            colon.animateTo(0.22f, tween(1000))
            colon.animateTo(1f, tween(1000))
        }
    }

    val hourFormat = remember(is24) { DateTimeFormatter.ofPattern(if (is24) "HH" else "h") }
    val minuteFormat = remember { DateTimeFormatter.ofPattern("mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()) }
    val dateText = remember(now) {
        now.format(dateFormat).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    Column(modifier) {
        BasicText(
            text = buildAnnotatedString {
                append(now.format(hourFormat))
                withStyle(SpanStyle(color = NoirColors.White.copy(alpha = colon.value))) { append(":") }
                append(now.format(minuteFormat))
            },
            style = NoirType.Clock,
            modifier = Modifier.riseIn(key = playKey),
        )
        BasicText(
            text = dateText,
            style = NoirType.Date,
            modifier = Modifier.riseIn(delayMillis = 80, key = playKey),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClaude: () -> Unit,
    onGoogle: () -> Unit,
    onMic: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(Modifier.padding(start = 28.dp, end = 8.dp, top = 20.dp, bottom = 20.dp)) {
                        if (query.isEmpty()) {
                            BasicText(text = stringResource(R.string.search_hint), style = NoirType.Hint)
                        }
                        innerTextField()
                    }
                },
            )
            // Deux icônes grises à droite quand on tape : Claude, Google.
            if (query.isNotBlank()) {
                SearchIcon(
                    packageName = PKG_CLAUDE,
                    fallback = R.drawable.ic_spark,
                    label = stringResource(R.string.ask_claude),
                    delayMillis = 0,
                    onClick = onClaude,
                )
                SearchIcon(
                    packageName = PKG_GOOGLE,
                    fallback = R.drawable.ic_search,
                    label = stringResource(R.string.search_google),
                    delayMillis = 70,
                    onClick = onGoogle,
                )
                Spacer(Modifier.size(12.dp))
            } else {
                // Champ vide : le micro lance Gemini.
                SearchIcon(
                    packageName = null,
                    fallback = R.drawable.ic_mic,
                    label = stringResource(R.string.ask_gemini),
                    delayMillis = 0,
                    onClick = onMic,
                )
                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

/**
 * Icône réelle de l'appli passée en niveaux de gris ; si l'appli n'est pas installée,
 * un pictogramme blanc neutre.
 */
@Composable
private fun SearchIcon(
    packageName: String?,
    @DrawableRes fallback: Int,
    label: String,
    delayMillis: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(packageName) {
        if (packageName == null) null else runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    Box(
        Modifier
            .size(48.dp)
            .spinIn(delayMillis)
            .springTappable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = label,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            )
        } else {
            Image(
                painter = painterResource(fallback),
                contentDescription = label,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
