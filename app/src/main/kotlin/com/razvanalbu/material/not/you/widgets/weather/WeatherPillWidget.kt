package com.razvanalbu.material.not.you.widgets.weather

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils

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
                showUnconfiguredState(context, appWidgetManager, appWidgetId)
            } else {
                refreshAndAnimate(
                    context, appWidgetManager,
                    appWidgetId, isUserInitiated = false
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
                showUnconfiguredState(context, appWidgetManager, appWidgetId)
                return
            }

            val cached = lastWeatherState[appWidgetId]
            if (cached != null) {
                applyWeatherData(context, appWidgetId, cached)
            } else {
                refreshWidget(context, appWidgetManager, appWidgetId)
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

                    schedulePeriodicRefresh(context, appWidgetId)
                    refreshAndAnimate(context, AppWidgetManager.getInstance(context), appWidgetId)
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
                        val loadingViews = WeatherWidgetViews.createBaseViews(context, appWidgetId)
                        WeatherWidgetViews.showSync(loadingViews)

                        AppWidgetManager.getInstance(context)
                            .updateAppWidget(appWidgetId, loadingViews)

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
        manager: AppWidgetManager,
        appWidgetId: Int,
        startAction: String = BasePumpService.ACTION_MORPH_IN,
        isUserInitiated: Boolean = true
    ) {
        var lastFraction = 0f
        val serviceIntent = Intent(context, FramePumpService::class.java).apply {
            action = startAction
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
            val initialViews = WeatherWidgetViews.createBaseViews(context, appWidgetId)
            WeatherWidgetViews.showSuccessUri(context, initialViews, appWidgetId)

            Log.d(TAG, "[$appWidgetId] content_image <- URI (user-initiated)")

            manager.updateAppWidget(appWidgetId, initialViews)

            try {
                context.startForegroundService(serviceIntent)
            } catch (_: Exception) {
                scheduleServiceStart(context, appWidgetId, serviceIntent)
            }
        } else {
            val loadingViews = WeatherWidgetViews.createBaseViews(context, appWidgetId)
            WeatherWidgetViews.showSync(loadingViews)

            Log.d(TAG, "[$appWidgetId] content_image <- sync")

            manager.updateAppWidget(appWidgetId, loadingViews)
        }

        Thread {
            try {
                val config = WidgetConfig.load(context, appWidgetId)

                if (config == null) {
                    mainHandler.post {
                        showUnconfiguredState(context, manager, appWidgetId)
                    }

                    return@Thread
                }

                val dataViews = WeatherWidgetViews.createBaseViews(context, appWidgetId)

                if (!isUserInitiated) {
                    WeatherWidgetViews.showSync(dataViews)

                    Log.d(TAG, "[$appWidgetId] content_image <- sync (thread)")
                }

                manager.updateAppWidget(appWidgetId, dataViews)

                val result = WeatherApi.fetchWeatherData(config.lat, config.lon)

                WidgetImageProvider.nextGeneration(appWidgetId)
                WidgetImageProvider.invalidateCache(appWidgetId)

                if (result is WeatherState.Success) {
                    val size = WidgetUtils.getSquareSizePx(context, appWidgetId)
                    val bitmap = renderMerged(context, result.temp, result.iconRes, size, size)

                    WidgetImageProvider.precache(context, appWidgetId, result.temp, result.iconRes)

                    postApplyAndFinish(mainHandler, isUserInitiated) {
                        val views = WeatherWidgetViews.createBaseViews(context, appWidgetId)

                        WeatherWidgetViews.showBitmap(views, bitmap)

                        Log.d(TAG, "[$appWidgetId] content_image <- bitmap (applySuccess)")

                        bitmap.recycle()

                        lastWeatherState[appWidgetId] = result
                        AppWidgetManager.getInstance(context)
                            .updateAppWidget(appWidgetId, views)

                        WeatherWidgetViews.requestMorphOutIfAnimating(appWidgetId)
                    }
                } else {
                    postApplyAndFinish(mainHandler, isUserInitiated) {
                        applyWeatherData(context, appWidgetId, result)
                        WeatherWidgetViews.requestMorphOutIfAnimating(appWidgetId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshAndAnimate failed", e)

                postApplyAndFinish(mainHandler, isUserInitiated) {
                    applyWeatherData(
                        context,
                        appWidgetId,
                        WeatherState.Error(WeatherState.ErrorType.UNKNOWN)
                    )
                    WeatherWidgetViews.requestMorphOutIfAnimating(appWidgetId)
                }
            }
        }.apply { name = "widget-init-$appWidgetId" }.start()
    }

    private fun applyWeatherData(context: Context, appWidgetId: Int, result: WeatherState) {
        try {
            val views = WeatherWidgetViews.createBaseViews(context, appWidgetId)

            if (FramePumpService.currentPhase == PumpPhase.IDLE) {
                views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
            }

            if (result is WeatherState.Success) {
                WeatherWidgetViews.showSuccessUri(context, views, appWidgetId)

                Log.d(TAG, "[$appWidgetId] content_image <- URI (applySuccess)")
            } else if (result is WeatherState.Error) {
                WeatherWidgetViews.showError(views, result.type)

                Log.d(
                    TAG,
                    "[$appWidgetId] content_image <- ${if (result.type == WeatherState.ErrorType.NETWORK) "ic_no_internet" else "ic_error"} (applyError)"
                )
            }

            lastWeatherState[appWidgetId] = result
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "applyWeatherData failed", e)
        }
    }

    private fun showUnconfiguredState(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = WeatherWidgetViews.createBaseViews(context, appWidgetId)
        WeatherWidgetViews.showUnconfigured(views)

        Log.d(TAG, "[$appWidgetId] content_image <- ic_gear")

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun openConfigActivity(context: Context, appWidgetId: Int) {
        val intent = Intent(context, WeatherConfigProxyActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    private fun refreshWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = WeatherWidgetViews.createBaseViews(context, appWidgetId)

        views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)

        manager.updateAppWidget(appWidgetId, views)
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

    private fun postApplyAndFinish(
        mainHandler: Handler,
        isUserInitiated: Boolean,
        applyAndFinish: () -> Unit
    ) {
        mainHandler.post {
            if (isUserInitiated && FramePumpService.currentPhase == PumpPhase.MORPH_IN) {
                animHandler.post(object : Runnable {
                    override fun run() {
                        if (FramePumpService.currentPhase == PumpPhase.MORPH_IN) {
                            animHandler.postDelayed(this, 10)
                        } else {
                            applyAndFinish()
                        }
                    }
                })
            } else {
                applyAndFinish()
            }
        }
    }

    companion object {
        private const val TAG = "WidgetProvider"

        const val ACTION_TAP_REFRESH =
            "com.razvanalbu.material.not.you.widgets.TAP_REFRESH"
        const val ACTION_SILENT_REFRESH =
            "com.razvanalbu.material.not.you.widgets.SILENT_REFRESH"

        internal val pendingMorphOut = mutableSetOf<Int>()
        internal val lastWeatherState = ConcurrentHashMap<Int, WeatherState>()

        private val animHandler = Handler(Looper.getMainLooper())
    }
}
