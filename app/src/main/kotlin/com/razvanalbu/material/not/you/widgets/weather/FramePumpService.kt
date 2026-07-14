package com.razvanalbu.material.not.you.widgets.weather

import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.ShapeType
import com.razvanalbu.material.not.you.widgets.weather.WeatherWidgetStateManager.ContentState

class FramePumpService : BasePumpService() {

    private var lastContentState: ContentState? = null

    override val contentContainerId: Int = R.id.content_container
    override val layoutResId: Int = R.layout.weather_pill_layout
    override val morphImageViewId: Int = R.id.morph_image

    override val notificationDescription: String = "Shows morph animation when widget is tapped"
    override val notificationChannelName: String = "Widget Animation"
    override val notificationChannelId: String = CHANNEL_ID

    override val startRadii: FloatArray = morphEngine.computeRadii(ShapeType.PILL)
    override val endRadii: FloatArray = morphEngine.computeRadii(ShapeType.COOKIE)

    override fun getNotificationTitle(): String = "Weather Widget"
    override fun getNotificationText(): String = "Loading weather..."

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onFrame(phase: PumpPhase, fraction: Float) {
        onAnimationFrame?.invoke(phase, fraction)
    }

    override fun onPushFrameHook(views: RemoteViews) {
        val state = WeatherWidgetStateManager.getContentState(widgetId) ?: ContentState.SUCCESS
        if (state != lastContentState) {
            lastContentState = state
            val phase = WeatherWidgetStateManager.getAnimPhase(widgetId)
            if (phase == PumpPhase.MORPH_OUT) {
                WeatherWidgetViews.applyContentStateBitmap(views, this, widgetId, state)
            } else {
                WeatherWidgetViews.applyContentState(views, this, widgetId, state)
            }
        }
        onPushFrameView?.invoke(views)
    }

    override fun onBeforeMorphOut() {
        WeatherWidgetStateManager.flushContentDuringMorphOut(this, widgetId)
    }

    override fun onAnimationComplete() {
        WeatherWidgetStateManager.reapplyState(this, widgetId)
    }

    companion object {
        private const val CHANNEL_ID = "widget_morph_animation"
        private const val TAG = "FramePumpService"

        @Volatile
        var onAnimationFrame: ((PumpPhase, Float) -> Unit)? = null

        @Volatile
        var onPushFrameView: ((RemoteViews) -> Unit)? = null

        @JvmStatic
        val currentPhase: PumpPhase
            get() = BasePumpService.currentPhase

        @JvmStatic
        val EXTRA_APPWIDGET_ID: String
            get() = BasePumpService.EXTRA_APPWIDGET_ID
    }
}
