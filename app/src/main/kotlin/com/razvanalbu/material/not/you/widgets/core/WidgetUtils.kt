package com.razvanalbu.material.not.you.widgets.core

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build

object WidgetUtils {

    fun getSizePx(
        context: Context,
        widgetId: Int,
        fallbackWidth: Int = 400,
        fallbackHeight: Int = 300
    ): Pair<Int, Int> {
        if (widgetId < 0) return Pair(fallbackWidth, fallbackHeight)
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)

        if (Build.VERSION.SDK_INT >= 33) {
            val sizes = options.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                android.util.SizeF::class.java
            )
            if (!sizes.isNullOrEmpty()) {
                val density = context.resources.displayMetrics.density
                return Pair(
                    (sizes[0].width * density + 0.5f).toInt(),
                    (sizes[0].height * density + 0.5f).toInt()
                )
            }
        }

        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (maxW > 0 && maxH > 0) {
            val density = context.resources.displayMetrics.density
            return Pair(
                (maxW * density + 0.5f).toInt(),
                (maxH * density + 0.5f).toInt()
            )
        }

        return Pair(fallbackWidth, fallbackHeight)
    }

    fun getSquareSizePx(context: Context, widgetId: Int, fallback: Int = 400): Int {
        val (w, h) = getSizePx(context, widgetId, fallback, fallback)

        return minOf(w, h)
    }
}
