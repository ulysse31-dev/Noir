package com.noir.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.text.Normalizer
import java.util.Locale

/** Mettre à true pour lancer automatiquement l'appli quand il ne reste qu'un résultat. */
const val AUTO_LAUNCH_SINGLE_RESULT = false

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String = normalize(label)
}

private val diacritics = Regex("\\p{M}+")

/** Minuscules et sans accents, pour que « agenda » trouve « Agenda » et « Événements ». */
fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(diacritics, "")

@Suppress("DEPRECATION")
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val infos = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        pm.queryIntentActivities(query, 0)
    }
    return infos
        .filter { it.activityInfo.packageName != context.packageName }
        .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
        .sortedBy { it.key }
}

fun searchApps(apps: List<AppEntry>, rawQuery: String, limit: Int = 20): List<AppEntry> {
    val q = normalize(rawQuery.trim())
    if (q.isEmpty()) return emptyList()
    return apps
        .mapNotNull { app -> score(app.key, q)?.let { app to it } }
        .sortedWith(compareBy<Pair<AppEntry, Int>>({ it.second }, { it.first.key }))
        .take(limit)
        .map { it.first }
}

/** 0 = commence par, 1 = un mot commence par, 2 = contient, 3 = lettres dans l'ordre. */
private fun score(key: String, q: String): Int? = when {
    key.startsWith(q) -> 0
    key.contains(" $q") -> 1
    key.contains(q) -> 2
    q.length >= 3 && isSubsequence(q, key) -> 3
    else -> null
}

private fun isSubsequence(q: String, s: String): Boolean {
    var i = 0
    for (c in s) {
        if (i < q.length && c == q[i]) i++
    }
    return i == q.length
}

fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(app.packageName, app.activityName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

fun openAppInfo(context: Context, app: AppEntry) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", app.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
