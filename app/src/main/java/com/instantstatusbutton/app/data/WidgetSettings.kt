package com.instantstatusbutton.app.data

import android.content.Context
import android.graphics.Color

const val MAX_LABEL_LENGTH = 10

enum class Network(val title: String) {
    X("X"),
    LINKEDIN("LinkedIn"),
    FACEBOOK("Facebook"),
    INSTAGRAM("Instagram"),
}

data class WidgetSettings(
    val statusText: String = "I am feeling great now!",
    val label: String = "Yeah!",
    val color: Int = DEFAULT_COLOR,
    val addLocation: Boolean = false,
    val networks: Set<Network> = emptySet(),
) {
    companion object {
        val DEFAULT_COLOR: Int = Color.parseColor("#3F51B5")
    }
}

/** Per-widget settings, keyed by appWidgetId. Nothing secret lives here. */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    fun load(widgetId: Int): WidgetSettings {
        val d = WidgetSettings()
        val nets = prefs.getStringSet(key(widgetId, "networks"), null)
            ?.mapNotNull { n -> Network.entries.firstOrNull { it.name == n } }?.toSet()
            ?: d.networks
        return WidgetSettings(
            statusText = prefs.getString(key(widgetId, "status"), d.statusText) ?: d.statusText,
            label = prefs.getString(key(widgetId, "label"), d.label) ?: d.label,
            color = prefs.getInt(key(widgetId, "color"), d.color),
            addLocation = prefs.getBoolean(key(widgetId, "location"), d.addLocation),
            networks = nets,
        )
    }

    fun save(widgetId: Int, s: WidgetSettings) {
        prefs.edit()
            .putString(key(widgetId, "status"), s.statusText)
            .putString(key(widgetId, "label"), s.label.take(MAX_LABEL_LENGTH))
            .putInt(key(widgetId, "color"), s.color)
            .putBoolean(key(widgetId, "location"), s.addLocation)
            .putStringSet(key(widgetId, "networks"), s.networks.map { it.name }.toSet())
            .putBoolean(key(widgetId, "exists"), true)
            .apply()
    }

    fun delete(widgetId: Int) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("w$widgetId.") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun key(id: Int, name: String) = "w$id.$name"
}
