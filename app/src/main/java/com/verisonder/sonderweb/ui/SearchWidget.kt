package com.verisonder.sonderweb.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.verisonder.sonderweb.R

/** The home screen search widget: tap to search, the mic for voice search, the frame to search with a picture. */
class SearchWidget : AppWidgetProvider() {

    companion object {
        const val SEARCH = "com.verisonder.sonderweb.WIDGET_SEARCH"
        const val VOICE = "com.verisonder.sonderweb.WIDGET_VOICE"
        const val IMAGE = "com.verisonder.sonderweb.WIDGET_IMAGE"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_search)
        views.setOnClickPendingIntent(R.id.widget_pill, open(context, SEARCH, 1))
        views.setOnClickPendingIntent(R.id.widget_voice, open(context, VOICE, 2))
        views.setOnClickPendingIntent(R.id.widget_image, open(context, IMAGE, 3))
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun open(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            code,
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
