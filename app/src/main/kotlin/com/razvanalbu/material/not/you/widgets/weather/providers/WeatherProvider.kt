package com.razvanalbu.material.not.you.widgets.weather.providers

import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.weather.PROVIDER_OPEN_METEO
import com.razvanalbu.material.not.you.widgets.weather.WeatherState

interface WeatherProvider {
    fun fetchWeatherData(lat: Double, lon: Double): WeatherState
}

internal object WeatherProviders {
    private val instances = mutableMapOf<String, CachedWeatherProvider>()

    fun get(providerId: String): WeatherProvider = instances.getOrPut(providerId) {
        val delegate: WeatherProvider = when (providerId) {
            PROVIDER_OPEN_METEO -> OpenMeteoWeatherProvider
            else -> MetNoWeatherProvider
        }

        CachedWeatherProvider(providerId, delegate)
    }
}

internal object WeatherIconResolver {
    fun resolveIcon(symbolCode: String, temp: Int): Int {
        val baseCode = symbolCode
            .replace("_day", "")
            .replace("_night", "")
            .replace("_polartwilight", "")
        var iconRes = iconResForCode(symbolCode)
        if (baseCode in setOf("clearsky", "fair", "partlycloudy")) {
            iconRes = when {
                temp > 30 -> R.drawable.very_hot
                temp < -5 -> R.drawable.very_cold
                else -> iconRes
            }
        }
        return iconRes
    }

    fun iconResForCode(symbolCode: String): Int {
        val isNight = symbolCode.endsWith("_night")
        val code = symbolCode
            .replace("_day", "")
            .replace("_night", "")
            .replace("_polartwilight", "")

        return when (code) {
            "clearsky" -> if (isNight) R.drawable.clear_night else R.drawable.sunny
            "fair" -> if (isNight) R.drawable.mostly_clear_night else R.drawable.mostly_sunny
            "partlycloudy" -> if (isNight) R.drawable.partly_cloudy_night else R.drawable.partly_cloudy
            "cloudy" -> R.drawable.cloudy
            "fog" -> R.drawable.fog
            "lightrain", "rain" -> R.drawable.drizzle
            "heavyrain" -> R.drawable.heavy_rain
            "lightrainshowers" -> R.drawable.sunny_with_rain
            "rainshowers" -> R.drawable.rain_with_cloudy
            "heavyrainshowers" -> R.drawable.cloudy_with_rain
            "lightsnow", "snow" -> R.drawable.flurries
            "heavysnow" -> R.drawable.heavy_snow
            "lightsnowshowers", "snowshowers" -> R.drawable.showers_snow
            "heavysnowshowers" -> R.drawable.snow_with_cloudy
            "lightsleet", "sleet", "heavysleet" -> R.drawable.sleet_hail
            "lightsleetshowers", "sleetshowers" -> R.drawable.rain_with_snow
            "heavysleetshowers" -> R.drawable.heavy_snow
            "lightrainandthunder", "rainandthunder" -> R.drawable.thunderstorms
            "heavyrainandthunder" -> R.drawable.strong_thunderstorms
            "lightrainshowersandthunder", "rainshowersandthunder" -> R.drawable.thunderstorms
            "heavyrainshowersandthunder" -> R.drawable.strong_thunderstorms
            "lightsleetandthunder", "sleetandthunder" -> R.drawable.thunderstorms
            "heavysleetandthunder" -> R.drawable.strong_thunderstorms
            "lightsleetshowersandthunder", "sleetshowersandthunder" -> R.drawable.thunderstorms
            "heavysleetshowersandthunder" -> R.drawable.strong_thunderstorms
            "lightssleetshowersandthunder" -> R.drawable.thunderstorms
            "lightsnowandthunder", "snowandthunder" -> R.drawable.thundersnow
            "heavysnowandthunder" -> R.drawable.strong_thunderstorms
            "lightsnowshowersandthunder", "snowshowersandthunder" -> R.drawable.thundersnow
            "heavysnowshowersandthunder" -> R.drawable.strong_thunderstorms
            "lightssnowshowersandthunder" -> R.drawable.thundersnow
            else -> R.drawable.cloudy
        }
    }
}
