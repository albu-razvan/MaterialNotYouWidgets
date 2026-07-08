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
                refreshAndAnimate(context, appWidgetManager,
                    appWidgetId, isUserInitiated = false)
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

        BasePumpService.updateWidgetSize(appWidgetId,
            WidgetUtils.getSquareSizePx(context, appWidgetId))

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
                    animHandler.postDelayed({
                        refreshAndAnimate(context, AppWidgetManager.getInstance(context), appWidgetId, isUserInitiated = false)
                    }, 500L)
                }
                return
            }

        }

        super.onReceive(context, intent)
    }

    private fun refreshAndAnimate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        startAction: String = BasePumpService.ACTION_MORPH_IN,
        isUserInitiated: Boolean = true
    ) {
        val serviceIntent = Intent(context, FramePumpService::class.java).apply {
            action = startAction
            putExtra(FramePumpService.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        var lastFraction = 0f

        FramePumpService.onAnimationFrame = { _, fraction ->
            lastFraction = fraction
        }

        FramePumpService.onPushFrameView = { v ->
            val spec = getSpecForPhase(FramePumpService.currentPhase)
            val t = spec.interpolator.getInterpolation(lastFraction)
            val containerScale = spec.containerScaleFrom + (spec.containerScaleTo - spec.containerScaleFrom) * t
            val infoScale = spec.infoScaleFrom + (spec.infoScaleTo - spec.infoScaleFrom) * t
            val opacity = spec.alphaFrom + (spec.alphaTo - spec.alphaFrom) * t
            v.setFloat(R.id.content_image, "setAlpha", opacity)
            v.setFloat(R.id.content_image, "setScaleX", infoScale)
            v.setFloat(R.id.content_image, "setScaleY", infoScale)
            v.setFloat(R.id.content_container, "setScaleX", containerScale)
            v.setFloat(R.id.content_container, "setScaleY", containerScale)
        }

        val mainHandler = Handler(Looper.getMainLooper())

        if (isUserInitiated) {
            val initialViews = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            setupTapIntent(context, initialViews, appWidgetId)
            initialViews.setImageViewUri(R.id.content_image,
                WidgetImageProvider.uri(context.packageName, appWidgetId))
            initialViews.setViewVisibility(R.id.loading_image, View.GONE)
            manager.updateAppWidget(appWidgetId, initialViews)

            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                scheduleServiceStart(context, appWidgetId, serviceIntent, 3000)
            }
        } else {
            loadingAnimations.remove(appWidgetId)?.let { animHandler.removeCallbacks(it) }

            val loadingViews = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            setupTapIntent(context, loadingViews, appWidgetId)
            loadingViews.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
            loadingViews.setViewVisibility(R.id.content_image, View.GONE)
            loadingViews.setViewVisibility(R.id.loading_image, View.VISIBLE)
            loadingViews.setImageViewResource(R.id.loading_image, R.drawable.loading_spinner)
            loadingViews.setImageViewUri(R.id.content_image,
                WidgetImageProvider.uri(context.packageName, appWidgetId))
            manager.updateAppWidget(appWidgetId, loadingViews)

            val rotationUpdater = object : Runnable {
                var deg = 0f
                override fun run() {
                    val v = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                    v.setFloat(R.id.loading_image, "setRotation", deg)
                    manager.updateAppWidget(appWidgetId, v)
                    deg = (deg + 15f) % 360f
                    if (loadingAnimations[appWidgetId] === this) {
                        animHandler.postDelayed(this, 50L)
                    }
                }
            }
            loadingAnimations[appWidgetId] = rotationUpdater
            animHandler.post(rotationUpdater)
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
                    dataViews.setViewVisibility(R.id.content_image, View.GONE)
                    dataViews.setViewVisibility(R.id.loading_image, View.VISIBLE)
                    dataViews.setImageViewResource(R.id.loading_image, R.drawable.loading_spinner)
                    dataViews.setImageViewUri(R.id.content_image,
                        WidgetImageProvider.uri(context.packageName, appWidgetId))
                }
                manager.updateAppWidget(appWidgetId, dataViews)

                val result = WeatherApi.fetchWeatherData(config.lat, config.lon)

                WidgetImageProvider.nextGeneration(appWidgetId)
                WidgetImageProvider.invalidateCache(appWidgetId)

                mainHandler.post {
                    applyWeatherData(context, appWidgetId, result)
                    if (FramePumpService.currentPhase != PumpPhase.IDLE) {
                        pendingMorphOut.add(appWidgetId)
                        BasePumpService.requestMorphOut()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "refreshAndAnimate failed", e)
                mainHandler.post {
                    applyWeatherData(context, appWidgetId, WeatherState.Error)
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
            loadingAnimations.remove(appWidgetId)?.let { animHandler.removeCallbacks(it) }

            val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            setupTapIntent(context, views, appWidgetId)
            if (FramePumpService.currentPhase == PumpPhase.IDLE) {
                views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
            }

            if (result is WeatherState.Success) {
                views.setViewVisibility(R.id.content_image, View.VISIBLE)
                views.setImageViewUri(R.id.content_image,
                    WidgetImageProvider.uri(context.packageName, appWidgetId))
            }

            views.setViewVisibility(R.id.loading_image, View.GONE)

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
        views.setViewVisibility(R.id.content_image, View.GONE)
        views.setViewVisibility(R.id.loading_image, View.VISIBLE)
        views.setImageViewResource(R.id.loading_image, R.drawable.ic_gear)
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
        delayMs: Long
    ) {
        val pi = PendingIntent.getForegroundService(
            context, appWidgetId, serviceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMs,
            pi
        )
    }

    private fun setupTapIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
        val tapIntent = Intent(context, WeatherPillWidget::class.java).apply {
            action = ACTION_TAP_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pi = PendingIntent.getBroadcast(
            context, appWidgetId, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        views.setOnClickPendingIntent(R.id.content_container, pi)
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
        private val loadingAnimations = mutableMapOf<Int, Runnable>()
    }
}
