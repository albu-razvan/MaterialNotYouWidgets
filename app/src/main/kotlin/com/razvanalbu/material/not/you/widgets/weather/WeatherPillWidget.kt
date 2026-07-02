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
import com.razvanalbu.material.not.you.widgets.core.MorphingEngine
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
                if (FramePumpService.currentPhase != PumpPhase.IDLE) return
                val shapeColor = computeShapeColor(context)
                refreshWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
                startAnimation(context, appWidgetId, shapeColor)
            }
            return
        }
        super.onReceive(context, intent)
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
                val squarePx = squareSizePx(context, appWidgetId)
                val frame = morphEngine.renderRadiiToBitmap(
                    squarePx, squarePx, pillRadii, pillRadii, 0f, shapeColor, -45f
                )
                val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                views.setInt(R.id.content_container, "setMinimumWidth", squarePx)
                views.setInt(R.id.content_container, "setMinimumHeight", squarePx)
                setupTapIntent(context, views, appWidgetId)
                setupTextColors(context, views)
                setLoadingState(views)
                views.setImageViewBitmap(R.id.morph_image, frame)
                manager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "refreshWidget failed", e)
            }
        }.apply { name = "widget-refresh-$appWidgetId" }.start()
    }

    private fun squareSizePx(context: Context, appWidgetId: Int): Int =
        WidgetUtils.getSquareSizePx(context, appWidgetId)

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

    private fun setupTextColors(context: Context, views: RemoteViews) {
        val ta = context.obtainStyledAttributes(intArrayOf(
            MaterialR.attr.colorOnSurface,
            MaterialR.attr.colorOnSurfaceVariant,
            android.R.attr.colorPrimary,
        ))
        val onSurface = ta.getColor(0, android.graphics.Color.WHITE)
        val onSurfaceVariant = ta.getColor(1, android.graphics.Color.GRAY)
        val primary = ta.getColor(2, android.graphics.Color.BLACK)
        ta.recycle()

        views.setTextColor(R.id.location_text, onSurface)
        views.setTextColor(R.id.temp_text, onSurface)
        views.setTextColor(R.id.condition_text, onSurfaceVariant)
        views.setTextColor(R.id.high_low_text, primary)
    }

    private fun setLoadingState(views: RemoteViews) {
        views.setTextViewText(R.id.location_text, "Stockholm")
        views.setTextViewText(R.id.icon_text, "\u23F3")
        views.setTextViewText(R.id.temp_text, "--\u00B0")
        views.setTextViewText(R.id.condition_text, "Tap to load")
        views.setTextViewText(R.id.high_low_text, "")
    }

    private fun startAnimation(context: Context, appWidgetId: Int, shapeColor: Int) {
        val intent = Intent(context, FramePumpService::class.java).apply {
            putExtra(FramePumpService.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(FramePumpService.EXTRA_SHAPE_COLOR, shapeColor)
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
        }
    }

    private fun computeShapeColor(context: Context): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(
            android.R.attr.colorPrimary,
        ))
        val primary = ta.getColor(0, 0xFF6750A4.toInt())
        ta.recycle()
        return primary
    }

    companion object {
        private const val TAG = "WidgetProvider"
        const val ACTION_TAP_REFRESH =
            "com.razvanalbu.material.not.you.widgets.TAP_REFRESH"
    }
}
