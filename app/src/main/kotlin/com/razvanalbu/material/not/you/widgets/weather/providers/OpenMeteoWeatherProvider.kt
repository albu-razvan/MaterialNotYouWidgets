package com.razvanalbu.material.not.you.widgets.weather.providers

import com.razvanalbu.material.not.you.widgets.weather.WeatherState
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.round

internal object OpenMeteoWeatherProvider : WeatherProvider {
    private const val TIMEOUT = 10_000

    private fun buildUrl(lat: Double, lon: Double): String {
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto"
    }

    override fun fetchWeatherData(lat: Double, lon: Double): WeatherState {
        return try {
            val url = URL(buildUrl(lat, lon))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
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
            val current = root.getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val weatherCode = current.getInt("weather_code")
            val isDay = current.optBoolean("is_day", true)

            val symbolCode = wmoCodeToSymbolCode(weatherCode, isDay)
            val tempInt = round(temp).toInt()
            val iconRes = WeatherIconResolver.resolveIcon(symbolCode, tempInt)

            WeatherState.Success(temp = tempInt, iconRes = iconRes)
        } catch (_: Exception) {
            WeatherState.Error(WeatherState.ErrorType.UNKNOWN)
        }
    }

    private fun wmoCodeToSymbolCode(code: Int, isDay: Boolean): String {
        val base = when (code) {
            0 -> "clearsky"
            1 -> "fair"
            2 -> "partlycloudy"
            3 -> "cloudy"
            45, 48 -> "fog"
            51, 53, 55, 56, 57 -> "lightrain"
            61, 66 -> "lightrain"
            63, 67 -> "rain"
            65 -> "heavyrain"
            71, 77 -> "lightsnow"
            73 -> "snow"
            75 -> "heavysnow"
            80 -> "lightrainshowers"
            81 -> "rainshowers"
            82 -> "heavyrainshowers"
            85 -> "lightsnowshowers"
            86 -> "heavysnowshowers"
            95 -> "lightrainandthunder"
            96, 99 -> "heavyrainandthunder"
            else -> "cloudy"
        }
        return if (isDay) base else "${base}_night"
    }
}
