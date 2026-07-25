package com.razvanalbu.material.not.you.widgets.weather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.BaseWidgetProvider
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.WidgetConfigProxyActivity
import java.util.concurrent.TimeUnit

import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager.ContentState
import com.razvanalbu.material.not.you.widgets.weather.providers.WeatherProviders
import com.razvanalbu.material.not.you.widgets.weather.providers.WidgetImageProvider

class WeatherPillWidgetProvider : BaseWidgetProvider() {

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
            WidgetImageProvider.nextGeneration(appWidgetId)
            WidgetImageProvider.invalidateCache(appWidgetId)

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
        } else {
            FramePumpService.resetContentState()
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

            Intent.ACTION_BOOT_COMPLETED -> {
                val appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, WeatherPillWidgetProvider::class.java)
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
        var effectiveUserInitiated = isUserInitiated

        if (effectiveUserInitiated) {
            FramePumpService.resetServiceStartLatch()
            try {
                context.startForegroundService(serviceIntent)
            } catch (_: Exception) {
                effectiveUserInitiated = false
                WeatherWidgetStateManager.applyState(
                    context, appWidgetId, ContentState.UPDATING
                )
            }
        } else {
            WeatherWidgetStateManager.applyState(
                context, appWidgetId, ContentState.UPDATING
            )
        }

        Thread {
            val serviceStarted = effectiveUserInitiated &&
                    FramePumpService.serviceStartLatch.await(5L, TimeUnit.SECONDS) &&
                    FramePumpService.serviceStarted

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

                val provider = WeatherProviders.get(config.provider)
                val result = provider.fetchWeatherData(config.lat, config.lon)

                val contentState = when (result) {
                    is WeatherState.Success -> ContentState.SUCCESS
                    is WeatherState.Error -> when (result.type) {
                        WeatherState.ErrorType.NETWORK -> ContentState.NO_INTERNET
                        WeatherState.ErrorType.UNKNOWN -> ContentState.ERROR
                    }
                }

                if (result is WeatherState.Success) {
                    WeatherWidgetStateManager.cacheAndPersistWeatherState(
                        context, appWidgetId, result
                    )
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

                    if (serviceStarted) {
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

                    if (serviceStarted) {
                        WeatherWidgetStateManager.requestMorphOut(appWidgetId)
                    }
                }
            }
        }.apply { name = "widget-init-$appWidgetId" }.start()
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
        val intent = Intent(context, WidgetConfigProxyActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    override fun getConfigurationActivity(): Class<*> {
        return WeatherConfigureActivity::class.java
    }

    companion object {
        private const val TAG = "WidgetProvider"

        const val ACTION_TAP =
            "com.razvanalbu.material.not.you.widgets.weather.TAP"

    }
}
