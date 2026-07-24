package com.razvanalbu.material.not.you.widgets.weather.providers

import com.razvanalbu.material.not.you.widgets.weather.WeatherState

internal class CachedWeatherProvider(
    private val providerId: String,
    private val delegate: WeatherProvider,
) : WeatherProvider {

    private data class CacheEntry(
        val state: WeatherState,
        val timestamp: Long,
    )

    private val cache = mutableMapOf<String, CacheEntry>()
    private val lock = Any()

    override fun fetchWeatherData(lat: Double, lon: Double): WeatherState {
        val startTime = System.currentTimeMillis()
        val key = cacheKey(lat, lon)

        synchronized(lock) {
            val entry = cache[key]
            if (entry != null && System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
                val elapsed = System.currentTimeMillis() - startTime
                sleepToMinimum(elapsed)
                return entry.state
            }
        }

        val result = delegate.fetchWeatherData(lat, lon)
        val elapsed = System.currentTimeMillis() - startTime

        synchronized(lock) {
            if (result is WeatherState.Success) {
                cache[key] = CacheEntry(result, System.currentTimeMillis())
            }
        }

        sleepToMinimum(elapsed)
        return result
    }

    private fun cacheKey(lat: Double, lon: Double): String = "$providerId:$lat:$lon"

    private fun sleepToMinimum(elapsed: Long) {
        val remaining = MIN_DURATION_MS - elapsed
        if (remaining > 0) {
            Thread.sleep(remaining)
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val MIN_DURATION_MS = 300L
    }
}
