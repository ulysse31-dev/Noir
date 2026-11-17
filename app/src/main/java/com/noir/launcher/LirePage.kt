package com.noir.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Page à gauche de l'accueil : pour les pauses, tout vient de Wikipédia. L'article du jour, puis
 * ce jour-là, les plus lus et des thèmes (Marseille, Photo, Web, IA). Toucher ouvre l'article.
 */
@Composable
fun LirePage(active: Boolean, resumeTick: Int) {
    val context = LocalContext.current
    var wiki by remember { mutableStateOf(cachedWiki(context)) }
    var section by rememberSaveable { mutableStateOf(SECTIONS.first()) }
    var attempted by remember { mutableStateOf(false) }
    var plays by remember { mutableIntStateOf(0) }

    LaunchedEffect(active, resumeTick) {
        if (active) {
            plays++
            loadWiki(context)?.let { wiki = it }
            attempted = true
        }
    }

    val fact = wiki?.fact
    val shown = remember(wiki, section) { wiki?.items.orEmpty().filter { it.section == section } }
    val animKey = section to plays

    Column(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 64.dp, bottom = 12.dp)) {
            BasicText(
                text = stringResource(R.string.read_title),
                style = NoirType.Title,
                modifier = Modifier.riseIn(key = plays),
            )
            BasicText(
                text = stringResource(R.string.read_subtitle),
                style = NoirType.Body,
                modifier = Modifier.riseIn(delayMillis = 60, key = plays),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            fact?.let { f ->
                item(key = "fact") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .riseIn(delayMillis = 100, key = plays)
                            .tappable { f.link?.let { openUrl(context, it) } }
                            .padding(start = 28.dp, end = 28.dp, top = 20.dp),
                    ) {
                        BasicText(
                            text = stringResource(R.string.read_featured).uppercase(),
                            style = NoirType.DayHeader,
                        )
                        Spacer(Modifier.height(12.dp))
                        BasicText(text = f.title, style = NoirType.Featured)
                        Spacer(Modifier.height(8.dp))
                        BasicText(text = f.text, style = NoirType.Body)
                        Spacer(Modifier.height(28.dp))
                        Divider()
                    }
                }
            }

            item(key = "chips") {
                Row(
                    Modifier
                        .padding(top = 8.dp, start = 16.dp)
                        .horizontalScroll(rememberScrollState())
                        .riseIn(delayMillis = 160, key = plays),
                ) {
                    SECTIONS.forEach { sec -> Chip(sec, section == sec) { section = sec } }
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    BasicText(
                        text = stringResource(if (attempted) R.string.read_empty else R.string.read_loading),
                        style = NoirType.Body,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    )
                }
            } else {
                items(shown, key = { it.link }) { a ->
                    val index = shown.indexOf(a)
                    ArticleRow(a, delayMillis = minOf(index, 8) * 50, animKey = animKey) {
                        openUrl(context, a.link)
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NoirColors.Hairline),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .tappable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    val h = 2.dp.toPx()
                    drawRect(
                        color = NoirColors.White,
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h),
                    )
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = NoirType.Chip.copy(
                color = if (selected) NoirColors.White else NoirColors.Muted,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun ArticleRow(a: WikiItem, delayMillis: Int, animKey: Any, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .riseIn(delayMillis = delayMillis, key = animKey)
            .tappable(onClick = onClick)
            .padding(horizontal = 28.dp),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            if (a.meta.isNotBlank()) {
                BasicText(
                    text = a.meta.uppercase(),
                    style = NoirType.DayHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            BasicText(
                text = a.title,
                style = NoirType.ArticleTitle,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (a.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    text = a.text,
                    style = NoirType.Body.copy(fontSize = 15.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Divider()
    }
}
