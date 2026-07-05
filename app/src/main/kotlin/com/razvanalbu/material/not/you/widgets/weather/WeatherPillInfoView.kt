package com.razvanalbu.material.not.you.widgets.weather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import kotlin.math.abs

object WeatherPillInfoView {

    fun textOnlyBitmap(
        temp: Int,
        typeface: Typeface,
        width: Int,
        height: Int,
        minDim: Float
    ): Bitmap {
        val isLarge = abs(temp) >= 100 || temp <= -10

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            textSize = minDim * (if (isLarge) 0.3f else 0.33f)
        }

        val fm = paint.fontMetrics
        val w = width.toFloat()
        val textX = if (isLarge) w * 0.54f else w * 0.57f
        val textY = -fm.ascent * if (isLarge) 1.7f else 1.5f

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawText("$temp\u00B0", textX, textY, paint)
        return bitmap
    }

    fun iconOnBitmap(
        icon: Drawable,
        iconSize: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        icon.setBounds(left, top, left + iconSize, top + iconSize)
        icon.draw(canvas)
        return bitmap
    }
}
