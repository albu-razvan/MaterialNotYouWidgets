package com.razvanalbu.material.not.you.widgets.weather

import android.animation.TimeInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.razvanalbu.material.not.you.widgets.core.PumpPhase

data class FrameAnimSpec(
    val containerScaleFrom: Float,
    val containerScaleTo: Float,
    val infoScaleFrom: Float,
    val infoScaleTo: Float,
    val alphaFrom: Float,
    val alphaTo: Float,
    val interpolator: TimeInterpolator,
)

fun getSpecForPhase(phase: PumpPhase): FrameAnimSpec {
    return when (phase) {
        PumpPhase.MORPH_IN -> FrameAnimSpec(
            containerScaleFrom = 1f,
            containerScaleTo = 0.6f,
            infoScaleFrom = 1f,
            infoScaleTo = 0.7f,
            alphaFrom = 1f,
            alphaTo = -1f,
            interpolator = PathInterpolator(0.9f, 0f, 0.3f, 1f)
        )

        PumpPhase.ROTATE -> FrameAnimSpec(
            containerScaleFrom = 0.6f,
            containerScaleTo = 0.6f,
            infoScaleFrom = 0.7f,
            infoScaleTo = 0.7f,
            alphaFrom = 0f,
            alphaTo = 0f,
            interpolator = LinearInterpolator()
        )

        PumpPhase.MORPH_OUT -> FrameAnimSpec(
            containerScaleFrom = 0.6f,
            containerScaleTo = 1f,
            infoScaleFrom = 0.7f,
            infoScaleTo = 1f,
            alphaFrom = -1f,
            alphaTo = 1f,
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        )

        PumpPhase.IDLE -> FrameAnimSpec(
            containerScaleFrom = 1f,
            containerScaleTo = 1f,
            infoScaleFrom = 1f,
            infoScaleTo = 1f,
            alphaFrom = 1f,
            alphaTo = 1f,
            interpolator = LinearInterpolator()
        )
    }
}
