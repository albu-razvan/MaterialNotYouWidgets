package com.razvanalbu.material.not.you.widgets.weather

import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import com.google.android.material.R as MaterialR
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.ShapeType

class FramePumpService : BasePumpService() {

    override val contentContainerId: Int = R.id.content_container
    override val layoutResId: Int = R.layout.weather_pill_layout
    override val morphImageViewId: Int = R.id.morph_image

    override val notificationDescription: String = "Shows morph animation when widget is tapped"
    override val notificationChannelName: String = "Widget Animation"
    override val notificationChannelId: String = CHANNEL_ID

    override val startRadii: FloatArray = morphEngine.computeRadii(ShapeType.PILL)
    override val endRadii: FloatArray = morphEngine.computeRadii(ShapeType.COOKIE)

    override fun getNotificationTitle(): String = "Weather Widget"
    override fun getNotificationText(): String = "Loading weather..."

    override fun fetchData(): Any = WeatherApi.fetchWeatherData()

    override fun onAnimationComplete() {
        val result = getFetchResult()

        val finalFrame = morphEngine.renderRadiiToBitmap(
            squarePx, squarePx, startRadii, startRadii,
            0f, shapeColor, -45f
        )

        val views = RemoteViews(packageName, layoutResId)
        views.setInt(contentContainerId, "setMinimumWidth", squarePx)
        views.setInt(contentContainerId, "setMinimumHeight", squarePx)
        views.setImageViewBitmap(morphImageViewId, finalFrame)

        val ta = obtainStyledAttributes(
            intArrayOf(
                MaterialR.attr.colorOnSurface,
                MaterialR.attr.colorOnSurfaceVariant,
                android.R.attr.colorPrimary,
            )
        )

        val onSurface = ta.getColor(0, android.graphics.Color.WHITE)
        val onSurfaceVariant = ta.getColor(1, android.graphics.Color.GRAY)
        val primary = ta.getColor(2, android.graphics.Color.BLACK)

        ta.recycle()

        views.setTextColor(R.id.location_text, onSurface)
        views.setTextColor(R.id.temp_text, onSurface)
        views.setTextColor(R.id.condition_text, onSurfaceVariant)
        views.setTextColor(R.id.high_low_text, primary)

        when (result) {
            is WeatherState.Success -> {
                views.setTextViewText(R.id.location_text, "Stockholm")
                views.setTextViewText(R.id.icon_text, result.icon)
                views.setTextViewText(R.id.temp_text, result.temp)
                views.setTextViewText(R.id.condition_text, result.condition)
                views.setTextViewText(R.id.high_low_text, "${result.high}  ${result.low}")
            }

            else -> {
                views.setTextViewText(R.id.location_text, "Stockholm")
                views.setTextViewText(R.id.icon_text, "\u26A0\uFE0F")
                views.setTextViewText(R.id.temp_text, "--\u00B0")
                views.setTextViewText(R.id.condition_text, "Unable to load")
                views.setTextViewText(R.id.high_low_text, "")
            }
        }

        AppWidgetManager.getInstance(this)
            .updateAppWidget(widgetId, views)

        lastBitmap?.recycle()
        lastBitmap = finalFrame
    }

    companion object {
        private const val CHANNEL_ID = "widget_morph_animation"

        @JvmStatic
        val currentPhase: PumpPhase
            get() = BasePumpService.currentPhase

        @JvmStatic
        val EXTRA_APPWIDGET_ID: String
            get() = BasePumpService.EXTRA_APPWIDGET_ID

        @JvmStatic
        val EXTRA_SHAPE_COLOR: String
            get() = BasePumpService.EXTRA_SHAPE_COLOR
    }
}
