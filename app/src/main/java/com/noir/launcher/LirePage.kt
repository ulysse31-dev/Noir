package com.noir.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

private sealed interface PoemUi {
    data object Loading : PoemUi
    data class Ready(val text: String) : PoemUi
    /** Échec de chargement : [reason] aide à comprendre ce qui coince. */
    data class Offline(val reason: String) : PoemUi
}

/**
 * Page à gauche de l'accueil : un poème à la fois, à apprendre. On le lit (ou on le récite en
 * ne voyant que les initiales), puis on le coche : il passe dans les appris et le suivant arrive.
 */
@Composable
private fun PoemPane(active: Boolean, resumeTick: Int) {
    val context = LocalContext.current
    val store = remember { PoemStore(context) }

    var index by remember { mutableStateOf(store.current()) }
    var ui by remember { mutableStateOf<PoemUi>(PoemUi.Loading) }
    var retry by remember { mutableIntStateOf(0) }
    var learnedCount by remember { mutableIntStateOf(store.learned().size) }
    var lastFailure by remember { mutableStateOf("") }
    var initialsOnly by rememberSaveable { mutableStateOf(false) }

    // Charge le poème en cours (depuis le téléphone, sinon depuis Wikisource).
    LaunchedEffect(index, retry, active) {
        val i = index ?: return@LaunchedEffect
        val ref = POEMS[i]
        val cached = store.cached(ref.id)
        if (cached != null) {
            ui = PoemUi.Ready(cached)
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        ui = PoemUi.Loading
        when (val r = fetchPoem(ref)) {
            is PoemResult.Ok -> {
                store.cache(ref.id, r.text)
                ui = PoemUi.Ready(r.text)
            }
            is PoemResult.Missing -> {
                // Texte introuvable : on l'écarte pour cette fois (pas définitivement) et on passe au suivant.
                lastFailure = "${ref.title} — ${r.reason}"
                store.markBad(ref.id)
                index = store.next(i)
                if (index == null) ui = PoemUi.Offline(lastFailure)
            }
            is PoemResult.Offline -> ui = PoemUi.Offline(r.reason)
        }
    }

    // Prépare le suivant en arrière-plan pour qu'il s'affiche tout de suite.
    LaunchedEffect(index, ui) {
        val i = index ?: return@LaunchedEffect
        if (ui !is PoemUi.Ready) return@LaunchedEffect
        val n = store.next(i) ?: return@LaunchedEffect
        val ref = POEMS[n]
        if (store.cached(ref.id) != null) return@LaunchedEffect
        when (val r = fetchPoem(ref)) {
            is PoemResult.Ok -> store.cache(ref.id, r.text)
            is PoemResult.Missing -> store.markBad(ref.id)
            is PoemResult.Offline -> Unit
        }
    }

    fun advance(from: Int) {
        index = store.next(from)
        ui = PoemUi.Loading
        initialsOnly = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black),
    ) {
        val current = index
        if (current == null && !store.allLearned()) {
            // Il reste des poèmes à apprendre, mais aucun n'a pu être chargé : ce n'est pas « tout appris ».
            Column(Modifier.padding(top = 24.dp)) {
                Message(stringResource(R.string.poem_offline), stringResource(R.string.poem_retry)) {
                    store.clearSkipped()
                    index = store.current()
                    ui = PoemUi.Loading
                    retry++
                }
                if (lastFailure.isNotEmpty()) {
                    BasicText(
                        text = lastFailure,
                        style = NoirType.DayHeader,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
            }
            return@Column
        }
        if (current == null) {
            Done(
                learned = learnedCount,
                onRestart = {
                    store.reset()
                    learnedCount = 0
                    index = store.current()
                    ui = PoemUi.Loading
                },
            )
            return@Column
        }

        val ref = POEMS[current]
        val animKey = current to active

        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp)) {
            BasicText(
                text = stringResource(R.string.read_progress, learnedCount, learnedCount + store.remaining()).uppercase(),
                style = NoirType.DayHeader,
                modifier = Modifier.riseIn(key = animKey),
            )
            Spacer(Modifier.height(14.dp))
            BasicText(
                text = ref.title,
                style = NoirType.PoemTitle,
                modifier = Modifier.riseIn(delayMillis = 60, key = animKey),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = ref.author,
                style = NoirType.Body,
                modifier = Modifier.riseIn(delayMillis = 100, key = animKey),
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (val state = ui) {
                PoemUi.Loading -> Message(stringResource(R.string.poem_loading), null) {}
                is PoemUi.Offline -> Column {
                    Message(stringResource(R.string.poem_offline), stringResource(R.string.poem_retry)) {
                        store.clearSkipped()
                        retry++
                    }
                    BasicText(
                        text = state.reason,
                        style = NoirType.DayHeader,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
                is PoemUi.Ready -> {
                    val shown = remember(state.text, initialsOnly) {
                        if (initialsOnly) toInitials(state.text) else state.text
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 24.dp),
                    ) {
                        shown.split("\n\n").forEachIndexed { n, stanza ->
                            BasicText(
                                text = stanza.trim(),
                                style = NoirType.Poem,
                                modifier = Modifier
                                    .padding(bottom = 24.dp)
                                    .riseIn(delayMillis = 140 + n * 60, key = animKey),
                            )
                        }
                    }
                }
            }
        }

        // Pictos seuls : initiales/texte, passer, et « appris » (le rond blanc).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 17.dp, end = 17.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(
                icon = if (initialsOnly) R.drawable.ic_show else R.drawable.ic_hide,
                description = stringResource(if (initialsOnly) R.string.cd_show else R.string.cd_hide),
            ) { initialsOnly = !initialsOnly }
            ActionIcon(R.drawable.ic_next, stringResource(R.string.cd_skip)) { advance(current) }
            Box(
                Modifier
                    .size(56.dp)
                    .springTappable {
                        store.markLearned(ref.id)
                        learnedCount = store.learned().size
                        advance(current)
                    }
                    .clip(CircleShape)
                    .background(NoirColors.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.cd_learned),
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .springTappable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.padding(horizontal = 28.dp, vertical = 28.dp)) {
        BasicText(text = text, style = NoirType.Body)
        if (action != null) {
            BasicText(
                text = action,
                style = NoirType.Action,
                modifier = Modifier
                    .tappable(onClick = onAction)
                    .padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Done(learned: Int, onRestart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(text = stringResource(R.string.poem_done), style = NoirType.PoemTitle)
        Spacer(Modifier.height(8.dp))
        BasicText(
            text = stringResource(R.string.read_progress, learned, learned),
            style = NoirType.Body,
        )
        BasicText(
            text = stringResource(R.string.poem_restart),
            style = NoirType.Action,
            modifier = Modifier
                .tappable(onClick = onRestart)
                .padding(vertical = 20.dp),
        )
    }
}

/** « Les sanglots longs » -> « L… s… l… » : pour réciter en ne voyant que les initiales. */
private fun toInitials(text: String): String =
    Regex("\\p{L}+").replace(text) { m ->
        val w = m.value
        if (w.length > 1) w.substring(0, 1) + "…" else w
    }

/** Page à gauche de l'accueil : un poème à apprendre ou une grille de sudoku, au choix. */
@Composable
fun LirePage(active: Boolean, resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("noir", android.content.Context.MODE_PRIVATE) }
    var mode by remember { mutableIntStateOf(prefs.getInt("lire_mode", 0)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.padding(start = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(R.string.tab_poem, R.string.tab_sudoku).forEachIndexed { i, label ->
                BasicText(
                    text = stringResource(label).uppercase(),
                    style = NoirType.DayHeader.copy(color = if (mode == i) NoirColors.White else NoirColors.Muted),
                    modifier = Modifier
                        .tappable {
                            mode = i
                            prefs.edit().putInt("lire_mode", i).apply()
                        }
                        .padding(8.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mode == 0) PoemPane(active, resumeTick) else SudokuPane(active)
        }
    }
}
