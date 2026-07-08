package com.razvanalbu.material.not.you.widgets.weather

import android.content.Context
import android.content.SharedPreferences

internal object WidgetConfig {
    private const val PREFS_NAME = "weather_widget_config"
    private const val KEY_LAT = "lat_"
    private const val KEY_LON = "lon_"
    private const val KEY_LOCATION = "location_"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    data class LocationConfig(
        val lat: Double,
        val lon: Double,
        val displayName: String,
    )

    fun save(context: Context, widgetId: Int, config: LocationConfig) {
        prefs(context).edit()
            .putFloat(KEY_LAT + widgetId, config.lat.toFloat())
            .putFloat(KEY_LON + widgetId, config.lon.toFloat())
            .putString(KEY_LOCATION + widgetId, config.displayName)
            .apply()
    }

    fun load(context: Context, widgetId: Int): LocationConfig? {
        val p = prefs(context)
        val lat = p.getFloat(KEY_LAT + widgetId, Float.NaN)
        val lon = p.getFloat(KEY_LON + widgetId, Float.NaN)
        val name = p.getString(KEY_LOCATION + widgetId, null) ?: return null
        if (lat.isNaN() || lon.isNaN()) return null
        return LocationConfig(lat.toDouble(), lon.toDouble(), name)
    }
}
