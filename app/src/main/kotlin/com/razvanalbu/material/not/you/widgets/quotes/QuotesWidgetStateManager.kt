package com.razvanalbu.material.not.you.widgets.quotes

import android.appwidget.AppWidgetManager
import android.content.Context
import com.razvanalbu.material.not.you.widgets.quotes.providers.QuotesImageProvider

internal object QuotesWidgetStateManager {

    fun refreshWidget(context: Context, appWidgetId: Int) {
        val quote = QuotesStore.pickRandomQuote(context, appWidgetId)
        if (quote != null) {
            QuotesImageProvider.nextGeneration(appWidgetId)
            QuotesImageProvider.invalidateCache(appWidgetId)
        }

        val views = QuotesWidgetViews.createViews(context, appWidgetId, quote)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    fun scheduleRefresh(context: Context, appWidgetId: Int) {
        refreshWidget(context, appWidgetId)
        QuotesRefreshWorker.enqueuePeriodicRefresh(context, appWidgetId)
    }
}
