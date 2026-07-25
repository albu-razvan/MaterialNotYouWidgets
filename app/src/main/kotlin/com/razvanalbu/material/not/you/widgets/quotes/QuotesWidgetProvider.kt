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
            QuotesWidgetStateManager.scheduleRefresh(context, appWidgetId)
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

            pendingResizes.remove(appWidgetId)?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                pendingResizes.remove(appWidgetId)
                val views = QuotesWidgetViews.createViews(context, appWidgetId, quote)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
            pendingResizes[appWidgetId] = runnable

            mainHandler.postDelayed(runnable, 150L)
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
                        QuotesWidgetStateManager.scheduleRefresh(context, appWidgetId)
                    }
                }

                return
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    android.content.ComponentName(context, QuotesWidgetProvider::class.java)
                )

                for (appWidgetId in appWidgetIds) {
                    QuotesRefreshWorker.enqueuePeriodicRefresh(context, appWidgetId)
                }

                return
            }
        }

        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        for (appWidgetId in appWidgetIds) {
            QuotesRefreshWorker.cancelPeriodicRefresh(context, appWidgetId)
            QuotesStore.removeQuotesData(context, appWidgetId)
            QuotesImageProvider.invalidateCache(appWidgetId)
        }
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
        private val mainHandler = Handler(Looper.getMainLooper())
        private val pendingResizes = mutableMapOf<Int, Runnable>()

        const val ACTION_TAP =
            "com.razvanalbu.material.not.you.widgets.quotes.TAP"
    }
}
