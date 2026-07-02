package com.razvanalbu.material.not.you.widgets.core

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build

object WidgetUtils {

    fun getSquareSizePx(context: Context, widgetId: Int, fallback: Int = 400): Int {
        if (widgetId < 0) return fallback
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)

        val squareDp = if (Build.VERSION.SDK_INT >= 33) {
            val sizes = options.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                android.util.SizeF::class.java
            )
            if (!sizes.isNullOrEmpty()) {
                minOf(sizes[0].width, sizes[0].height).toInt()
            } else 0
        } else 0

        if (squareDp > 0) {
            return (squareDp * context.resources.displayMetrics.density + 0.5f).toInt()
        }

        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (maxW > 0 && maxH > 0) {
            val dp = minOf(maxW, maxH)
            return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        }

        val displayMetrics = context.resources.displayMetrics
        val screenDp = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density
        val cappedDp = minOf(screenDp.toInt(), 600)
        return (cappedDp * displayMetrics.density + 0.5f).toInt()
    }
}
