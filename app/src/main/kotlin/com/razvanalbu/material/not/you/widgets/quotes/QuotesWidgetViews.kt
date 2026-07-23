package com.razvanalbu.material.not.you.widgets.quotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.quotes.providers.QuotesImageProvider

internal object QuotesWidgetViews {

    fun createSyncViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.quotes_widget)

        views.setImageViewResource(R.id.content_image, R.drawable.ic_sync)
        setTapIntent(context, views, appWidgetId)

        return views
    }

    fun createViews(context: Context, appWidgetId: Int, quote: Quote?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.quotes_widget)

        applyQuote(views, context, appWidgetId, quote)
        setTapIntent(context, views, appWidgetId)

        return views
    }

    fun applyQuote(views: RemoteViews, context: Context, appWidgetId: Int, quote: Quote?) {
        if (quote != null) {
            views.setImageViewUri(
                R.id.content_image,
                QuotesImageProvider.uri(context.packageName, appWidgetId)
            )
        } else {
            views.setImageViewResource(R.id.content_image, R.drawable.ic_gear)
        }
    }

    fun setTapIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setOnClickPendingIntent(
            R.id.content_container,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, QuotesWidgetProvider::class.java).apply {
                    action = QuotesWidgetProvider.ACTION_TAP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }
}
