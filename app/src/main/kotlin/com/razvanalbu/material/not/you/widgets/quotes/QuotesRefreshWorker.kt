package com.razvanalbu.material.not.you.widgets.quotes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.razvanalbu.material.not.you.widgets.quotes.providers.QuotesImageProvider
import java.util.concurrent.TimeUnit

class QuotesRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APPWIDGET_ID, INVALID_ID)
        if (appWidgetId == INVALID_ID) {
            return Result.failure()
        }

        val quote = QuotesStore.pickRandomQuote(applicationContext, appWidgetId)
        if (quote == null) {
            Log.d(TAG, "No quotes configured for widget $appWidgetId")

            val views = QuotesWidgetViews.createViews(applicationContext, appWidgetId, null)
            AppWidgetManager.getInstance(applicationContext).updateAppWidget(appWidgetId, views)

            return Result.success()
        }

        QuotesImageProvider.nextGeneration(appWidgetId)
        QuotesImageProvider.invalidateCache(appWidgetId)

        val views = QuotesWidgetViews.createViews(applicationContext, appWidgetId, quote)
        AppWidgetManager.getInstance(applicationContext).updateAppWidget(appWidgetId, views)

        return Result.success()
    }

    companion object {
        private const val TAG = "QuotesRefreshWorker"
        private const val INVALID_ID = -1
        private const val KEY_APPWIDGET_ID = "appWidgetId"

        fun enqueuePeriodicRefresh(context: Context, appWidgetId: Int) {
            val request = PeriodicWorkRequestBuilder<QuotesRefreshWorker>(
                60, TimeUnit.MINUTES
            )
                .setInputData(
                    workDataOf(KEY_APPWIDGET_ID to appWidgetId)
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "quotes_refresh_$appWidgetId",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelPeriodicRefresh(context: Context, appWidgetId: Int) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("quotes_refresh_$appWidgetId")
        }
    }
}
