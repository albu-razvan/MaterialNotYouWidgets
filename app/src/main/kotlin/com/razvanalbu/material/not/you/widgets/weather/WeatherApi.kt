package com.razvanalbu.material.not.you.widgets.weather

import com.razvanalbu.material.not.you.widgets.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.round

internal object WeatherApi {
    private const val MET_API_URL =
        "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=59.3293&lon=18.0686"
    private const val USER_AGENT = "MaterialNotYouWidgets/1.0"

    fun fetchWeatherData(): WeatherState {
        return try {
            val url = URL(MET_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()

            parseWeather(response)
        } catch (_: Exception) {
            WeatherState.Error
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

            WeatherState.Success(
                temp = round(temp).toInt(),
                iconRes = iconResForCode(symbolCode)
            )
        } catch (_: Exception) {
            WeatherState.Error
        }
    }

    private fun iconResForCode(symbolCode: String): Int {
        val isNight = symbolCode.endsWith("_night")
        val code = symbolCode.replace("_day", "")
            .replace("_night", "")

        return when (code) {
            "clearsky" -> if (isNight) R.drawable.clear_night else R.drawable.clear_day
            "fair" -> if (isNight) R.drawable.mostly_clear_night else R.drawable.mostly_clear_day
            "partlycloudy" -> if (isNight) R.drawable.partly_cloudy_night else R.drawable.partly_cloudy_day
            "cloudy" -> R.drawable.cloudy
            "fog" -> R.drawable.haze_fog_dust_smoke
            "rain", "lightrain" -> R.drawable.drizzle
            "heavyrain" -> R.drawable.heavy_rain
            "rainshowers", "lightrainshowers" -> if (isNight) R.drawable.scattered_showers_night else R.drawable.scattered_showers_day
            "heavyrainshowers" -> R.drawable.heavy_rain
            "snow", "lightsnow" -> R.drawable.flurries
            "heavysnow" -> R.drawable.heavy_snow
            "snowshowers", "lightsnowshowers" -> if (isNight) R.drawable.scattered_snow_showers_night else R.drawable.scattered_snow_showers_day
            "heavysnowshowers" -> R.drawable.heavy_snow
            "sleet", "heavysleet" -> R.drawable.sleet_hail
            "lightsleet", "sleetshowers", "lightsleetshowers" -> R.drawable.mixed_rain_hail_sleet
            "heavysleetshowers" -> R.drawable.heavy_snow
            "thunder" -> R.drawable.isolated_thunderstorms
            "rainandthunder", "snowandthunder", "sleetandthunder" ->
                if (isNight) R.drawable.isolated_scattered_thunderstorms_night else R.drawable.isolated_scattered_thunderstorms_day
            else -> R.drawable.cloudy
        }
    }
}
