package com.noir.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
 * Recherche vide -> l'heure et la date. Dès qu'on tape -> les résultats (texte seul),
 * le meilleur résultat étant le plus proche du champ.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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

    // Clavier fermé par défaut : rangé à chaque retour à l'accueil et en changeant de page.
    // Il s'ouvre en touchant la barre de recherche.
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
            if (query.isBlank()) {
                ClockBlock(
                    resumeTick = resumeTick,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 28.dp),
                )
            } else {
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
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onGo = { results.firstOrNull()?.let { launchApp(context, it) } },
            focusRequester = focusRequester,
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
