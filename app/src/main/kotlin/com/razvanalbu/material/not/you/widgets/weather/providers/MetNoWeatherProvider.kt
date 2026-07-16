package com.razvanalbu.material.not.you.widgets.weather.providers

import com.razvanalbu.material.not.you.widgets.weather.WeatherState
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.round

internal object MetNoWeatherProvider : WeatherProvider {
    private const val USER_AGENT = "MaterialNotYouWidgets/1.0"
    private const val TIMEOUT = 10_000

    private fun buildUrl(lat: Double, lon: Double): String {
        return "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$lat&lon=$lon"
    }

    override fun fetchWeatherData(lat: Double, lon: Double): WeatherState {
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

            val tempInt = round(temp).toInt()
            val iconRes = WeatherIconResolver.resolveIcon(symbolCode, tempInt)

            WeatherState.Success(temp = tempInt, iconRes = iconRes)
        } catch (_: Exception) {
            WeatherState.Error(WeatherState.ErrorType.UNKNOWN)
        }
    }
}
