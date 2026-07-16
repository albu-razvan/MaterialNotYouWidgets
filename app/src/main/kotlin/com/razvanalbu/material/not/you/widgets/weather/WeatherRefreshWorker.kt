package com.razvanalbu.material.not.you.widgets.weather

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
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager.ContentState
import com.razvanalbu.material.not.you.widgets.weather.providers.WeatherProviders
import com.razvanalbu.material.not.you.widgets.weather.providers.WidgetImageProvider
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

        val config = WidgetConfig.load(applicationContext, appWidgetId)
        val provider = WeatherProviders.get(config?.provider ?: PROVIDER_MET_NO)
        val result = provider.fetchWeatherData(config?.lat ?: lat, config?.lon ?: lon)

        val contentState = when (result) {
            is WeatherState.Success -> ContentState.SUCCESS
            is WeatherState.Error -> when (result.type) {
                WeatherState.ErrorType.NETWORK -> ContentState.NO_INTERNET
                WeatherState.ErrorType.UNKNOWN -> ContentState.ERROR
            }
        }

        if (result is WeatherState.Success) {
            WidgetImageProvider.nextGeneration(appWidgetId)
            WidgetImageProvider.invalidateCache(appWidgetId)
            WidgetImageProvider.precache(
                applicationContext, appWidgetId,
                result.temp, result.iconRes
            )
        }

        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                WeatherWidgetStateManager.applyState(
                    applicationContext, appWidgetId, contentState, result
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply weather data for widget $appWidgetId", e)
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
