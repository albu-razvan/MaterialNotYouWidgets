package com.razvanalbu.material.not.you.widgets.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager.ContentState

internal object WeatherWidgetViews {

    fun applyContentState(views: RemoteViews, context: Context, appWidgetId: Int, state: ContentState) {
        views.setViewVisibility(R.id.content_image, View.VISIBLE)
        when (state) {
            ContentState.REQUIRES_CONFIG -> {
                views.setImageViewResource(R.id.content_image, R.drawable.ic_gear)
            }
            ContentState.UPDATING -> {
                views.setImageViewResource(R.id.content_image, R.drawable.ic_sync)
            }
            ContentState.NO_INTERNET -> {
                views.setImageViewResource(R.id.content_image, R.drawable.ic_no_internet)
            }
            ContentState.ERROR -> {
                views.setImageViewResource(R.id.content_image, R.drawable.ic_error)
            }
            ContentState.SUCCESS -> {
                views.setImageViewUri(
                    R.id.content_image,
                    WidgetImageProvider.uri(context.packageName, appWidgetId)
                )
            }
        }
    }

    fun applyContentStateBitmap(views: RemoteViews, context: Context, appWidgetId: Int, state: ContentState) {
        when (state) {
            ContentState.SUCCESS -> {
                val bitmap = WidgetImageProvider.getCachedBitmap(context, appWidgetId)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.content_image, bitmap)
                } else {
                    applyContentState(views, context, appWidgetId, state)
                }
            }
            else -> applyContentState(views, context, appWidgetId, state)
        }
    }

    fun createResetViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.weather_pill)
        setTapRefreshIntent(context, views, appWidgetId)
        views.setImageViewResource(R.id.morph_image, BasePumpService.getMorphShapeRes(appWidgetId))
        return views
    }

    fun createViews(context: Context, appWidgetId: Int, state: ContentState): RemoteViews {
        val views = createResetViews(context, appWidgetId)
        applyContentState(views, context, appWidgetId, state)
        return views
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
