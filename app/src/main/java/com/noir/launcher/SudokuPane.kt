package com.noir.launcher

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ENTRY = Color(0xFF6CB8FF)
private val CONFLICT = Color(0xFFFF6B6B)

private class Game(val puzzle: IntArray, val solution: IntArray, val cur: IntArray, val level: Int)

private fun load(context: Context): Game? {
    val p = context.getSharedPreferences("noir", Context.MODE_PRIVATE)
    val puzzle = Sudoku.decode(p.getString("sd_puzzle", null)) ?: return null
    val solution = Sudoku.decode(p.getString("sd_solution", null)) ?: return null
    val cur = Sudoku.decode(p.getString("sd_cur", null)) ?: return null
    return Game(puzzle, solution, cur, p.getInt("sd_level", 1).coerceIn(0, 2))
}

private fun save(context: Context, g: Game) {
    context.getSharedPreferences("noir", Context.MODE_PRIVATE).edit()
        .putString("sd_puzzle", Sudoku.encode(g.puzzle))
        .putString("sd_solution", Sudoku.encode(g.solution))
        .putString("sd_cur", Sudoku.encode(g.cur))
        .putInt("sd_level", g.level)
        .apply()
}

/** Sudoku : grille générée sur le téléphone (aucun réseau), sauvegardée à chaque case. */
@Composable
fun SudokuPane(active: Boolean) {
    val context = LocalContext.current
    var game by remember { mutableStateOf(load(context)) }
    var level by remember { mutableIntStateOf(game?.level ?: 1) }
    var generating by remember { mutableStateOf(false) }
    var wantNew by remember { mutableIntStateOf(if (game == null) 1 else 0) }
    var selected by remember { mutableIntStateOf(-1) }

    LaunchedEffect(wantNew) {
        if (wantNew == 0) return@LaunchedEffect
        generating = true
        val (puzzle, solution) = withContext(Dispatchers.Default) { Sudoku.generate(Sudoku.CLUES[level]) }
        val g = Game(puzzle, solution, puzzle.copyOf(), level)
        save(context, g)
        game = g
        selected = -1
        generating = false
    }

    val g = game
    val conflicts = remember(g?.cur?.let { Sudoku.encode(it) }) { g?.let { Sudoku.conflicts(it.cur) } }
    val solved = g != null && conflicts != null && g.cur.none { it == 0 } && conflicts.none { it }

    fun enter(d: Int) {
        val gm = game ?: return
        val i = selected
        if (i < 0 || gm.puzzle[i] != 0) return
        val next = gm.cur.copyOf().also { it[i] = d }
        game = Game(gm.puzzle, gm.solution, next, gm.level).also { save(context, it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        BasicText(
            text = stringResource(
                when (level) {
                    0 -> R.string.sd_easy
                    2 -> R.string.sd_hard
                    else -> R.string.sd_medium
                },
            ).uppercase(),
            style = NoirType.DayHeader,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .riseIn(key = active),
        )
        Spacer(Modifier.height(14.dp))

        if (g == null || generating) {
            BasicText(
                text = stringResource(R.string.sd_loading),
                style = NoirType.Body,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            )
        } else {
            Grid(g, conflicts ?: BooleanArray(81), selected) { selected = it }
            Spacer(Modifier.height(18.dp))
            if (solved) {
                BasicText(
                    text = stringResource(R.string.sd_done),
                    style = NoirType.PoemTitle,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                val count = IntArray(10).also { c -> g.cur.forEach { c[it]++ } }
                Row(Modifier.fillMaxWidth()) {
                    for (d in 1..9) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .springTappable { enter(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = d.toString(),
                                style = PAD_STYLE.copy(color = if (count[d] >= 9) NoirColors.Hairline else NoirColors.White),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = stringResource(R.string.sd_level),
                style = NoirType.Action,
                modifier = Modifier
                    .tappable {
                        level = (level + 1) % 3
                        wantNew++
                    }
                    .padding(vertical = 12.dp),
            )
            if (g != null && !solved) {
                BasicText(
                    text = stringResource(R.string.sd_erase),
                    style = NoirType.Action,
                    modifier = Modifier
                        .tappable { enter(0) }
                        .padding(vertical = 12.dp),
                )
            }
            BasicText(
                text = stringResource(R.string.sd_new),
                style = NoirType.Action,
                modifier = Modifier
                    .tappable { wantNew++ }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

private val PAD_STYLE = TextStyle(fontFamily = NoirFont, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Color.White)

@Composable
private fun Grid(g: Game, conflicts: BooleanArray, selected: Int, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val cell = size.width / 9f
                    val c = (o.x / cell).toInt().coerceIn(0, 8)
                    val r = (o.y / cell).toInt().coerceIn(0, 8)
                    onSelect(r * 9 + c)
                }
            },
    ) {
        val cell = size.width / 9f
        val fontSize = (cell * 0.52f).toSp()

        // Case choisie et ses voisines (ligne, colonne, région)
        if (selected >= 0) {
            for (i in 0..80) {
                val peer = i / 9 == selected / 9 || i % 9 == selected % 9 || Sudoku.box(i) == Sudoku.box(selected)
                if (!peer) continue
                drawRect(
                    color = Color.White.copy(alpha = if (i == selected) 0.20f else 0.06f),
                    topLeft = Offset((i % 9) * cell, (i / 9) * cell),
                    size = Size(cell, cell),
                )
            }
        }

        // Lignes fines, puis lignes des régions un peu plus marquées
        for (k in 0..9) {
            val thick = k % 3 == 0
            val color = if (thick) NoirColors.Muted else NoirColors.Hairline
            val w = (if (thick) 1.5.dp else 0.7.dp).toPx()
            drawLine(color, Offset(k * cell, 0f), Offset(k * cell, size.width), w)
            drawLine(color, Offset(0f, k * cell), Offset(size.width, k * cell), w)
        }

        for (i in 0..80) {
            val d = g.cur[i]
            if (d == 0) continue
            val given = g.puzzle[i] != 0
            val color = when {
                conflicts[i] -> CONFLICT
                given -> Color.White
                else -> ENTRY
            }
            val layout = measurer.measure(
                d.toString(),
                TextStyle(
                    fontFamily = NoirFont,
                    fontWeight = if (given) FontWeight.Medium else FontWeight.Normal,
                    fontSize = fontSize,
                    color = color,
                ),
            )
            drawText(
                layout,
                topLeft = Offset(
                    (i % 9) * cell + (cell - layout.size.width) / 2f,
                    (i / 9) * cell + (cell - layout.size.height) / 2f,
                ),
            )
        }
    }
}
