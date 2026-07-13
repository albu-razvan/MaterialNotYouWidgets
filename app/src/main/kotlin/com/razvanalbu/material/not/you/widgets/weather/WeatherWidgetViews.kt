package com.razvanalbu.material.not.you.widgets.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.PumpPhase

internal object WeatherWidgetViews {

    fun createBaseViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.weather_pill_layout)
        setTapRefreshIntent(context, views, appWidgetId)
        return views
    }

    fun showSync(views: RemoteViews) {
        views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        views.setImageViewResource(R.id.content_image, R.drawable.ic_sync)
    }

    fun showUnconfigured(views: RemoteViews) {
        views.setImageViewResource(R.id.morph_image, R.drawable.pill_shape)
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        views.setImageViewResource(R.id.content_image, R.drawable.ic_gear)
    }

    fun showSuccessUri(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        views.setImageViewUri(
            R.id.content_image,
            WidgetImageProvider.uri(context.packageName, appWidgetId)
        )
    }

    fun showError(views: RemoteViews, type: WeatherState.ErrorType) {
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        views.setImageViewResource(R.id.content_image, errorIcon(type))
    }

    fun showBitmap(views: RemoteViews, bitmap: Bitmap) {
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        views.setImageViewBitmap(R.id.content_image, bitmap)
    }

    fun requestMorphOutIfAnimating(appWidgetId: Int) {
        if (FramePumpService.currentPhase != PumpPhase.IDLE) {
            WeatherPillWidget.pendingMorphOut.add(appWidgetId)
            BasePumpService.requestMorphOut()
        }
    }

    fun errorIcon(type: WeatherState.ErrorType): Int {
        return if (type == WeatherState.ErrorType.NETWORK) {
            R.drawable.ic_no_internet
        } else {
            R.drawable.ic_error
        }
    }

    private fun setTapRefreshIntent(context: Context, views: RemoteViews, appWidgetId: Int) {
        views.setOnClickPendingIntent(
            R.id.content_container,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, WeatherPillWidget::class.java).apply {
                    action = WeatherPillWidget.ACTION_TAP_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
    }
}
