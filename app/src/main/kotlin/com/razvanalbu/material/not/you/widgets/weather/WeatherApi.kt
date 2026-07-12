package com.razvanalbu.material.not.you.widgets.weather

import com.razvanalbu.material.not.you.widgets.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.round

internal object WeatherApi {
    private const val USER_AGENT = "MaterialNotYouWidgets/1.0"
    private const val TIMEOUT = 10_000

    private fun buildUrl(lat: Double, lon: Double): String {
        return "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon"
    }

    fun fetchWeatherData(lat: Double, lon: Double): WeatherState {
        return try {
            val url = URL(buildUrl(lat, lon))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()

            parseWeather(response)
        } catch (_: IOException) {
            WeatherState.Error(WeatherState.ErrorType.NETWORK)
        } catch (_: Exception) {
            WeatherState.Error(WeatherState.ErrorType.UNKNOWN)
        }
    }

    private fun parseWeather(json: String): WeatherState {
        return try {
            val root = JSONObject(json)
            val timeseries = root.getJSONObject("properties")
                .getJSONArray("timeseries")

            val current = timeseries.getJSONObject(0)
            val details = current.getJSONObject("data")
                .getJSONObject("instant").getJSONObject("details")
            val temp = details.getDouble("air_temperature")

            var symbolCode = "cloudy"
            val data = current.getJSONObject("data")
            if (data.has("next_1_hours")) {
                symbolCode = data.getJSONObject("next_1_hours")
                    .getJSONObject("summary").getString("symbol_code")
            } else if (data.has("next_6_hours")) {
                symbolCode = data.getJSONObject("next_6_hours")
                    .getJSONObject("summary").getString("symbol_code")
            }

            val baseCode = symbolCode.replace("_day", "")
                .replace("_night", "")
                .replace("_polartwilight", "")
            val tempInt = round(temp).toInt()
            var iconRes = iconResForCode(symbolCode)
            val isClearish = baseCode in setOf("clearsky", "fair", "partlycloudy")
            if (isClearish) {
                iconRes = when {
                    tempInt > 30 -> R.drawable.very_hot
                    tempInt < -5 -> R.drawable.very_cold
                    else -> iconRes
                }
            }

            WeatherState.Success(
                temp = tempInt,
                iconRes = iconRes
            )
        } catch (_: Exception) {
            WeatherState.Error(WeatherState.ErrorType.UNKNOWN)
        }
    }

    private fun iconResForCode(symbolCode: String): Int {
        val isNight = symbolCode.endsWith("_night")
        val code = symbolCode.replace("_day", "")
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
