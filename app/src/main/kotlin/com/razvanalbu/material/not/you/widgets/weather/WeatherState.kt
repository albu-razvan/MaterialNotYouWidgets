package com.razvanalbu.material.not.you.widgets.weather

sealed class WeatherState {
    data class Success(
        val temp: Int,
        val iconRes: Int,
    ) : WeatherState()

    data object Error : WeatherState()
}
