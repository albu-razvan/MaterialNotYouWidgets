package com.razvanalbu.material.not.you.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import java.util.concurrent.Executors

class WeatherPillWidget : AppWidgetProvider() {

    private val executor = Executors.newSingleThreadExecutor()
    private val morphEngine = MorphingEngine()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        executor.submit {
            val colorScheme = resolveColorScheme(context)
            val shapeColor = computeShapeColor(colorScheme)
            val pillRadii = morphEngine.computeRadii(ShapeType.PILL)
            val frame = morphEngine.renderRadiiToBitmap(
                400, 200, pillRadii, pillRadii, 0f, shapeColor, 0f
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                setupTapIntent(context, views, appWidgetId)
                setupTextColors(context, views, colorScheme)
                setLoadingState(views)
                views.setImageViewBitmap(R.id.morph_image, frame)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_TAP_REFRESH == intent.action) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                executor.submit {
                    val manager = AppWidgetManager.getInstance(context)
                    val colorScheme = resolveColorScheme(context)
                    val shapeColor = computeShapeColor(colorScheme)
                    val pillRadii = morphEngine.computeRadii(ShapeType.PILL)
                    val frame = morphEngine.renderRadiiToBitmap(
                        400, 200, pillRadii, pillRadii, 0f, shapeColor, 0f
                    )

                    val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
                    setupTapIntent(context, views, appWidgetId)
                    setupTextColors(context, views, colorScheme)
                    setLoadingState(views)
                    views.setImageViewBitmap(R.id.morph_image, frame)
                    manager.updateAppWidget(appWidgetId, views)

                    startAnimation(context, appWidgetId, shapeColor)
                }
            }
            return
        }
        super.onReceive(context, intent)
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

    private fun setupTextColors(context: Context, views: RemoteViews, colorScheme: androidx.compose.material3.ColorScheme) {
        views.setTextColor(R.id.location_text, colorScheme.onSurface.toArgb())
        views.setTextColor(R.id.temp_text, colorScheme.onSurface.toArgb())
        views.setTextColor(R.id.condition_text, colorScheme.onSurfaceVariant.toArgb())
        views.setTextColor(R.id.high_low_text, colorScheme.primary.toArgb())
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
        } catch (_: Exception) {
        }
    }

    private fun computeShapeColor(colorScheme: androidx.compose.material3.ColorScheme): Int {
        val argb = colorScheme.tertiaryContainer.toArgb()
        return android.graphics.Color.argb(
            (android.graphics.Color.alpha(argb) * 0.4).toInt(),
            android.graphics.Color.red(argb),
            android.graphics.Color.green(argb),
            android.graphics.Color.blue(argb)
        )
    }

    private fun resolveColorScheme(context: Context) =
        if ((context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    companion object {
        const val ACTION_TAP_REFRESH =
            "com.razvanalbu.material.not.you.widgets.TAP_REFRESH"
    }
}
