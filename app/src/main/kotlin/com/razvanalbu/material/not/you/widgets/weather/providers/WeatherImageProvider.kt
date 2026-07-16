package com.razvanalbu.material.not.you.widgets.weather.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import com.google.android.material.R
import com.razvanalbu.material.not.you.widgets.core.VariableFontProvider
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import com.razvanalbu.material.not.you.widgets.weather.WeatherState
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

class WidgetImageProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("Provider not initialized")

        val widgetId = extractWidgetId(uri)
        val generation = generationMap[widgetId] ?: 0
        val nightMode = currentNightMode(ctx)

        Log.d(TAG, "openFile widget=$widgetId generation=$generation")

        val state = WeatherWidgetStateManager.getOrRestoreWeatherState(ctx, widgetId) as? WeatherState.Success
            ?: throw FileNotFoundException("No weather data for widget $widgetId")

        val size = WidgetUtils.getSquareSizePx(ctx, widgetId)

        val bytes = getOrCreateImage(
            context = ctx,
            widgetId = widgetId,
            generation = generation,
            nightMode = nightMode,
            temp = state.temp,
            iconRes = state.iconRes,
            size = size
        )

        precacheOppositeTheme(
            ctx,
            widgetId,
            generation,
            state.temp,
            state.iconRes,
            size,
            nightMode
        )

        return createPipe(bytes, cacheKey(widgetId, nightMode, generation))
    }

    override fun getType(uri: Uri) = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?) = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ) = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ) = 0

    private fun extractWidgetId(uri: Uri): Int {
        val segments = uri.pathSegments

        if (segments.size < 2 || segments[0] != "render") {
            throw FileNotFoundException("Invalid URI: $uri")
        }

        return segments[1].toIntOrNull()
            ?: throw FileNotFoundException("Invalid widget ID")
    }

    private fun currentNightMode(context: Context): Int =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    private fun getOrCreateImage(
        context: Context,
        widgetId: Int,
        generation: Int,
        nightMode: Int,
        temp: Int,
        iconRes: Int,
        size: Int
    ): ByteArray {
        val key = cacheKey(widgetId, nightMode, generation)

        return pngCache.computeIfAbsent(key) {
            renderPng(themedContext(context, nightMode), temp, iconRes, size)
        }
    }

    private fun precacheOppositeTheme(
        context: Context,
        widgetId: Int,
        generation: Int,
        temp: Int,
        iconRes: Int,
        size: Int,
        currentNightMode: Int
    ) {
        val otherNightMode =
            if (currentNightMode == Configuration.UI_MODE_NIGHT_NO)
                Configuration.UI_MODE_NIGHT_YES
            else
                Configuration.UI_MODE_NIGHT_NO

        val key = cacheKey(widgetId, otherNightMode, generation)

        if (pngCache.containsKey(key)) {
            return
        }

        pngCache.putIfAbsent(
            key,
            renderPng(themedContext(context, otherNightMode), temp, iconRes, size)
        )
    }

    private fun createPipe(
        bytes: ByteArray,
        threadName: String
    ): ParcelFileDescriptor {
        val (readSide, writeSide) = ParcelFileDescriptor.createPipe()

        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use {
                    it.write(bytes)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pipe write failed", e)
            }
        }, "provider-serve-$threadName").start()

        return readSide
    }

    companion object {
        private const val AUTHORITY_SUFFIX = ".widgetimages"
        private const val TAG = "WidgetImageProvider"

        private val pngCache = ConcurrentHashMap<String, ByteArray>()
        private val generationMap = ConcurrentHashMap<Int, Int>()

        private fun cacheKey(
            widgetId: Int,
            nightMode: Int,
            generation: Int
        ) = "${widgetId}_content_${nightMode}_g$generation"

        fun nextGeneration(widgetId: Int) {
            generationMap.merge(widgetId, 1) { old, _ -> old + 1 }
        }

        fun invalidateCache(widgetId: Int) {
            pngCache.keys.removeAll {
                it.startsWith("${widgetId}_")
            }
        }

        @JvmStatic
        fun getCachedBitmap(context: Context, widgetId: Int): Bitmap? {
            val generation = generationMap[widgetId] ?: 0
            val baseNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val key = cacheKey(widgetId, baseNightMode, generation)
            val bytes = pngCache[key] ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        fun precache(
            context: Context,
            widgetId: Int,
            temp: Int,
            iconRes: Int
        ) {
            val generation = generationMap[widgetId] ?: 0
            val size = WidgetUtils.getSquareSizePx(context, widgetId)

            listOf(
                Configuration.UI_MODE_NIGHT_YES,
                Configuration.UI_MODE_NIGHT_NO
            ).forEach { nightMode ->

                val key = cacheKey(widgetId, nightMode, generation)

                if (pngCache.containsKey(key)) {
                    return@forEach
                }

                pngCache[key] = renderPng(
                    themedContext(context, nightMode),
                    temp,
                    iconRes,
                    size
                )
            }
        }

        fun uri(packageName: String, widgetId: Int): Uri {
            val generation = generationMap[widgetId] ?: 0
            return Uri.Builder()
                .scheme("content")
                .authority(packageName + AUTHORITY_SUFFIX)
                .path("render/$widgetId/content")
                .appendQueryParameter("g", generation.toString())
                .build()
        }

        private fun renderPng(
            context: Context,
            temp: Int,
            iconRes: Int,
            size: Int
        ): ByteArray {
            val bitmap = renderMerged(context, temp, iconRes, size, size)

            return ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                stream.toByteArray()
            }
        }

        private fun themedContext(context: Context, nightMode: Int): Context {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }

            return context.createConfigurationContext(config)
        }
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

    val typeface = VariableFontProvider.get(
        context,
        wght = 500f,
        wdth = 100f,
        grad = 20f,
        rond = 100f
    )

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
