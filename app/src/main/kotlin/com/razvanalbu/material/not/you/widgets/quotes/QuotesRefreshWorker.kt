package com.razvanalbu.material.not.you.widgets.quotes

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

        QuotesWidgetStateManager.refreshWidget(applicationContext, appWidgetId)

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
                .setInitialDelay(60, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "quotes_refresh_$appWidgetId",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodicRefresh(context: Context, appWidgetId: Int) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("quotes_refresh_$appWidgetId")
        }
    }
}
