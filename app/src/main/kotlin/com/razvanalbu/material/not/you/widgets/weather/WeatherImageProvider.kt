package com.razvanalbu.material.not.you.widgets.weather

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.ContextThemeWrapper
import androidx.core.graphics.createBitmap
import com.razvanalbu.material.not.you.widgets.core.VariableFontProvider
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

class WidgetImageProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("Provider not initialized")
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != "render") {
            throw FileNotFoundException("Invalid URI: $uri")
        }

        val widgetId = segments[1].toIntOrNull()
            ?: throw FileNotFoundException("Invalid widget ID in URI")
        val nightMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val generation = uri.getQueryParameter("g")?.toIntOrNull() ?: 0
        val cacheKey = "${widgetId}_content_${nightMode}_g$generation"
        val state = WeatherPillWidget.lastWeatherState[widgetId] as? WeatherState.Success
            ?: throw FileNotFoundException("No data for widget $widgetId")
        val width = WidgetUtils.getSquareSizePx(ctx, widgetId)

        val bytes = pngCache.computeIfAbsent(cacheKey) { _ ->
            val bitmap = renderMerged(ctx, state.temp, state.iconRes, width, width)
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                bitmap.recycle()
                baos.toByteArray()
            }
        }

        val otherNight = if (nightMode == (Configuration.UI_MODE_NIGHT_NO shl 4))
            Configuration.UI_MODE_NIGHT_YES shl 4
        else
            Configuration.UI_MODE_NIGHT_NO shl 4
        val otherKey = "${widgetId}_content_${otherNight}_g$generation"
        if (!pngCache.containsKey(otherKey)) {
            val otherCfg = Configuration(ctx.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or otherNight
            }
            val otherCtx = ctx.createConfigurationContext(otherCfg)
            val otherBmp = renderMerged(otherCtx, state.temp, state.iconRes, width, width)
            val otherBytes = ByteArrayOutputStream().use { baos ->
                otherBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                otherBmp.recycle()
                baos.toByteArray()
            }
            pngCache.putIfAbsent(otherKey, otherBytes)
        }

        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                    out.write(bytes)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pipe write failed", e)
            }
        }.apply { name = "provider-serve-$cacheKey" }.start()
        return readSide
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        private const val AUTHORITY_SUFFIX = ".widgetimages"
        private const val TAG = "WidgetImageProvider"

        private val pngCache = ConcurrentHashMap<String, ByteArray>()
        private val generationMap = ConcurrentHashMap<Int, Int>()

        fun nextGeneration(widgetId: Int) {
            generationMap.merge(widgetId, 1) { old, _ -> old + 1 }
        }

        fun invalidateCache(widgetId: Int) {
            pngCache.keys.removeAll { it.startsWith("${widgetId}_") }
        }

        fun uri(packageName: String, widgetId: Int): Uri {
            val gen = generationMap[widgetId] ?: 0
            return Uri.Builder()
                .scheme("content")
                .authority(packageName + AUTHORITY_SUFFIX)
                .path("render/$widgetId/content")
                .appendQueryParameter("g", gen.toString())
                .build()
        }
    }
}

private fun renderMerged(ctx: Context, temp: Int, iconRes: Int, width: Int, height: Int): Bitmap {
    val minDim = minOf(width, height).toFloat()
    val isLarge = temp >= 100 || temp <= -10

    val tf = VariableFontProvider.get(ctx, wght = 500f, wdth = 100f, grad = 20f, rond = 100f)

    val wrapper = ContextThemeWrapper(ctx, com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight)
    val ta = wrapper.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorOnSurface))
    val textColor = ta.getColor(0, 0xFF1C1B1F.toInt())
    ta.recycle()

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        this.typeface = tf
        textAlign = Paint.Align.CENTER
        textSize = minDim * (if (isLarge) 0.3f else 0.33f)
    }

    val w = width.toFloat()
    val fm = paint.fontMetrics
    val textX = if (isLarge) w * 0.54f else w * 0.57f
    val textY = -fm.ascent * if (isLarge) 1.7f else 1.5f

    val iconSize = (minDim * 0.32f).toInt()
    val iconLeft = (width * 0.26f).toInt()
    val iconTop = (height * 0.55f).toInt()

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    canvas.drawText("$temp\u00B0", textX, textY, paint)

    val iconDrawable = ctx.getDrawable(iconRes)
    if (iconDrawable != null) {
        iconDrawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        iconDrawable.draw(canvas)
    }
    return bitmap
}
