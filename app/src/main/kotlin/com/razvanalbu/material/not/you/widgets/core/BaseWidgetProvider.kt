package com.razvanalbu.material.not.you.widgets.core

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

abstract class BaseWidgetProvider : AppWidgetProvider() {
    companion object {
        private val implementations = mutableMapOf<Class<out BaseWidgetProvider>, Class<*>>()

        fun resolveConfigurationActivity(
            context: Context,
            appWidgetId: Int
        ): Class<*>? {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            for ((providerClass, configActivity) in implementations) {
                if (appWidgetId in appWidgetManager.getAppWidgetIds(
                        ComponentName(context, providerClass)
                    )
                ) {
                    return configActivity
                }
            }

            return null
        }
    }

    init {
        implementations[this::class.java] = getConfigurationActivity()
    }

    abstract fun getConfigurationActivity(): Class<*>
}