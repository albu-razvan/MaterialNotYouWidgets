package com.razvanalbu.material.not.you.widgets.weather

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.edit
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

internal object WeatherWidgetStateManager {

    enum class ContentState {
        REQUIRES_CONFIG,
        UPDATING,
        NO_INTERNET,
        ERROR,
        SUCCESS,
    }

    sealed class TickResult {
        data object None : TickResult()
        data object ToRotate : TickResult()
        data class ToMorphOut(val startRotation: Float, val targetRotation: Float) : TickResult()
        data object Completed : TickResult()
    }

    private const val MORPH_DURATION_NS = 500_000_000L
    private const val MIN_ROTATE_NS = 300_000_000L
    private const val MAX_ROTATE_WAIT_NS = 15_000_000_000L

    private data class PendingUpdate(
        val contentState: ContentState,
        val weatherState: WeatherState?,
        val bitmap: Bitmap? = null,
    )

    private val currentContentState = mutableMapOf<Int, ContentState>()
    private val pendingUpdates = mutableMapOf<Int, PendingUpdate>()
    private val cachedWeatherState = mutableMapOf<Int, WeatherState>()
    private val lock = Any()

    private val animPhase = ConcurrentHashMap<Int, PumpPhase>()
    private val animPhaseStartTime = ConcurrentHashMap<Int, Long>()
    private val animContentReady = ConcurrentHashMap.newKeySet<Int>()

    fun getAnimPhase(appWidgetId: Int): PumpPhase = animPhase[appWidgetId] ?: PumpPhase.IDLE

    fun getAnimPhaseStartTime(appWidgetId: Int): Long = animPhaseStartTime[appWidgetId] ?: 0L

    fun startAnimation(appWidgetId: Int) {
        animPhase[appWidgetId] = PumpPhase.MORPH_IN
        animPhaseStartTime[appWidgetId] = 0L
        animContentReady.remove(appWidgetId)
    }

    fun resetAnimation(appWidgetId: Int) {
        animPhase.remove(appWidgetId)
        animPhaseStartTime.remove(appWidgetId)
        animContentReady.remove(appWidgetId)
    }

    fun tickAnimation(
        appWidgetId: Int,
        frameTimeNanos: Long,
        currentRotation: Float,
        spinDegrees: Float
    ): TickResult {
        val phase = animPhase[appWidgetId] ?: return TickResult.None
        val startTime = animPhaseStartTime[appWidgetId] ?: return TickResult.None

        if (startTime == 0L) {
            animPhaseStartTime[appWidgetId] = frameTimeNanos
            return TickResult.None
        }

        return when (phase) {
            PumpPhase.MORPH_IN -> {
                val time =
                    ((frameTimeNanos - startTime).toFloat() / MORPH_DURATION_NS).coerceAtMost(1f)
                if (time >= 1f) {
                    animPhase[appWidgetId] = PumpPhase.ROTATE
                    animPhaseStartTime[appWidgetId] = frameTimeNanos
                    TickResult.ToRotate
                } else {
                    TickResult.None
                }
            }

            PumpPhase.ROTATE -> {
                val elapsed = frameTimeNanos - startTime
                if (appWidgetId in animContentReady) {
                    if (elapsed >= MIN_ROTATE_NS) {
                        return transitionToMorphOut(
                            appWidgetId, frameTimeNanos,
                            currentRotation, spinDegrees
                        )
                    }
                } else if (elapsed >= MAX_ROTATE_WAIT_NS) {
                    return transitionToMorphOut(
                        appWidgetId, frameTimeNanos,
                        currentRotation, spinDegrees
                    )
                }

                TickResult.None
            }

            PumpPhase.MORPH_OUT -> {
                val elapsed = frameTimeNanos - startTime
                val t = (elapsed.toFloat() / MORPH_DURATION_NS).coerceAtMost(1f)
                if (t >= 1f) {
                    animPhase.remove(appWidgetId)
                    animPhaseStartTime.remove(appWidgetId)
                    animContentReady.remove(appWidgetId)
                    TickResult.Completed
                } else {
                    TickResult.None
                }
            }

            else -> TickResult.None
        }
    }

    private fun transitionToMorphOut(
        appWidgetId: Int,
        frameTimeNanos: Long,
        currentRotation: Float,
        spinDegrees: Float
    ): TickResult.ToMorphOut {
        val approxTarget = currentRotation + spinDegrees
        val m = ((approxTarget + 45f) / 360f).roundToInt()
        val startRotation = currentRotation
        val targetRotation = -45f + 360f * m

        animPhase[appWidgetId] = PumpPhase.MORPH_OUT
        animPhaseStartTime[appWidgetId] = frameTimeNanos

        return TickResult.ToMorphOut(startRotation, targetRotation)
    }

    fun isAnimating(): Boolean = animPhase.values.any { it != PumpPhase.IDLE }

    fun weatherState(appWidgetId: Int): WeatherState? = synchronized(lock) {
        cachedWeatherState[appWidgetId]
    }

    fun isUpdating(appWidgetId: Int): Boolean = synchronized(lock) {
        currentContentState[appWidgetId] == ContentState.UPDATING
    }

    fun getContentState(appWidgetId: Int): ContentState? = synchronized(lock) {
        currentContentState[appWidgetId]
    }

    fun reapplyState(context: Context, appWidgetId: Int) {
        synchronized(lock) {
            val state = currentContentState[appWidgetId] ?: ContentState.SUCCESS
            val views = WeatherWidgetViews.createViews(context, appWidgetId, state)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    fun applyState(
        context: Context,
        appWidgetId: Int,
        contentState: ContentState,
        weatherState: WeatherState? = null,
        bitmap: Bitmap? = null,
    ) {
        synchronized(lock) {
            if (isAnimating()) {
                pendingUpdates[appWidgetId] = PendingUpdate(contentState, weatherState, bitmap)
                Log.d(TAG, "[$appWidgetId] queued state=$contentState (animating)")
                return
            }

            applyNow(context, appWidgetId, contentState, weatherState, bitmap)
        }
    }

    fun flushPendingUpdate(context: Context, appWidgetId: Int) {
        synchronized(lock) {
            val pending = pendingUpdates.remove(appWidgetId) ?: return
            Log.d(TAG, "[$appWidgetId] flushing pending state=${pending.contentState}")
            applyNow(context, appWidgetId, pending.contentState, pending.weatherState, pending.bitmap)
        }
    }

    fun flushContentDuringMorphOut(context: Context, appWidgetId: Int): ContentState? {
        synchronized(lock) {
            val pending = pendingUpdates.remove(appWidgetId) ?: return null
            Log.d(TAG, "[$appWidgetId] flushing content before morph_out state=${pending.contentState}")

            pending.bitmap?.recycle()

            if (pending.weatherState != null) {
                cachedWeatherState[appWidgetId] = pending.weatherState
                persistWeatherState(context, appWidgetId, pending.weatherState)
            }

            currentContentState[appWidgetId] = pending.contentState
            return pending.contentState
        }
    }

    fun requestMorphOut(appWidgetId: Int) {
        animContentReady.add(appWidgetId)
    }

    private fun applyNow(
        context: Context,
        appWidgetId: Int,
        contentState: ContentState,
        weatherState: WeatherState?,
        bitmap: Bitmap? = null,
    ) {
        bitmap?.recycle()

        val views = WeatherWidgetViews.createViews(context, appWidgetId, contentState)

        if (weatherState != null) {
            cachedWeatherState[appWidgetId] = weatherState
            persistWeatherState(context, appWidgetId, weatherState)
        }

        currentContentState[appWidgetId] = contentState
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    fun getOrRestoreWeatherState(context: Context, appWidgetId: Int): WeatherState? = synchronized(lock) {
        cachedWeatherState[appWidgetId] ?: loadPersistedWeatherState(context, appWidgetId)
    }

    fun cacheAndPersistWeatherState(context: Context, appWidgetId: Int, state: WeatherState) {
        synchronized(lock) {
            if (state is WeatherState.Success) {
                cachedWeatherState[appWidgetId] = state
                persistWeatherState(context, appWidgetId, state)
            }
        }
    }

    private fun persistWeatherState(context: Context, appWidgetId: Int, state: WeatherState) {
        if (state is WeatherState.Success) {
            context.getSharedPreferences(PREFS_WEATHER, Context.MODE_PRIVATE).edit {
                putInt(PREF_TEMP + appWidgetId, state.temp)
                putInt(PREF_ICON + appWidgetId, state.iconRes)
            }
        }
    }

    private fun loadPersistedWeatherState(context: Context, appWidgetId: Int): WeatherState? {
        val prefs = context.getSharedPreferences(PREFS_WEATHER, Context.MODE_PRIVATE)
        val temp = prefs.getInt(PREF_TEMP + appWidgetId, Int.MIN_VALUE)
        val iconRes = prefs.getInt(PREF_ICON + appWidgetId, 0)
        if (temp != Int.MIN_VALUE && iconRes != 0) {
            val state = WeatherState.Success(temp, iconRes)
            cachedWeatherState[appWidgetId] = state
            return state
        }
        return null
    }

    private const val TAG = "WidgetStateMgr"
    private const val PREFS_WEATHER = "weather_state_cache"
    private const val PREF_TEMP = "temp_"
    private const val PREF_ICON = "icon_"
}
