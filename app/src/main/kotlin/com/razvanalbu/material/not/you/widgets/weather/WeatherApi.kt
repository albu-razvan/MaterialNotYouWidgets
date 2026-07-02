package com.razvanalbu.material.not.you.widgets.weather

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

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
            val timeseries = root.getJSONObject("properties").getJSONArray("timeseries")

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

            var high = temp
            var low = temp
            val count = minOf(timeseries.length(), 24)
            for (i in 0 until count) {
                val entry = timeseries.getJSONObject(i)
                val entryDetails = entry.getJSONObject("data")
                    .getJSONObject("instant").getJSONObject("details")
                val entryTemp = entryDetails.getDouble("air_temperature")
                if (entryTemp > high) high = entryTemp
                if (entryTemp < low) low = entryTemp
            }

            val df = DecimalFormat("#")

            WeatherState.Success(
                temp = "${df.format(temp)}\u00B0",
                high = "H:${df.format(high)}\u00B0",
                low = "L:${df.format(low)}\u00B0",
                condition = getConditionText(symbolCode),
                icon = getWeatherIcon(symbolCode)
            )
        } catch (_: Exception) {
            WeatherState.Error
        }
    }

    private fun getWeatherIcon(symbolCode: String): String {
        val code = symbolCode.replace("_day", "").replace("_night", "")
        return when (code) {
            "clearsky" -> "\u2600\uFE0F"
            "fair" -> "\uD83C\uDF24"
            "partlycloudy" -> "\u26C5"
            "cloudy" -> "\u2601\uFE0F"
            "fog" -> "\uD83C\uDF2B"
            "rain", "lightrain" -> "\uD83C\uDF26"
            "heavyrain" -> "\uD83C\uDF27"
            "rainshowers", "lightrainshowers" -> "\uD83C\uDF26"
            "heavyrainshowers" -> "\uD83C\uDF27"
            "snow", "lightsnow" -> "\uD83C\uDF28"
            "heavysnow" -> "\u2744\uFE0F"
            "snowshowers", "lightsnowshowers" -> "\uD83C\uDF28"
            "heavysnowshowers" -> "\u2744\uFE0F"
            "sleet", "lightsleet" -> "\uD83C\uDF27"
            "heavysleet" -> "\uD83C\uDF27"
            "sleetshowers", "lightsleetshowers" -> "\uD83C\uDF26"
            "heavysleetshowers" -> "\uD83C\uDF27"
            "thunder", "rainandthunder", "snowandthunder", "sleetandthunder" -> "\u26C8"
            else -> "\uD83C\uDF24"
        }
    }

    private fun getConditionText(symbolCode: String): String {
        val code = symbolCode.replace("_day", "").replace("_night", "")
        return when (code) {
            "clearsky" -> "Clear Sky"
            "fair" -> "Fair"
            "partlycloudy" -> "Partly Cloudy"
            "cloudy" -> "Cloudy"
            "fog" -> "Foggy"
            "rain" -> "Rain"
            "lightrain" -> "Light Rain"
            "heavyrain" -> "Heavy Rain"
            "rainshowers" -> "Rain Showers"
            "lightrainshowers" -> "Light Showers"
            "heavyrainshowers" -> "Heavy Showers"
            "snow" -> "Snow"
            "lightsnow" -> "Light Snow"
            "heavysnow" -> "Heavy Snow"
            "snowshowers" -> "Snow Showers"
            "lightsnowshowers" -> "Light Snow Showers"
            "heavysnowshowers" -> "Heavy Snow Showers"
            "sleet" -> "Sleet"
            "lightsleet" -> "Light Sleet"
            "heavysleet" -> "Heavy Sleet"
            "sleetshowers" -> "Sleet Showers"
            "lightsleetshowers" -> "Light Sleet Showers"
            "heavysleetshowers" -> "Heavy Sleet Showers"
            "thunder" -> "Thunder"
            "rainandthunder" -> "Rain & Thunder"
            "snowandthunder" -> "Snow & Thunder"
            "sleetandthunder" -> "Sleet & Thunder"
            else -> "Unknown"
        }
    }
}
