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

/**
 * Page à gauche de l'accueil : pour les pauses. L'article du jour de Wikipédia, puis des articles
 * par thème (WordPress, Marseille, Photo, IA). Toucher ouvre le lien dans le navigateur.
 */
@Composable
fun LirePage(active: Boolean, resumeTick: Int) {
    val context = LocalContext.current
    var articles by remember { mutableStateOf(cachedFeed(context)) }
    var fact by remember { mutableStateOf(cachedFact(context)) }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }
    var plays by remember { mutableIntStateOf(0) }

    LaunchedEffect(active, resumeTick) {
        if (active) {
            plays++
            loadFact(context)?.let { fact = it }
            loadFeed(context)?.let { articles = it }
            attempted = true
        }
    }

    val shown = remember(articles, topic) {
        val list = articles.filter { topic == null || it.topic == topic }
        if (topic == null) list.sortedByDescending { it.time }.take(24) else list
    }
    val animKey = topic to plays

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
                        BasicText(text = f.text, style = NoirType.Featured)
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
                    Chip(stringResource(R.string.read_all), topic == null) { topic = null }
                    TOPICS.forEach { t -> Chip(t, topic == t) { topic = t } }
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
private fun ArticleRow(a: Article, delayMillis: Int, animKey: Any, onClick: () -> Unit) {
    val meta = listOf(a.topic, a.source, ago(a.time))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .uppercase()
    Column(
        Modifier
            .fillMaxWidth()
            .riseIn(delayMillis = delayMillis, key = animKey)
            .tappable(onClick = onClick)
            .padding(horizontal = 28.dp),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            BasicText(
                text = meta,
                style = NoirType.DayHeader,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = a.title,
                style = NoirType.ArticleTitle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Divider()
    }
}

private fun ago(time: Long): String {
    if (time <= 0L) return ""
    val minutes = (System.currentTimeMillis() - time) / 60_000L
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 60 * 24 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (60 * 24)} j"
    }
}
