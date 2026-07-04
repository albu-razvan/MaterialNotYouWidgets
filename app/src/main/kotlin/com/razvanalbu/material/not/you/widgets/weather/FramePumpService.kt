package com.razvanalbu.material.not.you.widgets.weather

import android.widget.RemoteViews
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BasePumpService
import com.razvanalbu.material.not.you.widgets.core.PumpPhase
import com.razvanalbu.material.not.you.widgets.core.ShapeType

class FramePumpService : BasePumpService() {

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

    override fun onFrame(phase: PumpPhase, fraction: Float) {
        onAnimationFrame?.invoke(phase, fraction)
    }

    override fun onPushFrameHook(views: RemoteViews) {
        onPushFrameView?.invoke(views)
    }

    companion object {
        private const val CHANNEL_ID = "widget_morph_animation"

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

        @JvmStatic
        val EXTRA_SHAPE_COLOR: String
            get() = BasePumpService.EXTRA_SHAPE_COLOR
    }
}
