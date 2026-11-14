package com.noir.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Héberge un widget Android (ici : Google Agenda) et retient lequel a été choisi. */
class WidgetController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("noir", Context.MODE_PRIVATE)

    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val host: AppWidgetHost = AppWidgetHost(appContext, HOST_ID)

    var widgetId: Int
        get() = prefs.getInt(KEY_WIDGET_ID, -1)
        set(value) {
            prefs.edit().putInt(KEY_WIDGET_ID, value).apply()
        }

    fun startListening() {
        runCatching { host.startListening() }
    }

    fun stopListening() {
        runCatching { host.stopListening() }
    }

    fun deleteId(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }

    /** Oublie le widget choisi (pour en choisir un autre). */
    fun clear() {
        val id = widgetId
        if (id != -1) deleteId(id)
        widgetId = -1
    }

    /** Widgets Google Agenda ; à défaut, tout widget « calendar » ; à défaut, tous les widgets. */
    fun providers(): List<AppWidgetProviderInfo> {
        val all = manager.installedProviders
        val google = all.filter { it.provider.packageName == GOOGLE_CALENDAR }
        if (google.isNotEmpty()) return google
        val calendar = all.filter { it.provider.packageName.contains("calendar", ignoreCase = true) }
        return calendar.ifEmpty { all }
    }

    companion object {
        const val HOST_ID = 1024
        const val REQUEST_CONFIGURE = 4242
        private const val KEY_WIDGET_ID = "widget_id"
        private const val GOOGLE_CALENDAR = "com.google.android.calendar"
    }
}
