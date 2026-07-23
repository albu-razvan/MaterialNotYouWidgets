package com.razvanalbu.material.not.you.widgets.quotes

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.razvanalbu.material.not.you.widgets.core.BaseWidgetProvider
import com.razvanalbu.material.not.you.widgets.core.WidgetConfigProxyActivity
import com.razvanalbu.material.not.you.widgets.quotes.providers.QuotesImageProvider

class QuotesWidgetProvider : BaseWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate for ${appWidgetIds.size} widgets")

        for (appWidgetId in appWidgetIds) {
            refreshWidget(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        Log.d(TAG, "options changed for $appWidgetId")

        val quote = QuotesStore.loadCurrentQuote(context, appWidgetId)
        if (quote != null) {
            QuotesImageProvider.nextGeneration(appWidgetId)
            QuotesImageProvider.invalidateCache(appWidgetId)

            appWidgetManager.updateAppWidget(
                appWidgetId,
                QuotesWidgetViews.createSyncViews(context, appWidgetId)
            )

            Handler(Looper.getMainLooper()).postDelayed({
                val views = QuotesWidgetViews.createViews(context, appWidgetId, quote)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }, 150L)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TAP -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val quotes = QuotesStore.loadQuotes(context, appWidgetId)
                    if (quotes.isEmpty()) {
                        openConfigActivity(context, appWidgetId)
                    } else {
                        refreshWidget(context, appWidgetId)
                    }
                }

                return
            }

            ACTION_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    refreshWidget(context, appWidgetId)
                }

                return
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    android.content.ComponentName(context, QuotesWidgetProvider::class.java)
                )

                for (appWidgetId in appWidgetIds) {
                    schedulePeriodicRefresh(context, appWidgetId)
                }

                return
            }
        }

        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        for (appWidgetId in appWidgetIds) {
            cancelPeriodicRefresh(context, appWidgetId)
            QuotesStore.removeQuotesData(context, appWidgetId)
            QuotesImageProvider.invalidateCache(appWidgetId)
        }
    }

    private fun refreshWidget(context: Context, appWidgetId: Int) {
        val quote = QuotesStore.pickRandomQuote(context, appWidgetId)
        if (quote != null) {
            QuotesImageProvider.nextGeneration(appWidgetId)
            QuotesImageProvider.invalidateCache(appWidgetId)
        }

        val views = QuotesWidgetViews.createViews(context, appWidgetId, quote)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)

        schedulePeriodicRefresh(context, appWidgetId)
    }

    private fun schedulePeriodicRefresh(context: Context, appWidgetId: Int) {
        QuotesRefreshWorker.enqueuePeriodicRefresh(context, appWidgetId)
    }

    private fun cancelPeriodicRefresh(context: Context, appWidgetId: Int) {
        QuotesRefreshWorker.cancelPeriodicRefresh(context, appWidgetId)
    }

    private fun openConfigActivity(context: Context, appWidgetId: Int) {
        val intent = Intent(context, WidgetConfigProxyActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    override fun getConfigurationActivity(): Class<*> {
        return QuotesConfigureActivity::class.java
    }

    companion object {
        private const val TAG = "QuotesWidget"

        const val ACTION_TAP =
            "com.razvanalbu.material.not.you.widgets.quotes.TAP"
        const val ACTION_REFRESH =
            "com.razvanalbu.material.not.you.widgets.quotes.REFRESH"
    }
}
