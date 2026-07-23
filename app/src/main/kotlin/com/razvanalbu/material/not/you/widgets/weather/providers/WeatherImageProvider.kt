package com.razvanalbu.material.not.you.widgets.weather.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.net.Uri
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.text.util.LocalePreferences
import com.google.android.material.R
import com.razvanalbu.material.not.you.widgets.core.BaseWidgetImageProvider
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import com.razvanalbu.material.not.you.widgets.R as AppR
import com.razvanalbu.material.not.you.widgets.weather.WeatherState
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager
import java.io.FileNotFoundException

class WidgetImageProvider : BaseWidgetImageProvider() {

    override val authoritySuffix = ".widgetimages"
    override val cacheKeyPrefix = "content"
    override val logTag = "WidgetImageProvider"

    override fun getWidgetDimensions(context: Context, widgetId: Int): Pair<Int, Int> {
        val size = WidgetUtils.getSquareSizePx(context, widgetId)

        return Pair(size, size)
    }

    override fun renderContent(context: Context, widgetId: Int, width: Int, height: Int): Bitmap {
        val state = WeatherWidgetStateManager.getOrRestoreWeatherState(
            context, widgetId
        ) as? WeatherState.Success
            ?: throw FileNotFoundException("No weather data for widget $widgetId")

        val displayTemp = if (isSystemFahrenheit()) celsiusToFahrenheit(state.temp) else state.temp
        return renderMerged(context, displayTemp, state.iconRes, width, height)
    }

    companion object {
        fun nextGeneration(widgetId: Int) = BaseWidgetImageProvider.nextGeneration(widgetId)

        fun invalidateCache(widgetId: Int) = BaseWidgetImageProvider.invalidateCache(widgetId)

        fun getCachedBitmap(context: Context, widgetId: Int): Bitmap? =
            getCachedBitmap(context, widgetId, "content")

        fun uri(packageName: String, widgetId: Int): Uri =
            uri(packageName, widgetId, ".widgetimages")

        fun precache(context: Context, widgetId: Int, temp: Int, iconRes: Int) {
            val generation = currentGeneration(widgetId)
            val size = WidgetUtils.getSquareSizePx(context, widgetId)
            val displayTemp = if (isSystemFahrenheit()) celsiusToFahrenheit(temp) else temp
            listOf(
                android.content.res.Configuration.UI_MODE_NIGHT_YES,
                android.content.res.Configuration.UI_MODE_NIGHT_NO
            ).forEach { nightMode ->
                val key = cacheKey(widgetId, nightMode, generation, "content")
                if (containsPng(key)) return@forEach
                val ctx = themedContext(context, nightMode)
                val bitmap = renderMerged(ctx, displayTemp, iconRes, size, size)
                storePng(key, compressPng(bitmap))
            }
        }

        private fun isSystemFahrenheit(): Boolean =
            LocalePreferences.getTemperatureUnit() == LocalePreferences.TemperatureUnit.FAHRENHEIT

        private fun celsiusToFahrenheit(celsius: Int): Int =
            (celsius * 9 / 5 + 32)
    }
}

internal fun renderMerged(
    context: Context,
    temp: Int,
    iconRes: Int,
    width: Int,
    height: Int
): Bitmap {

    val minDimension = minOf(width, height).toFloat()
    val largeTemperature = temp >= 100 || temp <= -10

    val typeface = ResourcesCompat.getFont(context, AppR.font.google_sans_flex_weather_subset)
        ?: Typeface.DEFAULT

    val themedContext = ContextThemeWrapper(
        context,
        R.style.Theme_Material3Expressive_DynamicColors_DayNight
    )

    val attributes = themedContext.obtainStyledAttributes(
        intArrayOf(R.attr.colorOnSurface)
    )

    val textColor = attributes.getColor(0, 0xFF1C1B1F.toInt())
    attributes.recycle()

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
        textSize = minDimension * if (largeTemperature) 0.30f else 0.33f
    }

    val metrics = paint.fontMetrics

    val textX = width * if (largeTemperature) 0.54f else 0.57f
    val textY = -metrics.ascent * if (largeTemperature) 1.7f else 1.5f

    val iconSize = (minDimension * 0.32f).toInt()
    val iconLeft = (width * 0.26f).toInt()
    val iconTop = (height * 0.55f).toInt()

    return createBitmap(width, height).also { bitmap ->
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawText("$temp°", textX, textY, paint)

        AppCompatResources.getDrawable(context, iconRes)?.apply {
            setBounds(
                iconLeft,
                iconTop,
                iconLeft + iconSize,
                iconTop + iconSize
            )
            draw(canvas)
        }
    }
}
