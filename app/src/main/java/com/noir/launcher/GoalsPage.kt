package com.noir.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale

private const val LETTERS = "LMMJVSD"

private val MissedRing = Color(0xFF555555)
private val FutureRing = Color(0xFF262626)
private val FutureText = Color(0xFF555555)
private val Danger = Color(0xFFFF6B6B)

private fun style(size: Int, weight: FontWeight, color: Color, lineHeight: Int? = null, underline: Boolean = false) =
    TextStyle(
        fontFamily = NoirFont,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
        color = color,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )

private val TitleStyle = TextStyle(
    fontFamily = NoirFont,
    fontWeight = FontWeight.Light,
    fontSize = 36.sp,
    letterSpacing = (-0.5).sp,
    color = NoirColors.White,
)
private val SubStyle = style(14, FontWeight.Normal, NoirColors.Muted, lineHeight = 20)
private val NameStyle = style(18, FontWeight.Normal, NoirColors.White, lineHeight = 24)
private val CountStyle = style(28, FontWeight.Light, NoirColors.White)
private val OfStyle = style(15, FontWeight.Light, NoirColors.Muted)
private val SmallStyle = style(13, FontWeight.Normal, NoirColors.Muted)
private val LetterStyle = style(11, FontWeight.Normal, NoirColors.Muted)
private val LetterToday = style(11, FontWeight.Medium, NoirColors.White)
private val LinkStyle = style(16, FontWeight.Normal, NoirColors.White, underline = true)
private val DangerLink = style(16, FontWeight.Normal, Danger, underline = true)
private val AddStyle = style(17, FontWeight.Light, NoirColors.White)
private val HintStyle = style(18, FontWeight.Light, NoirColors.Muted)
private val DetailTitle = style(26, FontWeight.Light, NoirColors.White, lineHeight = 32)
private val StatValue = style(22, FontWeight.Light, NoirColors.White)
private val BigNumber = TextStyle(
    fontFamily = NoirFont,
    fontWeight = FontWeight.Thin,
    fontSize = 104.sp,
    letterSpacing = (-3).sp,
    color = NoirColors.White,
)
private val MonthStyle = TextStyle(
    fontFamily = NoirFont,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 1.6.sp,
    color = NoirColors.Muted,
)

private sealed interface GoalScreen {
    data object Overview : GoalScreen
    data class Detail(val id: String) : GoalScreen
    data object Edit : GoalScreen
}

/**
 * Page à gauche de l'accueil : des objectifs à tenir, avec les jours de la semaine à cocher.
 * Liste -> détail du mois (série en cours, meilleure série) ; « Modifier » ajoute, retire, réordonne.
 * Tout est enregistré sur le téléphone.
 */
@Composable
fun GoalsPage(active: Boolean, resumeTick: Int) {
    val context = LocalContext.current
    val store = remember { GoalStore(context) }
    var goals by remember { mutableStateOf(store.load()) }
    val today = remember(resumeTick) { LocalDate.now() }
    var screen by remember { mutableStateOf<GoalScreen>(GoalScreen.Overview) }

    fun update(next: List<Goal>) {
        goals = next
        store.save(next)
    }

    fun toggle(id: String, date: LocalDate) {
        if (date.isAfter(today)) return
        update(
            goals.map { g ->
                if (g.id != id) g else g.copy(done = if (date in g.done) g.done - date else g.done + date)
            },
        )
    }

    // « Retour » revient d'abord à la liste, puis à l'accueil.
    BackHandler(enabled = active && screen != GoalScreen.Overview) { screen = GoalScreen.Overview }
    LaunchedEffect(active) { if (!active) screen = GoalScreen.Overview }

    Column(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val s = screen
        val detail = (s as? GoalScreen.Detail)?.let { d -> goals.firstOrNull { it.id == d.id } }
        when {
            s is GoalScreen.Edit -> EditScreen(
                goals = goals,
                onUpdate = { update(it) },
                onDone = { screen = GoalScreen.Overview },
            )
            detail != null -> DetailScreen(
                goal = detail,
                today = today,
                onBack = { screen = GoalScreen.Overview },
                onToggle = { toggle(detail.id, it) },
            )
            else -> OverviewScreen(
                goals = goals,
                today = today,
                active = active,
                onOpen = { screen = GoalScreen.Detail(it) },
                onToggle = { id, date -> toggle(id, date) },
                onEdit = { screen = GoalScreen.Edit },
            )
        }
    }
}

// ---------- Liste ----------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.OverviewScreen(
    goals: List<Goal>,
    today: LocalDate,
    active: Boolean,
    onOpen: (String) -> Unit,
    onToggle: (String, LocalDate) -> Unit,
    onEdit: () -> Unit,
) {
    val monday = mondayOf(today)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.riseIn(key = active)) {
            BasicText("Objectifs", style = TitleStyle)
            Spacer(Modifier.height(4.dp))
            BasicText(weekLabel(monday), style = SubStyle)
        }
        BasicText(
            "Modifier",
            style = LinkStyle,
            modifier = Modifier
                .tappable(onClick = onEdit)
                .padding(start = 12.dp, top = 14.dp, bottom = 12.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (goals.isEmpty()) {
            BasicText(
                "Aucun objectif pour l'instant. Ajoutes-en un pour suivre les jours où tu le tiens.",
                style = SubStyle,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
        goals.forEachIndexed { i, g ->
            GoalRow(g, today, monday, i, active, onOpen = { onOpen(g.id) }, onToggle = { onToggle(g.id, it) })
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tappable(onClick = onEdit)
            .padding(start = 28.dp, top = 12.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlusIcon(22, NoirColors.White)
        Spacer(Modifier.width(12.dp))
        BasicText("Ajouter un objectif", style = AddStyle)
    }
}

@Composable
private fun GoalRow(
    g: Goal,
    today: LocalDate,
    monday: LocalDate,
    index: Int,
    active: Boolean,
    onOpen: () -> Unit,
    onToggle: (LocalDate) -> Unit,
) {
    val count = g.countIn(monday, monday.plusDays(6))
    val streak = g.streak(today)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .riseIn(delayMillis = 60 + index * 70, key = active),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NoirColors.Hairline),
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText(
                g.name,
                style = NameStyle,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
                    .tappable(onClick = onOpen),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("$count", style = CountStyle)
                BasicText(" / 7", style = OfStyle)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (k in 0 until 7) {
                val date = monday.plusDays(k.toLong())
                DayDot(date, today, done = date in g.done, onClick = { onToggle(date) })
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            if (streak > 0) "Série en cours · $streak ${if (streak > 1) "jours" else "jour"}" else "Pas de série en cours",
            style = SmallStyle,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DayDot(date: LocalDate, today: LocalDate, done: Boolean, onClick: () -> Unit) {
    val isToday = date == today
    val future = date.isAfter(today)
    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(
            LETTERS[date.dayOfWeek.value - 1].toString(),
            style = if (isToday) LetterToday else LetterStyle,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(40.dp)
                .let { if (future) it else it.springTappable(onClick) },
            contentAlignment = Alignment.Center,
        ) {
            val ring = when {
                isToday -> NoirColors.White
                future -> FutureRing
                else -> MissedRing
            }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .let { if (done) it.background(NoirColors.White) else it.border(1.5.dp, ring, CircleShape) },
                contentAlignment = Alignment.Center,
            ) {
                if (done) CheckMark(16, Color.Black)
            }
        }
    }
}

// ---------- Détail ----------

@Composable
private fun DetailScreen(
    goal: Goal,
    today: LocalDate,
    onBack: () -> Unit,
    onToggle: (LocalDate) -> Unit,
) {
    val current = YearMonth.from(today)
    var month by remember(goal.id) { mutableStateOf(current) }
    val monthCount = goal.countInMonth(month)
    val elapsed = when {
        month == current -> today.dayOfMonth
        month.isBefore(current) -> month.lengthOfMonth()
        else -> 0
    }
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH)
    val streak = goal.streak(today)
    val best = goal.bestStreak()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        BasicText(
            "‹ Objectifs",
            style = SubStyle,
            modifier = Modifier
                .tappable(onClick = onBack)
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(goal.name, style = DetailTitle, modifier = Modifier.riseIn(key = goal.id))
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.riseIn(delayMillis = 60, key = goal.id)) {
            BasicText("$monthCount", style = BigNumber)
            Spacer(Modifier.width(14.dp))
            BasicText(
                "${if (monthCount > 1) "jours tenus" else "jour tenu"}\nen $monthName\nsur $elapsed",
                style = style(15, FontWeight.Normal, NoirColors.Muted, lineHeight = 20),
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.riseIn(delayMillis = 120, key = goal.id)) {
            Column {
                BasicText("$streak ${if (streak > 1) "jours" else "jour"}", style = StatValue)
                Spacer(Modifier.height(2.dp))
                BasicText("série en cours", style = SmallStyle)
            }
            Spacer(Modifier.width(40.dp))
            Column {
                BasicText("$best ${if (best > 1) "jours" else "jour"}", style = StatValue)
                Spacer(Modifier.height(2.dp))
                BasicText("meilleure série", style = SmallStyle)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NoirColors.Hairline),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .tappable { month = month.minusMonths(1) },
                contentAlignment = Alignment.CenterStart,
            ) { ChevronIcon(left = true, color = NoirColors.White) }
            BasicText("${monthName.uppercase()} ${month.year}", style = MonthStyle)
            val canGoNext = month.isBefore(current)
            Box(
                Modifier
                    .size(44.dp)
                    .let { if (canGoNext) it.tappable { month = month.plusMonths(1) } else it },
                contentAlignment = Alignment.CenterEnd,
            ) { ChevronIcon(left = false, color = if (canGoNext) NoirColors.White else FutureRing) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (k in 0 until 7) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    BasicText(LETTERS[k].toString(), style = LetterStyle)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val lead = month.atDay(1).dayOfWeek.value - 1
        val length = month.lengthOfMonth()
        val weeks = (lead + length + 6) / 7
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (c in 0 until 7) {
                    val day = w * 7 + c - lead + 1
                    if (day in 1..length) {
                        val date = month.atDay(day)
                        DayCell(date, today, done = date in goal.done, onClick = { onToggle(date) })
                    } else {
                        Spacer(Modifier.size(width = 40.dp, height = 44.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, done: Boolean, onClick: () -> Unit) {
    val isToday = date == today
    val future = date.isAfter(today)
    Box(
        Modifier
            .size(width = 40.dp, height = 44.dp)
            .let { if (future) it else it.springTappable(onClick) },
        contentAlignment = Alignment.Center,
    ) {
        val ring = if (isToday) NoirColors.White else MissedRing
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .let {
                    when {
                        done -> it.background(NoirColors.White)
                        future -> it
                        else -> it.border(1.5.dp, ring, CircleShape)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val color = when {
                done -> Color.Black
                isToday -> NoirColors.White
                future -> FutureText
                else -> NoirColors.Muted
            }
            BasicText(
                "${date.dayOfMonth}",
                style = style(13, if (done || isToday) FontWeight.Medium else FontWeight.Normal, color),
            )
        }
    }
}

// ---------- Modifier ----------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EditScreen(
    goals: List<Goal>,
    onUpdate: (List<Goal>) -> Unit,
    onDone: () -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current

    fun add() {
        val name = text.trim()
        if (name.isEmpty()) return
        onUpdate(goals + Goal(newGoalId(), name.take(60), emptySet()))
        text = ""
        focus.clearFocus()
    }

    fun move(i: Int, j: Int) {
        val m = goals.toMutableList()
        Collections.swap(m, i, j)
        onUpdate(m)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            BasicText("Modifier", style = TitleStyle)
            Spacer(Modifier.height(4.dp))
            BasicText("Retirer, réordonner ou ajouter", style = SubStyle)
        }
        BasicText(
            "OK",
            style = LinkStyle,
            modifier = Modifier
                .tappable(onClick = onDone)
                .padding(start = 12.dp, top = 14.dp, bottom = 12.dp),
        )
    }
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        goals.forEachIndexed { i, g ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NoirColors.Hairline),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .springTappable { confirm = if (confirm == g.id) null else g.id },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .border(1.5.dp, Danger, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { MinusIcon(12, Danger) }
                }
                BasicText(
                    g.name,
                    style = NameStyle,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                if (confirm == g.id) {
                    BasicText(
                        "Supprimer ?",
                        style = DangerLink,
                        modifier = Modifier
                            .tappable {
                                onUpdate(goals.filter { it.id != g.id })
                                confirm = null
                            }
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                    )
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .let { if (i > 0) it.tappable { move(i, i - 1) } else it },
                        contentAlignment = Alignment.Center,
                    ) { ChevronIcon(up = true, color = if (i > 0) NoirColors.Muted else FutureRing) }
                    Box(
                        Modifier
                            .size(40.dp)
                            .let { if (i < goals.lastIndex) it.tappable { move(i, i + 1) } else it },
                        contentAlignment = Alignment.Center,
                    ) { ChevronIcon(up = false, color = if (i < goals.lastIndex) NoirColors.Muted else FutureRing) }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NoirColors.Hairline),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.CenterStart) { PlusIcon(28, NoirColors.White) }
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                singleLine = true,
                textStyle = NameStyle,
                cursorBrush = SolidColor(NoirColors.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) BasicText("Nouvel objectif", style = HintStyle)
                        inner()
                    }
                },
            )
            BasicText(
                "Ajouter",
                style = LinkStyle,
                modifier = Modifier
                    .tappable { add() }
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        BasicText("Retirer un objectif efface aussi ses jours cochés.", style = SmallStyle)
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- Pictos dessinés ----------

@Composable
private fun CheckMark(sizeDp: Int, color: Color) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.52f)
            lineTo(w * 0.40f, h * 0.72f)
            lineTo(w * 0.80f, h * 0.30f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun PlusIcon(sizeDp: Int, color: Color) {
    Canvas(Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val m = w * 0.18f
        drawLine(color, Offset(m, h / 2), Offset(w - m, h / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(w / 2, m), Offset(w / 2, h - m), sw, StrokeCap.Round)
    }
}

@Composable
private fun MinusIcon(sizeDp: Int, color: Color) {
    Canvas(Modifier.size(sizeDp.dp)) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.4.dp.toPx(), StrokeCap.Round)
    }
}

/** Chevron vers la gauche/droite ([left]) ou vers le haut/bas ([up]). */
@Composable
private fun ChevronIcon(color: Color, left: Boolean? = null, up: Boolean? = null) {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val path = Path()
        when {
            left != null -> if (left) {
                path.moveTo(w * 0.7f, h * 0.1f); path.lineTo(w * 0.25f, h * 0.5f); path.lineTo(w * 0.7f, h * 0.9f)
            } else {
                path.moveTo(w * 0.3f, h * 0.1f); path.lineTo(w * 0.75f, h * 0.5f); path.lineTo(w * 0.3f, h * 0.9f)
            }
            up == true -> {
                path.moveTo(w * 0.1f, h * 0.7f); path.lineTo(w * 0.5f, h * 0.25f); path.lineTo(w * 0.9f, h * 0.7f)
            }
            else -> {
                path.moveTo(w * 0.1f, h * 0.3f); path.lineTo(w * 0.5f, h * 0.75f); path.lineTo(w * 0.9f, h * 0.3f)
            }
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun weekLabel(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val long = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)
    val short = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH)
    return if (monday.month == sunday.month) {
        "${monday.dayOfMonth} – ${sunday.dayOfMonth} ${sunday.format(long)}"
    } else {
        "${monday.dayOfMonth} ${monday.format(short)} – ${sunday.dayOfMonth} ${sunday.format(short)}"
    }
}
