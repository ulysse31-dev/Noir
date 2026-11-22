package com.noir.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Un objectif à tenir : son nom et les jours où il a été tenu. */
data class Goal(val id: String, val name: String, val done: Set<LocalDate>)

fun newGoalId(): String = UUID.randomUUID().toString()

/** Lundi de la semaine de [d]. */
fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

/** Jours tenus entre [from] et [to] inclus. */
fun Goal.countIn(from: LocalDate, to: LocalDate): Int =
    done.count { !it.isBefore(from) && !it.isAfter(to) }

fun Goal.countInMonth(month: YearMonth): Int = done.count { YearMonth.from(it) == month }

/** Série en cours : jours consécutifs tenus jusqu'à aujourd'hui (ou jusqu'à hier si aujourd'hui n'est pas encore coché). */
fun Goal.streak(today: LocalDate): Int {
    var d = if (today in done) today else today.minusDays(1)
    var n = 0
    while (d in done) {
        n++
        d = d.minusDays(1)
    }
    return n
}

/** Plus longue série de jours consécutifs tenus. */
fun Goal.bestStreak(): Int {
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in done.sorted()) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        if (run > best) best = run
        prev = d
    }
    return best
}

/** Objectifs enregistrés sur le téléphone (aucun réseau). */
class GoalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("noir", Context.MODE_PRIVATE)

    fun load(): List<Goal> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val days = o.optJSONArray("done") ?: JSONArray()
                Goal(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    done = (0 until days.length())
                        .mapNotNull { runCatching { LocalDate.parse(days.getString(it)) }.getOrNull() }
                        .toSet(),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(goals: List<Goal>) {
        val arr = JSONArray()
        goals.forEach { g ->
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("done", JSONArray(g.done.sorted().map { it.toString() })),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "goals_json"
    }
}
