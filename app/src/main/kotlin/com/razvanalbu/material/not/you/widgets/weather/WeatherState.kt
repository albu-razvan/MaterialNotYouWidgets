package com.razvanalbu.material.not.you.widgets.weather

sealed class WeatherState {
    data class Success(
        val temp: Int,
        val iconRes: Int,
    ) : WeatherState()

    data class Error(
        val type: ErrorType = ErrorType.UNKNOWN
    ) : WeatherState()

    enum class ErrorType {
        NETWORK,
        UNKNOWN
    }
}
