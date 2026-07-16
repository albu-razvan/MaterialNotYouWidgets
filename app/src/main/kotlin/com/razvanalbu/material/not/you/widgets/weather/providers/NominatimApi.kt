package com.razvanalbu.material.not.you.widgets.weather.providers

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.pow

internal object NominatimApi {

    private const val BASE_URL = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "MaterialNotYouWidgets/1.0"

    data class GeocodingResult(
        val displayName: String,
        val lat: Double,
        val lon: Double,
        val type: String,
        val placeRank: Int,
        val importance: Double,

        // The actual matched place
        val name: String,

        // Parent locality (only city-level)
        val city: String?,
        val state: String?,
        val country: String?,
        val countryCode: String?,
    )

    fun search(
        query: String,
        countryCode: String? = null
    ): List<GeocodingResult> {

        if (query.isBlank()) return emptyList()

        val params = mutableListOf(
            "q" to query.trim(),
            "format" to "jsonv2",
            "limit" to "30",
            "addressdetails" to "1",
            "namedetails" to "1",
            "dedupe" to "1",
            "accept-language" to preferredLanguages()
        )

        countryCode?.let {
            params += "countrycodes" to it.lowercase(Locale.US)
        }

        val url = params.joinToString("&") {
            "${encode(it.first)}=${encode(it.second)}"
        }

        val connection =
            URL("$BASE_URL/search?$url")
                .openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 10000
            readTimeout = 10000
        }

        return try {
            BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { reader ->

                val json = JSONArray(reader.readText())

                (0 until json.length())
                    .mapNotNull {
                        parseResult(json.getJSONObject(it))
                    }
                    .filter {
                        typePriority(it.type) > 0
                    }
                    .distinctBy {
                        dedupeKey(it)
                    }
                    .sortedWith(
                        compareBy<GeocodingResult> {
                            typePriority(it.type)
                        }
                            .thenBy {
                                it.importance
                            }
                    )
                    .take(10)
            }

        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResult(
        obj: JSONObject
    ): GeocodingResult? {

        val address = obj.optJSONObject("address")
            ?: return null

        val place = firstNonBlank(
            address.optString("suburb"),
            address.optString("neighbourhood"),
            address.optString("quarter"),
            address.optString("city_district"),
            address.optString("borough"),
            address.optString("residential"),
            address.optString("district"),
            address.optString("island"),
            address.optString("city"),
            address.optString("town"),
            address.optString("village"),
            address.optString("municipality"),
            address.optString("hamlet")
        ) ?: return null

        val city = firstNonBlank(
            address.optString("city"),
            address.optString("town"),
            address.optString("municipality"),
            address.optString("village")
        )

        val state = firstNonBlank(
            address.optString("state"),
            address.optString("county"),
            address.optString("province"),
            address.optString("region")
        )

        return GeocodingResult(
            displayName = buildDisplayName(address),

            lat = obj.optString("lat").toDoubleOrNull() ?: return null,
            lon = obj.optString("lon").toDoubleOrNull() ?: return null,

            type = obj.optString("type"),
            placeRank = obj.optInt("place_rank"),
            importance = obj.optDouble("importance"),

            name = place,
            city = city,
            state = state,

            country = address.optString("country")
                .takeIf { it.isNotBlank() },

            countryCode = address.optString("country_code")
                .takeIf { it.isNotBlank() }
        )
    }

    private fun buildDisplayName(
        address: JSONObject
    ): String {

        val place = firstNonBlank(
            address.optString("suburb"),
            address.optString("neighbourhood"),
            address.optString("quarter"),
            address.optString("city_district"),
            address.optString("borough"),
            address.optString("residential"),
            address.optString("district"),
            address.optString("island"),
            address.optString("city"),
            address.optString("town"),
            address.optString("village"),
            address.optString("municipality"),
            address.optString("hamlet")
        )

        val city = firstNonBlank(
            address.optString("city"),
            address.optString("town"),
            address.optString("municipality"),
            address.optString("village")
        )

        val state = firstNonBlank(
            address.optString("state"),
            address.optString("county"),
            address.optString("province"),
            address.optString("region")
        )

        val country = address.optString("country")
            .takeIf { it.isNotBlank() }

        return listOfNotNull(
            place,
            city.takeIf { it != place },
            state.takeIf { it != city && it != place },
            country
        )
            .distinct()
            .joinToString(", ")
    }

    private fun dedupeKey(
        result: GeocodingResult
    ): String {

        return listOf(
            result.name,
            result.city,
            result.state,
            result.country,
            result.lat.round(4),
            result.lon.round(4),
            result.displayName.lowercase(Locale.ROOT),
        )
            .joinToString("|")
            .lowercase(Locale.ROOT)
    }

    private fun typePriority(type: String): Int =
        when (type) {
            "city" -> 100
            "town" -> 95
            "borough" -> 90
            "suburb" -> 85
            "city_district" -> 80
            "district" -> 75
            "neighbourhood" -> 70
            "quarter" -> 65
            "residential" -> 60
            "village" -> 55
            "municipality" -> 50
            "hamlet" -> 45
            "island" -> 40
            "administrative" -> 30
            else -> 0
        }

    private fun preferredLanguages(): String {
        val locale = Locale.getDefault()

        return listOf(
            locale.toLanguageTag(),
            locale.language
        )
            .distinct()
            .joinToString(",")
    }

    private fun firstNonBlank(
        vararg values: String
    ): String? {
        return values.firstOrNull {
            it.isNotBlank()
        }
    }

    private fun encode(
        value: String
    ): String {
        return URLEncoder.encode(
            value,
            Charsets.UTF_8.name()
        )
    }

    private fun Double.round(
        decimals: Int
    ): Double {
        val factor = 10.0.pow(decimals)
        return kotlin.math.round(this * factor) / factor
    }
}