package com.razvanalbu.material.not.you.widgets.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.google.android.material.R as MaterialR
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.MorphingEngine
import com.razvanalbu.material.not.you.widgets.core.VariableFontProvider
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.ShapeType
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils

class WeatherPillWidget : AppWidgetProvider() {
    private val morphEngine = MorphingEngine()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            refreshWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        Log.d(TAG, "options changed for $appWidgetId")
        refreshWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_TAP_REFRESH == intent.action) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                if (FramePumpService.currentPhase != PumpPhase.IDLE) {
                    return
                }

                val shapeColor = computeShapeColor(context)
                refreshWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                startAnimation(context, appWidgetId, shapeColor)
            }
            return
        }
        super.onReceive(context, intent)
    }

    private fun startAnimation(context: Context, appWidgetId: Int, shapeColor: Int) {
        var lastFraction = 0f

        FramePumpService.onAnimationFrame = { _, fraction ->
            lastFraction = fraction
        }

        FramePumpService.onPushFrameView = { views ->
            val spec = getSpecForPhase(FramePumpService.currentPhase)

            val t = spec.interpolator.getInterpolation(lastFraction)

            val containerScale = spec.containerScaleFrom + (spec.containerScaleTo - spec.containerScaleFrom) * t
            val infoScale = spec.infoScaleFrom + (spec.infoScaleTo - spec.infoScaleFrom) * t
            val opacity = spec.alphaFrom + (spec.alphaTo - spec.alphaFrom) * t

            views.setFloat(R.id.weather_info_image, "setAlpha", opacity)
            views.setFloat(R.id.weather_info_image, "setScaleX", infoScale)
            views.setFloat(R.id.weather_info_image, "setScaleY", infoScale)
            views.setFloat(R.id.content_container, "setScaleX", containerScale)
            views.setFloat(R.id.content_container, "setScaleY", containerScale)
        }

        val morphInIntent = Intent(context, FramePumpService::class.java).apply {
            action = BasePumpService.ACTION_MORPH_IN
            putExtra(FramePumpService.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(FramePumpService.EXTRA_SHAPE_COLOR, shapeColor)
        }
        try {
            context.startForegroundService(morphInIntent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
        }

        Thread {
            try {
                val result = WeatherApi.fetchWeatherData()
                applyWeatherData(context, appWidgetId, result)
            } catch (e: Exception) {
                Log.e(TAG, "fetch failed", e)
                applyWeatherData(context, appWidgetId, WeatherState.Error)
            }

            val morphOutIntent = Intent(context, FramePumpService::class.java).apply {
                action = BasePumpService.ACTION_MORPH_OUT
            }
            try {
                context.startService(morphOutIntent)
            } catch (e: Exception) {
                Log.e(TAG, "morph out failed", e)
            }
        }.apply { name = "weather-fetch-$appWidgetId" }.start()
    }

    private fun applyWeatherData(context: Context, appWidgetId: Int, result: WeatherState) {
        try {
            val squarePx = WidgetUtils.getSquareSizePx(context, appWidgetId)
            val typeface = VariableFontProvider.get(
                context,
                rond = TYPEFACE_ROUNDNESS,
                wght = TYPEFACE_WEIGHT,
                wdth = TYPEFACE_WIDTH,
                grad = TYPEFACE_GRADE,
            )

            val (tempText, iconRes) = when (result) {
                is WeatherState.Success -> result.temp to result.iconRes
                else -> null to null
            }

            val icon = context.getDrawable(iconRes ?: 0)

            val weatherView = WeatherPillInfoView(context).apply {
                setTemperature(tempText)
                setIcon(icon)
                setTextColor(resolveTextColor(context))
                setTypeface(typeface)
            }

            val weatherFrame = weatherView.renderToBitmap(squarePx, squarePx)

            val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
            views.setInt(R.id.content_container, "setMinimumWidth", squarePx)
            views.setInt(R.id.content_container, "setMinimumHeight", squarePx)
            views.setImageViewBitmap(R.id.weather_info_image, weatherFrame)

            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "applyWeatherData failed", e)
        }
    }

    private fun refreshWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Thread {
            try {
                val shapeColor = computeShapeColor(context)
                val pillRadii = morphEngine.computeRadii(ShapeType.PILL)
                val squarePx = WidgetUtils.getSquareSizePx(context, appWidgetId)
                val backgroundFrame = morphEngine.renderRadiiToBitmap(
                    squarePx, squarePx, pillRadii, pillRadii, 0f, shapeColor, -45f
                )

                val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                views.setInt(R.id.content_container, "setMinimumWidth", squarePx)
                views.setInt(R.id.content_container, "setMinimumHeight", squarePx)

                setupTapIntent(context, views, appWidgetId)

                views.setImageViewBitmap(R.id.morph_image, backgroundFrame)
                manager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "refreshWidget failed", e)
            }
        }.apply { name = "widget-refresh-$appWidgetId" }.start()
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
        views.setOnClickPendingIntent(R.id.widget_root, pi)
    }

    private fun computeShapeColor(context: Context): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(
            android.R.attr.colorPrimary,
        ))
        val primary = ta.getColor(0, 0xFF6750A4.toInt())
        ta.recycle()
        return primary
    }

    private fun resolveTextColor(context: Context): Int {
        val ta = context.obtainStyledAttributes(
            intArrayOf(MaterialR.attr.colorOnSurface)
        )

        val color = ta.getColor(0, android.graphics.Color.WHITE)
        ta.recycle()

        return color
    }

    companion object {
        private const val TAG = "WidgetProvider"

        private const val TYPEFACE_ROUNDNESS = 100f
        private const val TYPEFACE_WEIGHT = 500f
        private const val TYPEFACE_WIDTH = 100f
        private const val TYPEFACE_GRADE = 20f

        const val ACTION_TAP_REFRESH =
            "com.razvanalbu.material.not.you.widgets.TAP_REFRESH"
    }
}
