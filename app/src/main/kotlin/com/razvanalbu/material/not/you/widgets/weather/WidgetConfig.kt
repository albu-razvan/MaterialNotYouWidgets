package com.razvanalbu.material.not.you.widgets.weather

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

const val PROVIDER_MET_NO = "metno"
const val PROVIDER_OPEN_METEO = "openmeteo"

internal object WidgetConfig {
    private const val PREFS_NAME = "weather_widget_config"
    private const val KEY_LAT = "lat_"
    private const val KEY_LON = "lon_"
    private const val KEY_LOCATION = "location_"
    private const val KEY_PROVIDER = "provider_"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    data class LocationConfig(
        val lat: Double,
        val lon: Double,
        val displayName: String,
        val provider: String = PROVIDER_MET_NO,
    )

    fun save(context: Context, widgetId: Int, config: LocationConfig) {
        prefs(context).edit {
            putFloat(KEY_LAT + widgetId, config.lat.toFloat())
                .putFloat(KEY_LON + widgetId, config.lon.toFloat())
                .putString(KEY_LOCATION + widgetId, config.displayName)
                .putString(KEY_PROVIDER + widgetId, config.provider)
        }
    }

    fun load(context: Context, widgetId: Int): LocationConfig? {
        val prefs = prefs(context)
        val lat = prefs.getFloat(KEY_LAT + widgetId, Float.NaN)
        val lon = prefs.getFloat(KEY_LON + widgetId, Float.NaN)
        val name = prefs.getString(KEY_LOCATION + widgetId, null) ?: return null
        if (lat.isNaN() || lon.isNaN()) return null
        val provider = prefs.getString(KEY_PROVIDER + widgetId, PROVIDER_MET_NO)
            ?: PROVIDER_MET_NO
        return LocationConfig(lat.toDouble(), lon.toDouble(), name, provider)
    }
}
