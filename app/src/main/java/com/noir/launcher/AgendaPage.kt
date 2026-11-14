package com.noir.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Nombre de jours affichés à partir d'aujourd'hui. */
const val AGENDA_DAYS = 14

data class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
)

data class AgendaItem(val event: AgendaEvent, val timeLabel: String)

data class AgendaDay(val date: LocalDate, val items: List<AgendaItem>)

fun hasCalendarPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Lit les événements via le fournisseur d'agenda d'Android (Google Agenda synchronisé
 * sur le téléphone) : pas de compte, pas de clé d'API, fonctionne hors ligne.
 * Seuls les calendriers cochés « visibles » dans Google Agenda sont lus.
 */
fun loadAgenda(context: Context, days: Int = AGENDA_DAYS): List<AgendaDay> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val windowStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val windowEnd = today.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uriBuilder, windowStart)
    ContentUris.appendId(uriBuilder, windowEnd)

    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.CALENDAR_COLOR,
    )

    val untitled = context.getString(R.string.untitled)
    val allDayLabel = context.getString(R.string.all_day)
    val events = mutableListOf<AgendaEvent>()

    try {
        context.contentResolver.query(
            uriBuilder.build(),
            projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                events += AgendaEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: untitled,
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                    color = c.getInt(6),
                )
            }
        }
    } catch (e: SecurityException) {
        return emptyList()
    }

    val timeFormat = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
    )

    return (0 until days).map { offset ->
        val date = today.plusDays(offset.toLong())
        val items = events.mapNotNull { e ->
            val (first, last) = e.dateSpan(zone)
            if (date.isBefore(first) || date.isAfter(last)) return@mapNotNull null
            val label = if (e.allDay) {
                allDayLabel
            } else {
                val start = if (date == first) {
                    Instant.ofEpochMilli(e.begin).atZone(zone).format(timeFormat)
                } else {
                    "…"
                }
                val stop = if (date == last) {
                    Instant.ofEpochMilli(e.end).atZone(zone).format(timeFormat)
                } else {
                    "…"
                }
                "$start – $stop"
            }
            AgendaItem(e, label)
        }.sortedWith(compareBy<AgendaItem>({ !it.event.allDay }, { it.event.begin }))
        AgendaDay(date, items)
    }
}

/** Premier et dernier jour couverts. Les événements « toute la journée » sont stockés en UTC. */
private fun AgendaEvent.dateSpan(zone: ZoneId): Pair<LocalDate, LocalDate> {
    val z = if (allDay) ZoneOffset.UTC else zone
    val first = Instant.ofEpochMilli(begin).atZone(z).toLocalDate()
    val last = Instant.ofEpochMilli(maxOf(end - 1, begin)).atZone(z).toLocalDate()
    return first to last
}

@Composable
fun AgendaPage(resumeTick: Int) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCalendarPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf<List<AgendaDay>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        denied = !ok
    }

    // Re-vérifie la permission au retour (ex. après un passage dans les réglages).
    LaunchedEffect(resumeTick) { granted = hasCalendarPermission(context) }

    LaunchedEffect(granted, resumeTick) {
        if (granted) days = withContext(Dispatchers.IO) { loadAgenda(context) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(NoirColors.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (!granted) {
            PermissionPrompt(
                denied = denied,
                onRequest = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onSettings = { openAppSettings(context) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
            )
        } else {
            days?.let { AgendaList(it) }
        }
    }
}

@Composable
private fun PermissionPrompt(
    denied: Boolean,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        BasicText(text = stringResource(R.string.agenda_permission_text), style = NoirType.Body)
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = stringResource(if (denied) R.string.open_settings else R.string.allow_agenda),
            style = NoirType.Action,
            modifier = Modifier
                .tappable { if (denied) onSettings() else onRequest() }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun AgendaList(days: List<AgendaDay>) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()) }
    val anyEvent = days.any { it.items.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 48.dp),
    ) {
        if (!anyEvent) {
            item(key = "empty") {
                BasicText(
                    text = stringResource(R.string.nothing_planned_days, AGENDA_DAYS),
                    style = NoirType.Body,
                )
            }
        } else {
            days.forEachIndexed { index, day ->
                item(key = "header-${day.date}") {
                    DayHeader(day.date.format(dateFormat), index)
                }
                if (day.items.isEmpty()) {
                    if (index == 0) {
                        item(key = "today-empty") {
                            BasicText(
                                text = stringResource(R.string.nothing_planned),
                                style = NoirType.EventPlace,
                            )
                        }
                    }
                } else {
                    items(
                        day.items,
                        key = { "${day.date}-${it.event.id}-${it.event.begin}" },
                    ) { entry ->
                        EventRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(dateText: String, index: Int) {
    val text = when (index) {
        0 -> stringResource(R.string.today) + " · " + dateText
        1 -> stringResource(R.string.tomorrow) + " · " + dateText
        else -> dateText
    }
    BasicText(
        text = text.uppercase(),
        style = NoirType.DayHeader,
        modifier = Modifier.padding(top = if (index == 0) 0.dp else 32.dp, bottom = 6.dp),
    )
}

@Composable
private fun EventRow(entry: AgendaItem) {
    val context = LocalContext.current
    val e = entry.event
    val barColor = Color(0xFF000000.toInt() or e.color)

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .tappable { openEvent(context, e) }
            .padding(vertical = 10.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(barColor),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            BasicText(text = entry.timeLabel, style = NoirType.EventTime)
            BasicText(text = e.title, style = NoirType.EventTitle)
            e.location?.let { BasicText(text = it, style = NoirType.EventPlace) }
        }
    }
}

private fun openEvent(context: Context, e: AgendaEvent) {
    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
    val intent = Intent(Intent.ACTION_VIEW)
        .setData(uri)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
