package com.razvanalbu.material.not.you.widgets

sealed class WeatherState {
    data class Success(
        val temp: String,
        val high: String,
        val low: String,
        val condition: String,
        val icon: String,
    ) : WeatherState()

    data object Error : WeatherState()
}
