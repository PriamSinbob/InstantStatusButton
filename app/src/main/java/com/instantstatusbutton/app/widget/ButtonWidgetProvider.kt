package com.instantstatusbutton.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.instantstatusbutton.app.R
import com.instantstatusbutton.app.TapActivity
import com.instantstatusbutton.app.data.MAX_LABEL_LENGTH
import com.instantstatusbutton.app.data.SettingsRepository

class ButtonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val repo = SettingsRepository(context)
        appWidgetIds.forEach { repo.delete(it) }
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val s = SettingsRepository(context).load(widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_button)
            views.setInt(R.id.widget_circle, "setColorFilter", s.color)
            views.setTextViewText(R.id.widget_label, s.label.take(MAX_LABEL_LENGTH))
            val light = ColorUtils.calculateLuminance(s.color) > 0.6
            views.setTextColor(R.id.widget_label, if (light) Color.parseColor("#1B1B1F") else Color.WHITE)

            val tap = Intent(context, TapActivity::class.java)
                .setData(Uri.parse("instantstatusbutton://tap/$widgetId"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, widgetId, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            manager.updateAppWidget(widgetId, views)
        }

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ButtonWidgetProvider::class.java))
    }
}
