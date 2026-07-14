package com.razvanalbu.material.not.you.widgets.weather

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager.ContentState

class WeatherPillWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate for ${appWidgetIds.size} widgets")

        for (appWidgetId in appWidgetIds) {
            val config = WidgetConfig.load(context, appWidgetId)
            if (config == null) {
                WeatherWidgetStateManager.applyState(
                    context, appWidgetId, ContentState.REQUIRES_CONFIG
                )
            } else {
                refreshAndAnimate(
                    context, appWidgetId, isUserInitiated = false
                )

                schedulePeriodicRefresh(context, appWidgetId)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        Log.d(TAG, "options changed for $appWidgetId")

        BasePumpService.updateWidgetSize(
            appWidgetId,
            WidgetUtils.getSquareSizePx(context, appWidgetId)
        )

        if (FramePumpService.currentPhase == PumpPhase.IDLE) {
            val config = WidgetConfig.load(context, appWidgetId)
            if (config == null) {
                WeatherWidgetStateManager.applyState(
                    context, appWidgetId, ContentState.REQUIRES_CONFIG
                )
                return
            }

            val cached = WeatherWidgetStateManager.weatherState(appWidgetId)
            if (cached != null) {
                val contentState = when (cached) {
                    is WeatherState.Success -> ContentState.SUCCESS
                    is WeatherState.Error -> when (cached.type) {
                        WeatherState.ErrorType.NETWORK -> ContentState.NO_INTERNET
                        WeatherState.ErrorType.UNKNOWN -> ContentState.ERROR
                    }
                }
                WeatherWidgetStateManager.applyState(
                    context, appWidgetId, contentState, cached
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TAP_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val config = WidgetConfig.load(context, appWidgetId)
                    if (config == null) {
                        openConfigActivity(context, appWidgetId)
                        return
                    }

                    if (FramePumpService.currentPhase != PumpPhase.IDLE) {
                        return
                    }

                    if (WeatherWidgetStateManager.isUpdating(appWidgetId)) {
                        return
                    }

                    schedulePeriodicRefresh(context, appWidgetId)
                    refreshAndAnimate(context, appWidgetId)
                }

                return
            }

            ACTION_SILENT_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val config = WidgetConfig.load(context, appWidgetId)
                    if (config != null) {
                        WeatherWidgetStateManager.applyState(
                            context, appWidgetId, ContentState.UPDATING
                        )

                        WeatherRefreshWorker.enqueueImmediateRefresh(
                            context,
                            appWidgetId,
                            config.lat,
                            config.lon
                        )
                        schedulePeriodicRefresh(context, appWidgetId)
                    }
                }

                return
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    android.content.ComponentName(context, WeatherPillWidget::class.java)
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
        }
    }

    private fun refreshAndAnimate(
        context: Context,
        appWidgetId: Int,
        isUserInitiated: Boolean = true
    ) {
        var lastFraction = 0f
        val serviceIntent = Intent(context, FramePumpService::class.java).apply {
            action = BasePumpService.ACTION_MORPH_IN
            putExtra(FramePumpService.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        FramePumpService.onAnimationFrame = { _, fraction ->
            lastFraction = fraction
        }

        // @formatter:off
        FramePumpService.onPushFrameView = { views ->
            val spec = getSpecForPhase(FramePumpService.currentPhase)
            val t = spec.interpolator.getInterpolation(lastFraction)
            val containerScale = spec.containerScaleFrom + (spec.containerScaleTo - spec.containerScaleFrom) * t
            val infoScale = spec.infoScaleFrom + (spec.infoScaleTo - spec.infoScaleFrom) * t
            val opacity = spec.alphaFrom + (spec.alphaTo - spec.alphaFrom) * t
            views.setFloat(R.id.content_image, "setAlpha", opacity)
            views.setFloat(R.id.content_image, "setScaleX", infoScale)
            views.setFloat(R.id.content_image, "setScaleY", infoScale)
            views.setFloat(R.id.content_container, "setScaleX", containerScale)
            views.setFloat(R.id.content_container, "setScaleY", containerScale)
        }
        // @formatter:on

        val mainHandler = Handler(Looper.getMainLooper())

        if (isUserInitiated) {
            try {
                context.startForegroundService(serviceIntent)
            } catch (_: Exception) {
                scheduleServiceStart(context, appWidgetId, serviceIntent)
            }
        } else {
            WeatherWidgetStateManager.applyState(
                context, appWidgetId, ContentState.UPDATING
            )
        }

        Thread {
            try {
                val config = WidgetConfig.load(context, appWidgetId)

                if (config == null) {
                    mainHandler.post {
                        WeatherWidgetStateManager.applyState(
                            context, appWidgetId, ContentState.REQUIRES_CONFIG
                        )
                    }

                    return@Thread
                }

                val result = WeatherApi.fetchWeatherData(config.lat, config.lon)

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
                        context, appWidgetId,
                        result.temp, result.iconRes
                    )
                }

                mainHandler.post {
                    WeatherWidgetStateManager.applyState(
                        context, appWidgetId, contentState, result
                    )

                    if (isUserInitiated) {
                        WeatherWidgetStateManager.requestMorphOut(appWidgetId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshAndAnimate failed", e)

                mainHandler.post {
                    WeatherWidgetStateManager.applyState(
                        context, appWidgetId, ContentState.ERROR,
                        WeatherState.Error(WeatherState.ErrorType.UNKNOWN)
                    )

                    if (isUserInitiated) {
                        WeatherWidgetStateManager.requestMorphOut(appWidgetId)
                    }
                }
            }
        }.apply { name = "widget-init-$appWidgetId" }.start()
    }

    private fun scheduleServiceStart(
        context: Context,
        appWidgetId: Int,
        serviceIntent: Intent,
        delayMs: Long = 3000L
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            PendingIntent.getForegroundService(
                context, appWidgetId, serviceIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    private fun schedulePeriodicRefresh(context: Context, appWidgetId: Int) {
        val config = WidgetConfig.load(context, appWidgetId) ?: return
        WeatherRefreshWorker.enqueuePeriodicRefresh(
            context, appWidgetId, config.lat, config.lon
        )
    }

    private fun cancelPeriodicRefresh(context: Context, appWidgetId: Int) {
        WeatherRefreshWorker.cancelPeriodicRefresh(context, appWidgetId)
    }

    private fun openConfigActivity(context: Context, appWidgetId: Int) {
        val intent = Intent(context, WeatherConfigProxyActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "WidgetProvider"

        const val ACTION_TAP_REFRESH =
            "com.razvanalbu.material.not.you.widgets.TAP_REFRESH"
        const val ACTION_SILENT_REFRESH =
            "com.razvanalbu.material.not.you.widgets.SILENT_REFRESH"

    }
}
