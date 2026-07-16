package com.razvanalbu.material.not.you.widgets.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.razvanalbu.material.not.you.widgets.R

class StatefulFontButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MaterialButton(context, attrs) {

    private val active = ResourcesCompat.getFont(context, R.font.google_sans_flex_bold_rounded)
    private val inactive = ResourcesCompat.getFont(context, R.font.google_sans_flex_regular)

    override fun drawableStateChanged() {
        super.drawableStateChanged()

        typeface = if (isChecked) {
            active
        } else {
            inactive
        }
    }
}