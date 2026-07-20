package com.razvanalbu.material.not.you.widgets.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.view.Choreographer
import android.view.ContextThemeWrapper
import android.view.animation.PathInterpolator
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils.getSquareSizePx

enum class PumpPhase {
    IDLE,
    MORPH_IN,
    ROTATE,
    MORPH_OUT
}

abstract class BasePumpService : Service() {
    protected abstract val notificationChannelName: String
    protected abstract val notificationDescription: String
    protected abstract val notificationChannelId: String
    protected abstract val contentContainerId: Int
    protected abstract val morphImageViewId: Int
    protected abstract val layoutResId: Int

    protected open val spinOutInterpolator = PathInterpolator(0.75f, 0.1f, 0.25f, 1.0f)
    protected open val spinInInterpolator = PathInterpolator(0.75f, 0.0f, 0.25f, 0.9f)
    protected open val morphInterpolator = PathInterpolator(0.75f, 0.0f, 0.25f, 1.0f)
    protected open val notificationSmallIcon: Int = android.R.drawable.ic_menu_compass
    protected open val morphDurationNs: Long = 500_000_000L
    protected open val rotationSpeed: Float = 120f
    protected open val notificationId: Int = 1001
    protected open val spinDegrees: Float = 120f

    protected abstract val startRadii: FloatArray
    protected abstract val endRadii: FloatArray

    @Volatile
    protected var squarePx = 400
    protected var widgetId = -1
    protected var lastBitmap: Bitmap? = null
    protected val morphEngine = MorphingEngine()
    private lateinit var widgetManager: AppWidgetManager
    private var shapeColor = 0
    private var currentRotation = 0f
    private var morphOutTargetRotation = -45f
    private var morphOutStartRotation = 0f

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        processFrame(frameTimeNanos)
    }

    override fun onCreate() {
        super.onCreate()
        widgetManager = AppWidgetManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setActiveInstance(this)
        when (intent?.action) {
            ACTION_MORPH_IN -> {
                widgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
                if (WeatherWidgetStateManager.getAnimPhase(widgetId) != PumpPhase.IDLE) {
                    return START_NOT_STICKY
                }

                squarePx = getSquareSizePx(this, widgetId)
                reset()

                val notification = createNotification()
                startForeground(notificationId, notification)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanup()
        setActiveInstance(null)
        super.onDestroy()
    }

    protected open fun createNotification(): Notification {
        return Notification.Builder(this, notificationChannelId)
            .setContentTitle(getNotificationTitle())
            .setContentText(getNotificationText())
            .setSmallIcon(notificationSmallIcon)
            .setOngoing(true)
            .build()
    }

    protected open fun getNotificationTitle(): String = "Widget"
    protected open fun getNotificationText(): String = "Loading..."

    private fun reset() {
        WeatherWidgetStateManager.startAnimation(widgetId)
        shapeColor = resolveShapeColor()
    }

    private fun cleanup() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastBitmap?.recycle()
        lastBitmap = null

        if (widgetId != -1 && WeatherWidgetStateManager.getAnimPhase(widgetId) != PumpPhase.IDLE) {
            WeatherWidgetStateManager.reapplyState(this, widgetId)
            WeatherWidgetStateManager.resetAnimation(widgetId)
        }
    }

    private fun processFrame(frameTimeNanos: Long) {
        val event = WeatherWidgetStateManager.tickAnimation(
            widgetId, frameTimeNanos, currentRotation, spinDegrees
        )
        when (event) {
            is WeatherWidgetStateManager.TickResult.ToMorphOut -> {
                onBeforeMorphOut()
                morphOutStartRotation = event.startRotation
                morphOutTargetRotation = event.targetRotation
            }
            is WeatherWidgetStateManager.TickResult.Completed -> {
                onAnimationComplete()
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            else -> {}
        }

        val size = squarePx
        val phase = WeatherWidgetStateManager.getAnimPhase(widgetId)
        val phaseStartTimeNs = WeatherWidgetStateManager.getAnimPhaseStartTime(widgetId)

        when (phase) {
            PumpPhase.IDLE -> return

            PumpPhase.MORPH_IN -> {
                val time = ((frameTimeNanos - phaseStartTimeNs).toFloat() / morphDurationNs)
                    .coerceAtMost(1f)
                val morphT = morphInterpolator.getInterpolation(time)
                val rot = -45f + spinDegrees * spinInInterpolator.getInterpolation(time)
                currentRotation = rot

                onFrame(PumpPhase.MORPH_IN, time)

                pushFrame(
                    morphEngine.renderRadiiToBitmap(
                        size, size, startRadii, endRadii,
                        morphT, shapeColor, rot
                    )
                )
            }

            PumpPhase.ROTATE -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                currentRotation =
                    -45f + spinDegrees + elapsed.toFloat() / 1_000_000_000f * rotationSpeed

                onFrame(PumpPhase.ROTATE, 0f)

                pushFrame(
                    morphEngine.renderRadiiToBitmap(
                        size, size, endRadii, endRadii,
                        0f, shapeColor, currentRotation
                    )
                )
            }

            PumpPhase.MORPH_OUT -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                val t = (elapsed.toFloat() / morphDurationNs).coerceAtMost(1f)
                val morphT = morphInterpolator.getInterpolation(t)
                val spinT = spinOutInterpolator.getInterpolation(t)
                val rot = morphOutStartRotation * (1f - spinT) + morphOutTargetRotation * spinT

                onFrame(PumpPhase.MORPH_OUT, t)

                pushFrame(
                    morphEngine.renderRadiiToBitmap(
                        size, size, endRadii, startRadii,
                        morphT, shapeColor, rot
                    )
                )
            }
        }

        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    protected fun pushFrame(frame: Bitmap) {
        val views = RemoteViews(packageName, layoutResId)
        views.setImageViewBitmap(morphImageViewId, frame)

        onPushFrameHook(views)

        widgetManager.updateAppWidget(widgetId, views)

        lastBitmap?.recycle()
        lastBitmap = frame
    }

    protected open fun onPushFrameHook(views: RemoteViews) {}

    protected open fun onFrame(phase: PumpPhase, fraction: Float) {}

    protected open fun onBeforeMorphOut() {}

    protected open fun onAnimationComplete() {}

    protected fun resolveShapeColor(): Int {
        val wrapper = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3Expressive_DynamicColors_DayNight)
        val ta = wrapper.obtainStyledAttributes(intArrayOf(
            com.google.android.material.R.attr.colorSurfaceContainer,
        ))
        val color = ta.getColor(0, 0xFF6750A4.toInt())
        ta.recycle()
        return color
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            notificationChannelId,
            notificationChannelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = notificationDescription
            setShowBadge(false)
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_APPWIDGET_ID = "app_widget_id"
        const val ACTION_MORPH_IN = "morph_in"
        const val ACTION_MORPH_OUT = "morph_out"

        @Volatile
        private var activeInstance: BasePumpService? = null

        val currentPhase: PumpPhase
            get() {
                val instance = activeInstance ?: return PumpPhase.IDLE
                return WeatherWidgetStateManager.getAnimPhase(instance.widgetId)
            }

        fun setActiveInstance(service: BasePumpService?) {
            activeInstance = service
        }

        @JvmStatic
        fun getActiveInstance(): BasePumpService? = activeInstance

        fun updateWidgetSize(appWidgetId: Int, squarePx: Int) {
            val instance = activeInstance ?: return
            if (instance.widgetId != appWidgetId) return
            instance.squarePx = squarePx
        }

        @JvmStatic
        fun getMorphShapeRes(widgetId: Int): Int {
            val instance = activeInstance ?: return R.drawable.pill_shape
            if (instance.widgetId != widgetId) return R.drawable.pill_shape
            val phase = WeatherWidgetStateManager.getAnimPhase(widgetId)
            return if (phase == null || phase == PumpPhase.IDLE) {
                R.drawable.pill_shape
            } else {
                R.drawable.pill_shape
            }
        }
    }
}
