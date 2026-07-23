package com.razvanalbu.material.not.you.widgets.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

abstract class BaseWidgetImageProvider : ContentProvider() {

    protected abstract val authoritySuffix: String
    protected abstract val cacheKeyPrefix: String
    protected abstract val logTag: String

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("Provider not initialized")

        val widgetId = extractWidgetId(uri)
        val generation = generationMap[widgetId] ?: 0
        val nightMode = currentNightMode(ctx)

        Log.d(logTag, "openFile widget=$widgetId generation=$generation")

        val (width, height) = getWidgetDimensions(ctx, widgetId)
        val key = cacheKey(widgetId, nightMode, generation, cacheKeyPrefix)

        val bytes = pngCache.computeIfAbsent(key) {
            renderPng(themedContext(ctx, nightMode), widgetId, width, height)
        }

        precacheOppositeTheme(ctx, widgetId, generation, width, height, nightMode)

        return createPipe(bytes, key)
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

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

    protected abstract fun renderContent(context: Context, widgetId: Int, width: Int, height: Int): Bitmap

    protected open fun getWidgetDimensions(context: Context, widgetId: Int): Pair<Int, Int> {
        val size = WidgetUtils.getSquareSizePx(context, widgetId)
        return Pair(size, size)
    }

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

    private fun renderPng(context: Context, widgetId: Int, width: Int, height: Int): ByteArray {
        val bitmap = renderContent(context, widgetId, width, height)
        return compressPng(bitmap)
    }

    private fun precacheOppositeTheme(
        context: Context,
        widgetId: Int,
        generation: Int,
        width: Int,
        height: Int,
        currentNightMode: Int
    ) {
        val otherNightMode =
            if (currentNightMode == Configuration.UI_MODE_NIGHT_NO)
                Configuration.UI_MODE_NIGHT_YES
            else
                Configuration.UI_MODE_NIGHT_NO

        val key = cacheKey(widgetId, otherNightMode, generation, cacheKeyPrefix)
        if (pngCache.containsKey(key)) {
            return
        }

        pngCache.putIfAbsent(
            key,
            renderPng(themedContext(context, otherNightMode), widgetId, width, height)
        )
    }

    private fun createPipe(bytes: ByteArray, threadName: String): ParcelFileDescriptor {
        val (readSide, writeSide) = ParcelFileDescriptor.createPipe()
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use {
                    it.write(bytes)
                }
            } catch (e: Exception) {
                Log.w(logTag, "Pipe write failed", e)
            }
        }, "$logTag-serve-$threadName").start()
        return readSide
    }

    companion object {
        private val pngCache = ConcurrentHashMap<String, ByteArray>()
        private val generationMap = ConcurrentHashMap<Int, Int>()

        @JvmStatic
        fun nextGeneration(widgetId: Int) {
            generationMap.merge(widgetId, 1) { old, _ -> old + 1 }
        }

        @JvmStatic
        fun invalidateCache(widgetId: Int) {
            pngCache.keys.removeAll { it.startsWith("${widgetId}_") }
        }

        @JvmStatic
        fun getCachedBitmap(context: Context, widgetId: Int, prefix: String): Bitmap? {
            val generation = generationMap[widgetId] ?: 0
            val nightMode =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val key = cacheKey(widgetId, nightMode, generation, prefix)
            val bytes = pngCache[key] ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        @JvmStatic
        fun uri(packageName: String, widgetId: Int, authoritySuffix: String): Uri {
            val generation = generationMap[widgetId] ?: 0
            return Uri.Builder()
                .scheme("content")
                .authority(packageName + authoritySuffix)
                .path("render/$widgetId/content")
                .appendQueryParameter("g", generation.toString())
                .build()
        }

        internal fun currentNightMode(context: Context): Int =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        internal fun themedContext(context: Context, nightMode: Int): Context {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            return context.createConfigurationContext(config)
        }

        internal fun compressPng(bitmap: Bitmap): ByteArray {
            return ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                stream.toByteArray()
            }
        }

        internal fun cacheKey(
            widgetId: Int,
            nightMode: Int,
            generation: Int,
            prefix: String
        ): String = "${widgetId}_${prefix}_${nightMode}_g$generation"

        internal fun currentGeneration(widgetId: Int): Int = generationMap[widgetId] ?: 0

        internal fun storePng(key: String, bytes: ByteArray) {
            pngCache[key] = bytes
        }

        internal fun containsPng(key: String): Boolean = pngCache.containsKey(key)
    }
}
