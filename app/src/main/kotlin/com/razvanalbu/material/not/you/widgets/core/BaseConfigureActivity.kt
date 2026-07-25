package com.razvanalbu.material.not.you.widgets.core

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.razvanalbu.material.not.you.widgets.R
import kotlin.math.max

abstract class BaseConfigureActivity : AppCompatActivity() {

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    protected var isImeAnimating = false
    protected var hasChanged = false

    protected abstract val layoutResId: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Maybe deal with landscape layouts in the future?
        // noinspection SourceLockedOrientationActivity
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContentView(layoutResId)

        setResult(RESULT_CANCELED, Intent())

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupWindowInsets()
    }

    protected open fun setupWindowInsets() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val layoutRoot = findViewById<ViewGroup>(R.id.root_layout)

        root.clipToPadding = false
        root.clipChildren = false
        layoutRoot.clipToPadding = false
        layoutRoot.clipChildren = false

        ViewCompat.setOnApplyWindowInsetsListener(layoutRoot) { view, insets ->
            if (!isImeAnimating) {
                updatePaddingForInsets(view, insets)
            }

            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            layoutRoot,
            object : WindowInsetsAnimationCompat.Callback(
                DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        isImeAnimating = true
                    }

                    super.onPrepare(animation)
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    updatePaddingForInsets(layoutRoot, insets)

                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        isImeAnimating = false
                        ViewCompat.requestApplyInsets(layoutRoot)
                    }

                    super.onEnd(animation)
                }
            }
        )

        ViewCompat.requestApplyInsets(layoutRoot)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = !isNight
            controller.isAppearanceLightNavigationBars = !isNight
        }
    }

    protected fun updatePaddingForInsets(view: View, insets: WindowInsetsCompat) {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

        view.updatePadding(
            left = systemBars.left,
            top = systemBars.top,
            right = systemBars.right,
            bottom = max(systemBars.bottom, ime.bottom)
        )
    }
}
