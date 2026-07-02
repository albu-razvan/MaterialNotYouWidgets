package com.razvanalbu.material.not.you.widgets

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb

class FramePumpService : Service() {

    private enum class Phase { MORPH_IN, ROTATE, MORPH_OUT }

    private val handler = Handler(Looper.getMainLooper())
    private val morphEngine = MorphingEngine()
    private val pillRadii = morphEngine.computeRadii(ShapeType.PILL)
    private val hexRadii = morphEngine.computeRadii(ShapeType.HEXAGON)

    private var phase = Phase.MORPH_IN
    private var phaseStartTimeNs = 0L
    private var widgetId = -1
    private var shapeColor = 0
    private var lastBitmap: Bitmap? = null
    private var fetchStarted = false
    private var fetchCompleted = false
    private var fetchResult: WeatherState? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (phaseStartTimeNs == 0L) {
                phaseStartTimeNs = frameTimeNanos
            }
            processFrame(frameTimeNanos)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        widgetId = intent?.getIntExtra(EXTRA_APPWIDGET_ID, -1) ?: -1
        shapeColor = intent?.getIntExtra(EXTRA_SHAPE_COLOR, 0) ?: 0
        phase = Phase.MORPH_IN
        phaseStartTimeNs = 0L
        fetchStarted = false
        fetchCompleted = false
        fetchResult = null

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Weather Widget")
            .setContentText("Loading weather...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        Choreographer.getInstance().postFrameCallback(frameCallback)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastBitmap?.recycle()
        lastBitmap = null
        super.onDestroy()
    }

    private fun processFrame(frameTimeNanos: Long) {
        when (phase) {
            Phase.MORPH_IN -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                val t = (elapsed.toFloat() / MORPH_DURATION_NS).coerceAtMost(1f)
                val frame = morphEngine.renderRadiiToBitmap(
                    400, 200, pillRadii, hexRadii, t, shapeColor, 0f
                )
                pushFrame(frame)
                if (t >= 1f) {
                    phase = Phase.ROTATE
                    phaseStartTimeNs = frameTimeNanos
                    if (!fetchStarted) {
                        fetchStarted = true
                        startFetch()
                    }
                }
            }

            Phase.ROTATE -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                val rotationDeg = elapsed.toFloat() / 1_000_000_000f * ROTATION_SPEED
                val frame = morphEngine.renderRadiiToBitmap(
                    400, 200, hexRadii, hexRadii, 0f, shapeColor, rotationDeg
                )
                pushFrame(frame)

                if (!fetchStarted) {
                    fetchStarted = true
                    startFetch()
                }

                if (fetchCompleted || elapsed >= FETCH_TIMEOUT_NS) {
                    if (!fetchCompleted) {
                        fetchResult = null
                    }
                    phase = Phase.MORPH_OUT
                    phaseStartTimeNs = frameTimeNanos
                }
            }

            Phase.MORPH_OUT -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                val t = (elapsed.toFloat() / MORPH_DURATION_NS).coerceAtMost(1f)
                val frame = morphEngine.renderRadiiToBitmap(
                    400, 200, hexRadii, pillRadii, t, shapeColor, 0f
                )
                pushFrame(frame)

                if (t >= 1f) {
                    pushWeatherText()
                    lastBitmap?.recycle()
                    lastBitmap = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }
            }
        }

        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun pushFrame(frame: Bitmap) {
        val views = RemoteViews(packageName, R.layout.weather_pill_layout)
        views.setImageViewBitmap(R.id.morph_image, frame)
        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, views)

        lastBitmap?.recycle()
        lastBitmap = frame
    }

    private fun pushWeatherText() {
        val isDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorScheme = if (isDark) dynamicDarkColorScheme(this)
        else dynamicLightColorScheme(this)

        val views = RemoteViews(packageName, R.layout.weather_pill_layout)

        views.setTextColor(R.id.location_text, colorScheme.onSurface.toArgb())
        views.setTextColor(R.id.temp_text, colorScheme.onSurface.toArgb())
        views.setTextColor(R.id.condition_text, colorScheme.onSurfaceVariant.toArgb())
        views.setTextColor(R.id.high_low_text, colorScheme.primary.toArgb())

        when (val data = fetchResult) {
            is WeatherState.Success -> {
                views.setTextViewText(R.id.location_text, "Stockholm")
                views.setTextViewText(R.id.icon_text, data.icon)
                views.setTextViewText(R.id.temp_text, data.temp)
                views.setTextViewText(R.id.condition_text, data.condition)
                views.setTextViewText(R.id.high_low_text, "${data.high}  ${data.low}")
            }
            else -> {
                views.setTextViewText(R.id.location_text, "Stockholm")
                views.setTextViewText(R.id.icon_text, "\u26A0\uFE0F")
                views.setTextViewText(R.id.temp_text, "--\u00B0")
                views.setTextViewText(R.id.condition_text, "Unable to load")
                views.setTextViewText(R.id.high_low_text, "")
            }
        }

        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, views)
    }

    private fun startFetch() {
        Thread {
            val data = WeatherApi.fetchWeatherData()
            handler.post {
                fetchCompleted = true
                fetchResult = data
            }
        }.start()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Widget Animation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows morph animation when widget is tapped"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "widget_morph_animation"
        private const val NOTIFICATION_ID = 1001
        private const val MORPH_DURATION_NS = 500_000_000L
        private const val ROTATION_SPEED = 120f
        private const val FETCH_TIMEOUT_NS = 10_000_000_000L

        const val EXTRA_APPWIDGET_ID = "app_widget_id"
        const val EXTRA_SHAPE_COLOR = "shape_color"
    }
}
