package com.micoyc.speakthat.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.micoyc.speakthat.MasterSwitchController
import com.micoyc.speakthat.R

class SpeakThatSmallWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_MASTER) {
            val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("master_switch_enabled", true)
            MasterSwitchController.setEnabled(context, !isEnabled, "SmallWidget")
            updateAllWidgets(context)
        } else if (intent.action == ACTION_STATE_CHANGED) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_MASTER = "com.micoyc.speakthat.widgets.ACTION_TOGGLE_MASTER_SMALL"
        const val ACTION_STATE_CHANGED = "com.micoyc.speakthat.widgets.ACTION_STATE_CHANGED"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("master_switch_enabled", true)

            val views = RemoteViews(context.packageName, R.layout.widget_small)

            // Update icon background color
            if (isEnabled) {
                views.setImageViewResource(R.id.widget_small_icon_bg, R.drawable.widget_circle_white_vector)
            } else {
                views.setImageViewResource(R.id.widget_small_icon_bg, R.drawable.widget_circle_grey_vector)
            }

            // Set up click intent
            val intent = Intent(context, SpeakThatSmallWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_MASTER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_small_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SpeakThatSmallWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}