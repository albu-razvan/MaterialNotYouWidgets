package com.razvanalbu.material.not.you.widgets.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import android.view.Choreographer
import android.view.animation.PathInterpolator
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils.getSquareSizePx
import kotlin.math.roundToInt

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

    protected var shapeColor = 0
    protected var squarePx = 400
    protected var widgetId = -1
    protected var lastBitmap: Bitmap? = null
    protected val morphEngine = MorphingEngine()

    private var phase = PumpPhase.MORPH_IN
    private var phaseStartTimeNs = 0L
    private var currentRotation = 0f
    private var morphOutTargetRotation = -45f
    private var morphOutStartRotation = 0f
    private var pendingDeactivate = false

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        if (phaseStartTimeNs == 0L) {
            phaseStartTimeNs = frameTimeNanos
        }

        processFrame(frameTimeNanos)
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MORPH_IN -> {
                if (currentPhase != PumpPhase.IDLE) {
                    return START_NOT_STICKY
                }

                shapeColor = intent.getIntExtra(EXTRA_SHAPE_COLOR, 0)
                widgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
                squarePx = getSquareSizePx(this, widgetId)

                reset()

                val notification = createNotification()
                startForeground(notificationId, notification)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }
            ACTION_MORPH_OUT -> {
                triggerMorphOut()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanup()

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
        phase = PumpPhase.MORPH_IN
        currentPhase = PumpPhase.MORPH_IN
        phaseStartTimeNs = 0L
        pendingDeactivate = false
    }

    private fun cleanup() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastBitmap?.recycle()
        lastBitmap = null
    }

    private fun processFrame(frameTimeNanos: Long) {
        when (phase) {
            PumpPhase.IDLE -> return

            PumpPhase.MORPH_IN -> {
                val time = ((frameTimeNanos - phaseStartTimeNs).toFloat() / morphDurationNs)
                    .coerceAtMost(1f)

                val rot = -45f + spinDegrees * spinInInterpolator.getInterpolation(time)
                currentRotation = rot

                pushFrame(
                    morphEngine.renderRadiiToBitmap(
                        squarePx, squarePx, startRadii, endRadii,
                        morphInterpolator.getInterpolation(time), shapeColor, rot
                    )
                )

                if (time >= 1f) {
                    if (pendingDeactivate) {
                        pendingDeactivate = false
                        val approxTarget = currentRotation + spinDegrees
                        val m = ((approxTarget + 45f) / 360f).roundToInt()
                        morphOutStartRotation = currentRotation
                        morphOutTargetRotation = -45f + 360f * m
                        phase = PumpPhase.MORPH_OUT
                    } else {
                        phase = PumpPhase.ROTATE
                    }
                    phaseStartTimeNs = frameTimeNanos
                }
            }

            PumpPhase.ROTATE -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                currentRotation =
                    -45f + spinDegrees + elapsed.toFloat() / 1_000_000_000f * rotationSpeed
                pushFrame(
                    morphEngine.renderRadiiToBitmap(
                        squarePx, squarePx, endRadii, endRadii, 0f, shapeColor, currentRotation
                    )
                )
            }

            PumpPhase.MORPH_OUT -> {
                val elapsed = frameTimeNanos - phaseStartTimeNs
                val t = (elapsed.toFloat() / morphDurationNs).coerceAtMost(1f)
                val morphT = morphInterpolator.getInterpolation(t)
                val spinT = spinOutInterpolator.getInterpolation(t)
                val rot = morphOutStartRotation * (1f - spinT) + morphOutTargetRotation * spinT

                pushFrame(
                    morphEngine.renderRadiiToBitmap(
                        squarePx, squarePx, endRadii, startRadii, morphT, shapeColor, rot
                    )
                )

                if (t >= 1f) {
                    currentPhase = PumpPhase.IDLE
                    cleanup()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()

                    return
                }
            }
        }

        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun triggerMorphOut() {
        when (phase) {
            PumpPhase.MORPH_IN -> {
                pendingDeactivate = true
            }
            PumpPhase.ROTATE -> {
                val approxTarget = currentRotation + spinDegrees
                val m = ((approxTarget + 45f) / 360f).roundToInt()
                morphOutStartRotation = currentRotation
                morphOutTargetRotation = -45f + 360f * m
                phase = PumpPhase.MORPH_OUT
                phaseStartTimeNs = System.nanoTime()
            }
            else -> { }
        }
    }

    protected fun pushFrame(frame: Bitmap) {
        val views = RemoteViews(packageName, layoutResId)
        views.setInt(contentContainerId, "setMinimumWidth", squarePx)
        views.setInt(contentContainerId, "setMinimumHeight", squarePx)
        views.setImageViewBitmap(morphImageViewId, frame)

        onPushFrameHook(views)

        AppWidgetManager.getInstance(this)
            .updateAppWidget(widgetId, views)

        lastBitmap?.recycle()
        lastBitmap = frame
    }

    protected open fun onPushFrameHook(views: RemoteViews) {}

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
        private const val TAG = "BasePumpService"

        const val EXTRA_APPWIDGET_ID = "app_widget_id"
        const val EXTRA_SHAPE_COLOR = "shape_color"
        const val ACTION_MORPH_IN = "morph_in"
        const val ACTION_MORPH_OUT = "morph_out"

        @Volatile
        var currentPhase = PumpPhase.IDLE
    }
}
