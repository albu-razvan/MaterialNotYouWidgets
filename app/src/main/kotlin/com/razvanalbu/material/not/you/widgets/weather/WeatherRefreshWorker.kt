package com.razvanalbu.material.not.you.widgets.weather

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import java.util.concurrent.TimeUnit

class WeatherRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APPWIDGET_ID, INVALID_ID)
        val lat = inputData.getDouble(KEY_LAT, 0.0)
        val lon = inputData.getDouble(KEY_LON, 0.0)

        if (appWidgetId == INVALID_ID) return Result.failure()

        Log.d(TAG, "Worker started for widget $appWidgetId")

        val result = WeatherApi.fetchWeatherData(lat, lon)
        WeatherPillWidget.lastWeatherState[appWidgetId] = result

        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val views = WeatherWidgetViews.createBaseViews(applicationContext, appWidgetId)
                views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)

                when (result) {
                    is WeatherState.Success -> {
                        val size = WidgetUtils.getSquareSizePx(
                            applicationContext, appWidgetId
                        )
                        WidgetImageProvider.nextGeneration(appWidgetId)
                        WidgetImageProvider.invalidateCache(appWidgetId)

                        val bitmap = renderMerged(
                            applicationContext, result.temp,
                            result.iconRes, size, size
                        )
                        WidgetImageProvider.precache(
                            applicationContext, appWidgetId,
                            result.temp, result.iconRes
                        )

                        WeatherWidgetViews.showBitmap(views, bitmap)
                        bitmap.recycle()

                        Log.d(
                            TAG,
                            "[$appWidgetId] content_image <- bitmap (Worker success)"
                        )
                    }

                    is WeatherState.Error -> {
                        WeatherWidgetViews.showError(views, result.type)

                        Log.d(
                            TAG,
                            "[$appWidgetId] content_image <- ${
                                if (result.type == WeatherState.ErrorType.NETWORK)
                                    "ic_no_internet" else "ic_error"
                            } (Worker error)"
                        )
                    }
                }

                AppWidgetManager.getInstance(applicationContext)
                    .updateAppWidget(appWidgetId, views)

                WeatherWidgetViews.requestMorphOutIfAnimating(appWidgetId)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to apply weather data for widget $appWidgetId",
                    e
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "WeatherRefreshWorker"
        private const val INVALID_ID = -1
        private const val KEY_APPWIDGET_ID = "appWidgetId"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"

        fun enqueuePeriodicRefresh(
            context: Context,
            appWidgetId: Int,
            lat: Double,
            lon: Double
        ) {
            val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(
                15, TimeUnit.MINUTES
            )
                .setInputData(
                    workDataOf(
                        KEY_APPWIDGET_ID to appWidgetId,
                        KEY_LAT to lat,
                        KEY_LON to lon
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "weather_refresh_$appWidgetId",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueImmediateRefresh(
            context: Context,
            appWidgetId: Int,
            lat: Double,
            lon: Double
        ) {
            val request = OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
                .setInputData(
                    workDataOf(
                        KEY_APPWIDGET_ID to appWidgetId,
                        KEY_LAT to lat,
                        KEY_LON to lon
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancelPeriodicRefresh(context: Context, appWidgetId: Int) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("weather_refresh_$appWidgetId")
        }
    }
}
