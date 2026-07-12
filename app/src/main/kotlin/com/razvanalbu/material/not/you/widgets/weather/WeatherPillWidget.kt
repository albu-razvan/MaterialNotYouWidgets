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
import android.view.View
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
                    schedulePeriodicRefresh(context, appWidgetId)
                    animHandler.postDelayed({
                        refreshAndAnimate(
                            context,
                            AppWidgetManager.getInstance(context),
                            appWidgetId,
                            isUserInitiated = false
                        )
                    }, 500L)
                }

                return
            }

            ACTION_PERIODIC_REFRESH -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    schedulePeriodicRefresh(context, appWidgetId)

                    refreshAndAnimate(
                        context,
                        AppWidgetManager.getInstance(context),
                        appWidgetId,
                        isUserInitiated = false
                    )
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
            val initialViews = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            setupTapIntent(context, initialViews, appWidgetId)


            initialViews.setImageViewUri(
                R.id.content_image,
                WidgetImageProvider.uri(context.packageName, appWidgetId)
            )

            Log.d(TAG, "[$appWidgetId] content_image <- URI (user-initiated)")

            manager.updateAppWidget(appWidgetId, initialViews)

            try {
                context.startForegroundService(serviceIntent)
            } catch (_: Exception) {
                scheduleServiceStart(context, appWidgetId, serviceIntent)
            }
        } else {
            val loadingViews = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            setupTapIntent(context, loadingViews, appWidgetId)

            loadingViews.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
            loadingViews.setViewVisibility(R.id.content_image, View.VISIBLE)
            loadingViews.setImageViewResource(R.id.content_image, R.drawable.ic_sync)

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

                val dataViews = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                setupTapIntent(context, dataViews, appWidgetId)

                if (!isUserInitiated) {
                    dataViews.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
                    dataViews.setViewVisibility(R.id.content_image, View.VISIBLE)
                    dataViews.setImageViewResource(R.id.content_image, R.drawable.ic_sync)

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
                        val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                        setupTapIntent(context, views, appWidgetId)

                        views.setViewVisibility(R.id.content_image, View.VISIBLE)
                        views.setImageViewBitmap(R.id.content_image, bitmap)

                        Log.d(TAG, "[$appWidgetId] content_image <- bitmap (applySuccess)")

                        bitmap.recycle()

                        lastWeatherState[appWidgetId] = result
                        AppWidgetManager.getInstance(context)
                            .updateAppWidget(appWidgetId, views)

                        if (FramePumpService.currentPhase != PumpPhase.IDLE) {
                            pendingMorphOut.add(appWidgetId)
                            BasePumpService.requestMorphOut()
                        }
                    }
                } else {
                    postApplyAndFinish(mainHandler, isUserInitiated) {
                        applyWeatherData(context, appWidgetId, result)

                        if (FramePumpService.currentPhase != PumpPhase.IDLE) {
                            pendingMorphOut.add(appWidgetId)
                            BasePumpService.requestMorphOut()
                        }
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

                    if (FramePumpService.currentPhase != PumpPhase.IDLE) {
                        pendingMorphOut.add(appWidgetId)
                        BasePumpService.requestMorphOut()
                    }
                }
            }
        }.apply { name = "widget-init-$appWidgetId" }.start()
    }

    private fun applyWeatherData(context: Context, appWidgetId: Int, result: WeatherState) {
        try {
            val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            setupTapIntent(context, views, appWidgetId)

            if (FramePumpService.currentPhase == PumpPhase.IDLE) {
                views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
            }

            if (result is WeatherState.Success) {
                views.setViewVisibility(R.id.content_image, View.VISIBLE)
                views.setImageViewUri(
                    R.id.content_image,
                    WidgetImageProvider.uri(context.packageName, appWidgetId)
                )

                Log.d(TAG, "[$appWidgetId] content_image <- URI (applySuccess)")
            } else if (result is WeatherState.Error) {
                val errorIcon =
                    if (result.type == WeatherState.ErrorType.NETWORK) R.drawable.ic_no_internet
                    else R.drawable.ic_error

                views.setViewVisibility(R.id.content_image, View.VISIBLE)
                views.setImageViewResource(R.id.content_image, errorIcon)

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
        val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
        setupTapIntent(context, views, appWidgetId)

        views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        views.setImageViewResource(R.id.content_image, R.drawable.ic_gear)

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
        val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
        setupTapIntent(context, views, appWidgetId)

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
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + REFRESH_INTERVAL_MS,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, WeatherPillWidget::class.java).apply {
                    action = ACTION_PERIODIC_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }

    private fun cancelPeriodicRefresh(context: Context, appWidgetId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId,
            Intent(context, WeatherPillWidget::class.java).apply {
                action = ACTION_PERIODIC_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun setupTapIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setOnClickPendingIntent(
            R.id.content_container,
            PendingIntent.getBroadcast(
                context, appWidgetId,
                Intent(context, WeatherPillWidget::class.java).apply {
                    action = ACTION_TAP_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
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
        const val ACTION_PERIODIC_REFRESH =
            "com.razvanalbu.material.not.you.widgets.PERIODIC_REFRESH"

        private const val REFRESH_INTERVAL_MS = 1800000L

        internal val pendingMorphOut = mutableSetOf<Int>()
        internal val lastWeatherState = ConcurrentHashMap<Int, WeatherState>()

        private val animHandler = Handler(Looper.getMainLooper())
    }
}
