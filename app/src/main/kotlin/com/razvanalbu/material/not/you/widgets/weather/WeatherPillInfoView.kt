package com.razvanalbu.material.not.you.widgets.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.core.graphics.createBitmap
import java.lang.Math.random
import kotlin.math.abs

class WeatherPillInfoView(context: Context) : View(context) {

    private var textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var textColor: Int = android.graphics.Color.WHITE
    private var typeface: Typeface? = null
    private var temperature: Int? = null
    private var icon: Drawable? = null

    fun setTemperature(text: Int?) {
        temperature = text
        invalidate()
    }

    fun setIcon(drawable: Drawable?) {
        icon = drawable
        invalidate()
    }

    fun setTextColor(color: Int) {
        textColor = color
        invalidate()
    }

    fun setTypeface(tf: Typeface?) {
        typeface = tf
        invalidate()
    }

    fun renderToBitmap(width: Int, height: Int): Bitmap {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )

        layout(0, 0, width, height)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        canvas.drawColor(
            android.graphics.Color.TRANSPARENT,
            android.graphics.PorterDuff.Mode.CLEAR
        )
        draw(canvas)

        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        val temp = temperature ?: return
        val tf = typeface ?: return

        val w = width.toFloat()
        val h = height.toFloat()

        val isThreeDigits = abs(temp) >= 100

        textPaint.apply {
            color = textColor
            typeface = tf
            textAlign = Paint.Align.CENTER
            textSize = minOf(width, height) * if (isThreeDigits) 0.3f else 0.33f
        }

        val fm = textPaint.fontMetrics

        val textX = if (isThreeDigits) w * 0.54f else w * 0.57f
        val textY = -fm.ascent * if (isThreeDigits) 1.7f else 1.5f

        canvas.drawText("$temp°", textX, textY, textPaint)

        icon?.let {
            val iconSize = (minOf(width, height) * 0.32f).toInt()
            val iconLeft = (w * 0.27f).toInt()
            val iconTop = (h * 0.55f).toInt()

            it.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            it.draw(canvas)
        }
    }
}
