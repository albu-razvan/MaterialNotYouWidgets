package com.razvanalbu.material.not.you.widgets.weather

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.transition.Transition
import android.transition.TransitionValues
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

class BlurTransition(private val isEnter: Boolean) : Transition() {

    private companion object {
        const val MAX_BLUR = 30
    }

    override fun getTransitionProperties(): Array<String> = emptyArray()

    override fun captureStartValues(transitionValues: TransitionValues) {}

    override fun captureEndValues(transitionValues: TransitionValues) {}

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        val window = (sceneRoot.context as? Activity)?.window ?: return null

        val startBlur = if (isEnter) 0 else window.attributes.blurBehindRadius
        val endBlur = if (isEnter) MAX_BLUR else 0
        if (startBlur == endBlur) return null

        val animator = ValueAnimator.ofInt(startBlur, endBlur)
        animator.duration = 400
        animator.interpolator = FastOutSlowInInterpolator()
        animator.addUpdateListener { anim ->
            val params = window.attributes
            params.blurBehindRadius = anim.animatedValue as Int
            window.attributes = params
        }
        return animator
    }
}